<script setup lang="ts">
import { useApiClient } from "~/api/client"
import type { components } from "~/api/generated/schema"

definePageMeta({
  layout: "default",
  middleware: ["auth"],
})

type Booking = components["schemas"]["BookingResponse"]
type BookingStatusKind = "confirmed" | "completed" | "cancelled"
type BookingFilter = "upcoming" | "past"

const api = useApiClient()
const { loadBookings, cancelBooking } = useBookings()
const { loadContacts } = useContacts()

const { data: me } = await api.GET("/api/me")

const loading = ref(true)
const generalError = ref<string | null>(null)
const bookings = ref<Awaited<ReturnType<typeof loadBookings>>>([])
const recentContacts = ref<Awaited<ReturnType<typeof loadContacts>>>([])
const filter = ref<BookingFilter>("upcoming")

const tabItems: { label: string; value: BookingFilter; icon: string }[] = [
  { label: "Upcoming", value: "upcoming", icon: "i-lucide-calendar-clock" },
  { label: "Past", value: "past", icon: "i-lucide-history" },
]

const cancelTarget = ref<Booking | null>(null)
const cancelling = ref(false)
const isCancelModalOpen = ref(false)

const cancelDescription = computed(() => {
  const target = cancelTarget.value
  if (!target) return ""
  return `This will cancel "${target.eventTitle}" and notify ${target.guestEmail}.`
})

const statusMeta: Record<BookingStatusKind, { label: string; color: "success" | "neutral" | "error"; testid: string }> = {
  confirmed: { label: "Confirmed", color: "success", testid: "booking-status-confirmed" },
  completed: { label: "Completed", color: "neutral", testid: "booking-status-completed" },
  cancelled: { label: "Cancelled", color: "error", testid: "booking-status-cancelled" },
}

async function load() {
  loading.value = true
  generalError.value = null
  try {
    bookings.value = await loadBookings(filter.value)
  } catch (e) {
    generalError.value = e instanceof Error ? e.message : "Failed to load bookings"
  } finally {
    loading.value = false
  }
}

function onTabChange(value: string | number) {
  if (value === "upcoming" || value === "past") {
    filter.value = value
  }
}

function openCancelModal(booking: Booking) {
  cancelTarget.value = booking
  isCancelModalOpen.value = true
}

async function confirmCancel() {
  const target = cancelTarget.value
  if (!target || cancelling.value) return
  cancelling.value = true
  try {
    await cancelBooking(target.id)
    await load()
  } catch (e) {
    generalError.value = e instanceof Error ? e.message : "Failed to cancel booking"
  } finally {
    cancelling.value = false
    isCancelModalOpen.value = false
    cancelTarget.value = null
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  })
}

function statusKind(b: Booking): BookingStatusKind {
  if (b.status === "CANCELLED") return "cancelled"
  return new Date(b.endTime).getTime() < Date.now() ? "completed" : "confirmed"
}

async function loadRecentContacts() {
  try {
    recentContacts.value = await loadContacts()
  } catch {
    // Non-critical, dashboard works without it
  }
}

watch(filter, () => load())

await Promise.all([load(), loadRecentContacts()])
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <header class="mb-6">
      <h1 class="text-2xl font-bold text-highlighted" data-testid="page-heading">Dashboard</h1>
      <p v-if="me" class="mt-1 text-sm text-muted">Welcome, {{ me.email }}</p>
    </header>

    <UTabs
      :model-value="filter"
      :items="tabItems"
      :content="false"
      class="mb-6"
      data-testid="dashboard-tabs"
      @update:model-value="onTabChange"
    />

    <p
      v-if="generalError"
      class="mb-4 text-sm text-error"
      role="alert"
      data-testid="dashboard-error"
    >
      {{ generalError }}
    </p>

    <div v-if="loading" class="flex flex-col gap-4" data-testid="dashboard-loading">
      <UCard v-for="i in 3" :key="i">
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1 space-y-2">
            <USkeleton class="h-5 w-2/5" />
            <USkeleton class="h-4 w-3/5" />
            <USkeleton class="h-4 w-1/3" />
          </div>
          <USkeleton class="h-8 w-20" />
        </div>
      </UCard>
    </div>

    <UCard v-else-if="bookings.length === 0" data-testid="dashboard-empty">
      <div class="flex flex-col items-center gap-3 py-8 text-center">
        <UIcon
          :name="filter === 'upcoming' ? 'i-lucide-calendar-clock' : 'i-lucide-history'"
          class="size-10 text-muted"
        />
        <div>
          <p class="font-medium text-highlighted">
            {{ filter === "upcoming" ? "No upcoming bookings" : "No past bookings" }}
          </p>
          <p class="mt-1 text-sm text-muted">
            {{ filter === "upcoming"
              ? "Create an event type so people can book you."
              : "Your past meetings will appear here." }}
          </p>
        </div>
        <UButton
          v-if="filter === 'upcoming'"
          to="/event-types/create"
          icon="i-lucide-plus"
          label="Create event type"
          color="primary"
          data-testid="dashboard-empty-cta"
        />
      </div>
    </UCard>

    <div v-else class="flex flex-col gap-4" data-testid="dashboard-bookings">
      <UCard
        v-for="b in bookings"
        :key="b.id"
        :ui="{ body: 'flex items-start justify-between gap-4' }"
      >
        <div class="flex-1">
          <div class="mb-1 flex flex-wrap items-center gap-2">
            <h3 class="text-base font-semibold text-highlighted">{{ b.eventTitle }}</h3>
            <UBadge
              :color="statusMeta[statusKind(b)].color"
              variant="subtle"
              size="sm"
              :data-testid="statusMeta[statusKind(b)].testid"
            >
              {{ statusMeta[statusKind(b)].label }}
            </UBadge>
          </div>
          <div class="mb-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted">
            <span class="inline-flex items-center gap-1">
              <UIcon name="i-lucide-user" class="size-4" />
              {{ b.guestName }}
            </span>
            <span class="inline-flex items-center gap-1">
              <UIcon name="i-lucide-mail" class="size-4" />
              {{ b.guestEmail }}
            </span>
          </div>
          <p class="inline-flex items-center gap-1 text-sm text-default">
            <UIcon name="i-lucide-calendar" class="size-4 text-muted" />
            {{ formatDate(b.startTime) }}
          </p>
          <p v-if="b.notes" class="mt-2 text-sm italic text-muted">{{ b.notes }}</p>
        </div>
        <div v-if="statusKind(b) === 'confirmed'" class="shrink-0">
          <UButton
            icon="i-lucide-x"
            label="Cancel"
            color="error"
            variant="outline"
            data-testid="cancel-booking"
            @click="openCancelModal(b)"
          />
        </div>
      </UCard>
    </div>

    <UModal
      v-model:open="isCancelModalOpen"
      title="Cancel booking?"
      :description="cancelDescription"
      data-testid="cancel-booking-modal"
    >
      <template #footer="{ close }">
        <div class="flex w-full justify-end gap-2">
          <UButton
            label="Keep booking"
            color="neutral"
            variant="ghost"
            data-testid="cancel-booking-keep"
            @click="close"
          />
          <UButton
            label="Cancel booking"
            color="error"
            :loading="cancelling"
            data-testid="confirm-cancel-booking"
            @click="confirmCancel"
          />
        </div>
      </template>
    </UModal>

    <section v-if="recentContacts.length > 0" class="mt-8">
      <header class="mb-3 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-highlighted">Recent Contacts</h2>
        <UButton to="/contacts" label="View all" color="neutral" variant="ghost" size="sm" icon="i-lucide-arrow-right" trailing />
      </header>
      <div class="flex flex-col gap-2">
        <UCard
          v-for="contact in recentContacts.slice(0, 5)"
          :key="contact.id"
          :ui="{ body: 'flex items-center gap-3 py-3' }"
        >
          <div class="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted text-xs font-semibold text-highlighted">
            {{ contact.name?.charAt(0)?.toUpperCase() ?? "?" }}
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-highlighted">{{ contact.name }}</p>
            <p class="truncate text-xs text-muted">{{ contact.email }}</p>
          </div>
          <UIcon
            :name="contact.isFavorite ? 'i-lucide-star' : 'i-lucide-star'"
            :class="contact.isFavorite ? 'text-warning' : 'text-muted'"
            class="size-4 shrink-0"
          />
        </UCard>
      </div>
    </section>
  </div>
</template>
