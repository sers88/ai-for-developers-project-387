import { expect, test, type Page } from "@playwright/test"

function uniqueEmail(): string {
  return `e2e-contacts-${Date.now()}-${Math.floor(Math.random() * 10000)}@test.com`
}

function nextWeekday(): Date {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  while (date.getDay() === 0 || date.getDay() === 6) {
    date.setDate(date.getDate() + 1)
  }
  return date
}

async function navigateToBookingDay(page: Page, target: Date): Promise<void> {
  const normalize = (s: string | null): string => (s ?? "").replace(/\s+/g, " ").trim()
  const targetMonth = normalize(
    target.toLocaleDateString("en-US", { month: "long", year: "numeric" }),
  )

  for (let i = 0; i < 3; i++) {
    const label = normalize(await page.locator("[data-slot='heading']").textContent())
    if (label === targetMonth) break
    await page.getByRole("button", { name: "Next month" }).click()
  }

  const dayCell = page
    .locator("[data-slot='cellTrigger']:not([data-outside-view])")
    .filter({ hasText: String(target.getDate()) })
  await dayCell.first().click()
}

test.describe("Contacts: auto-creation, favorites, search, dashboard", () => {
  test("contacts feature end-to-end", async ({ page }) => {
    test.setTimeout(120_000)

    const email = uniqueEmail()

    // ── 1. Register ──────────────────────────────────────────────
    await page.goto("/register")
    await page.getByTestId("email").fill(email)
    await page.getByTestId("password").fill("password123")
    await page.getByTestId("register-submit").click()

    await expect(page).toHaveURL(/\/dashboard/, { timeout: 30_000 })
    await expect(page.getByTestId("page-heading")).toHaveText("Dashboard")

    // ── 2. Save schedule (default Mon–Fri 09:00–18:00) ──────────
    await page.goto("/schedules")
    await expect(page.getByTestId("page-heading")).toHaveText("Schedule Settings")
    await expect(page.getByTestId("schedule-day-row").first()).toBeVisible({ timeout: 10_000 })

    await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/api/schedules") && resp.request().method() === "PUT",
        { timeout: 15_000 },
      ),
      page.getByTestId("schedule-save").click(),
    ])

    // ── 3. Create event type ────────────────────────────────────
    await page.goto("/event-types/create")
    await expect(page.getByTestId("page-heading")).toHaveText("New Event Type")

    await page.getByTestId("event-title").fill("E2E Contacts Consultation")
    await page.getByTestId("event-duration").fill("30")

    await page.getByTestId("schedule-select").click()
    const scheduleOption = page.getByRole("option").first()
    await scheduleOption.waitFor({ state: "visible", timeout: 10_000 })
    await scheduleOption.click()

    await page.getByTestId("event-create-submit").click()
    await expect(page).toHaveURL(/\/event-types$/, { timeout: 15_000 })

    // ── 4. Get booking URL from API ─────────────────────────────
    const token = await page.evaluate(() => localStorage.getItem("accessToken"))
    expect(token).toBeTruthy()

    const apiBase = await page.evaluate(() => {
      const nuxt = (window as unknown as { __NUXT__: { public: { apiBase: string } } }).__NUXT__
      return nuxt?.public?.apiBase || "http://localhost:8080"
    })

    const eventTypesResp = await page.request.get(`${apiBase}/api/event-types`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    expect(eventTypesResp.ok()).toBeTruthy()
    const eventTypes = await eventTypesResp.json()
    expect(eventTypes.length).toBeGreaterThan(0)

    const bookingUrl = eventTypes[0].bookingUrl as string
    expect(bookingUrl).toBeTruthy()

    // ── 5. Book a slot on the public booking page ───────────────
    await page.goto(bookingUrl)
    await expect(page.getByTestId("page-heading")).toHaveText("E2E Contacts Consultation")

    const targetDate = nextWeekday()
    await navigateToBookingDay(page, targetDate)

    await expect(page.getByTestId("time-slot").first()).toBeVisible({ timeout: 15_000 })
    await page.getByTestId("time-slot").first().click()

    const guestName = "E2E Contact Guest"
    const guestEmail = "e2e-contact-guest@test.com"
    await page.getByTestId("guest-name").fill(guestName)
    await page.getByTestId("guest-email").fill(guestEmail)

    const [bookingResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes("/api/bookings") && resp.request().method() === "POST",
        { timeout: 15_000 },
      ),
      page.getByTestId("confirm-booking").click(),
    ])
    expect(bookingResp.ok()).toBeTruthy()

    const bookingData = await bookingResp.json()
    await page.goto(`${bookingUrl}/success?id=${bookingData.id}`)
    await expect(page.getByTestId("booking-confirmed")).toBeVisible()

    // ── 6. Navigate to contacts page ─────────────────────────────
    await page.goto("/contacts")
    await expect(page.getByTestId("page-heading")).toHaveText("Contacts")

    // Should show the auto-created contact
    await expect(page.getByTestId("contacts-list")).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText(guestName)).toBeVisible()
    await expect(page.getByText(guestEmail)).toBeVisible()

    // ── 7. Toggle favorite ──────────────────────────────────────
    const contactsResp1 = await page.request.get(`${apiBase}/api/contacts`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    const contacts1 = await contactsResp1.json()
    expect(contacts1.length).toBeGreaterThan(0)
    const contactId = contacts1[0].id as string

    const favButton = page.getByTestId(`contact-fav-${contactId}`)
    await favButton.click()

    // Wait for API and verify the button shows as favorited
    await expect(favButton).toHaveAttribute("aria-label", "Remove from favorites", { timeout: 10_000 })

    // ── 8. Favorite filter ──────────────────────────────────────
    // Check "Favorites only" — should still show since we favorited
    await page.getByTestId("contacts-fav-filter").click()
    await expect(page.getByTestId("contacts-list")).toBeVisible()
    await expect(page.getByText(guestName)).toBeVisible()

    // Uncheck filter
    await page.getByTestId("contacts-fav-filter").click()

    // ── 9. Search ────────────────────────────────────────────────
    const searchInput = page.getByTestId("contacts-search").locator("input")
    await searchInput.fill("nonexistent")
    await page.waitForTimeout(500)
    await expect(page.getByTestId("contacts-empty")).toBeVisible({ timeout: 10_000 })

    await searchInput.fill("")
    await page.waitForTimeout(500)
    await expect(page.getByTestId("contacts-list")).toBeVisible({ timeout: 10_000 })

    // ── 10. Dashboard "Recent Contacts" section ─────────────────
    await page.goto("/dashboard")
    await expect(page.getByTestId("page-heading")).toHaveText("Dashboard")

    // The contact should appear in the Recent Contacts section
    await expect(page.getByText("Recent Contacts")).toBeVisible()
    await expect(page.getByText(guestName)).toBeVisible()
  })
})
