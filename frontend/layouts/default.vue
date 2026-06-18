<script setup lang="ts">
const route = useRoute()
const store = useAuthStore()
const { logout } = useAuth()
const colorMode = useColorMode()

const navItems = [
  { label: "Dashboard", to: "/dashboard", icon: "i-lucide-layout-dashboard", testid: "nav-dashboard" },
  { label: "Event Types", to: "/event-types", icon: "i-lucide-calendar-days", testid: "nav-event-types" },
  { label: "Contacts", to: "/contacts", icon: "i-lucide-users", testid: "nav-contacts" },
  { label: "Schedules", to: "/schedules", icon: "i-lucide-clock", testid: "nav-schedules" },
  { label: "Settings", to: "/settings", icon: "i-lucide-settings", testid: "nav-settings" },
] as const

function isActive(to: string): boolean {
  if (to === "/event-types") return route.path.startsWith("/event-types")
  return route.path === to
}

const userMenuItems = computed(() => [
  [{ label: store.user?.email ?? "Account", type: "label" as const, disabled: true }],
  [
    {
      label: "Logout",
      icon: "i-lucide-log-out",
      onSelect: () => logout(),
    },
  ],
])

function toggleTheme() {
  colorMode.preference = colorMode.value === "dark" ? "light" : "dark"
}
</script>

<template>
  <div class="min-h-screen">
    <aside
      class="fixed inset-y-0 left-0 z-40 flex w-64 flex-col gap-2 border-r border-default bg-default p-4"
    >
      <div class="mb-4 px-2">
        <span class="text-lg font-bold text-highlighted">AiForDev</span>
      </div>

      <nav class="flex flex-1 flex-col gap-1">
        <UButton
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :icon="item.icon"
          :label="item.label"
          :color="isActive(item.to) ? 'primary' : 'neutral'"
          :variant="isActive(item.to) ? 'soft' : 'ghost'"
          block
          class="justify-start"
          :data-testid="item.testid"
        />
      </nav>

      <UButton
        icon="i-lucide-log-out"
        label="Logout"
        color="neutral"
        variant="ghost"
        block
        class="justify-start"
        data-testid="nav-logout"
        @click="logout"
      />
    </aside>

    <div class="flex min-h-screen flex-col pl-64">
      <header
        class="sticky top-0 z-30 flex h-16 items-center justify-end gap-2 border-b border-default bg-default/75 px-6 backdrop-blur"
      >
        <UButton
          :icon="colorMode.value === 'dark' ? 'i-lucide-sun' : 'i-lucide-moon'"
          color="neutral"
          variant="ghost"
          aria-label="Toggle theme"
          data-testid="theme-toggle"
          @click="toggleTheme"
        />

        <UDropdownMenu :items="userMenuItems">
          <UButton
            icon="i-lucide-user"
            :label="store.user?.email ?? 'Account'"
            color="neutral"
            variant="ghost"
            trailing-icon="i-lucide-chevron-down"
            data-testid="user-menu"
          />
        </UDropdownMenu>
      </header>

      <main class="flex-1">
        <slot />
      </main>
    </div>
  </div>
</template>
