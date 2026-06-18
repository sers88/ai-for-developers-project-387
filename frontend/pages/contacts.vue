<script setup lang="ts">
import type { components } from "~/api/generated/schema"

definePageMeta({
  layout: "default",
  middleware: ["auth"],
})

type Contact = components["schemas"]["ContactResponse"]

const { loadContacts, toggleFavorite } = useContacts()

const loading = ref(true)
const generalError = ref<string | null>(null)
const contacts = ref<Contact[]>([])
const favoriteFilter = ref(false)
const searchQuery = ref("")
const searchDebounce = ref<ReturnType<typeof setTimeout> | null>(null)

async function load() {
  loading.value = true
  generalError.value = null
  try {
    contacts.value = await loadContacts(favoriteFilter.value || undefined, searchQuery.value || undefined)
  } catch (e) {
    generalError.value = e instanceof Error ? e.message : "Failed to load contacts"
  } finally {
    loading.value = false
  }
}

async function onToggleFavorite(contact: Contact) {
  try {
    const newFavoriteValue = !contact.isFavorite
    const result = await toggleFavorite(contact.id!, newFavoriteValue)
    
    // Update in place
    const idx = contacts.value.findIndex((c) => c.id === result.id)
    if (idx !== -1) {
      contacts.value[idx] = result
    } else {
      console.error('[DEBUG] Contact not found in list!')
    }
  } catch (e) {
    console.error('[DEBUG] Toggle error:', e)
    generalError.value = e instanceof Error ? e.message : "Failed to update contact"
  }
}

function onSearchInput() {
  if (searchDebounce.value) clearTimeout(searchDebounce.value)
  searchDebounce.value = setTimeout(() => load(), 300)
}

function formatDate(iso: string | undefined): string {
  if (!iso) return "Never"
  return new Date(iso).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  })
}

watch(favoriteFilter, () => load())

await load()
</script>

<template>
  <div class="mx-auto max-w-4xl px-6 py-8">
    <header class="mb-6">
      <h1 class="text-2xl font-bold text-highlighted" data-testid="page-heading">Contacts</h1>
      <p class="mt-1 text-sm text-muted">People you've met with. Star your favorites to keep them close.</p>
    </header>

     <div class="mb-6 flex flex-wrap items-center gap-3">
       <div data-testid="contacts-search">
         <UInput
           v-model="searchQuery"
           icon="i-lucide-search"
           placeholder="Search by name or email..."
           class="w-72"
           @input="onSearchInput"
         />
       </div>
       <UCheckbox
         v-model="favoriteFilter"
         label="Favorites only"
         name="favorite-filter"
         data-testid="contacts-fav-filter"
       />
     </div>

    <p
      v-if="generalError"
      class="mb-4 rounded-md bg-error/10 px-3 py-2 text-sm text-error"
      role="alert"
      data-testid="contacts-error"
    >
      {{ generalError }}
    </p>

    <div v-if="loading" class="flex flex-col gap-4" data-testid="contacts-loading">
      <UCard v-for="i in 3" :key="i">
        <div class="flex items-center justify-between gap-4">
          <div class="flex-1 space-y-2">
            <USkeleton class="h-5 w-2/5" />
            <USkeleton class="h-4 w-3/5" />
            <USkeleton class="h-4 w-1/3" />
          </div>
          <USkeleton class="size-8 rounded-full" />
        </div>
      </UCard>
    </div>

    <UCard v-else-if="contacts.length === 0" data-testid="contacts-empty">
      <div class="flex flex-col items-center gap-3 py-8 text-center">
        <UIcon name="i-lucide-users" class="size-10 text-muted" />
        <div>
          <p class="font-medium text-highlighted">No contacts yet</p>
          <p class="mt-1 text-sm text-muted">
            When someone books a meeting with you, they'll appear here.
          </p>
        </div>
      </div>
    </UCard>

    <div v-else class="flex flex-col gap-3" data-testid="contacts-list">
      <UCard
        v-for="contact in contacts"
        :key="contact.id"
        :ui="{ body: 'flex items-center gap-4' }"
      >
        <div class="flex size-10 shrink-0 items-center justify-center rounded-full bg-muted text-sm font-semibold text-highlighted">
          {{ contact.name?.charAt(0)?.toUpperCase() ?? "?" }}
        </div>
        <div class="flex-1 min-w-0">
          <p class="truncate font-medium text-highlighted">{{ contact.name }}</p>
          <p class="truncate text-sm text-muted">{{ contact.email }}</p>
          <p class="mt-0.5 text-xs text-muted">
            Last met: {{ formatDate(contact.lastBookedAt!) }}
            <span v-if="contact.bookingCount && contact.bookingCount > 1">
              &middot; {{ contact.bookingCount }} meetings
            </span>
          </p>
        </div>
        <UButton
          :icon="contact.isFavorite ? 'i-lucide-star' : 'i-lucide-star-off'"
          :color="contact.isFavorite ? 'warning' : 'neutral'"
          :variant="contact.isFavorite ? 'solid' : 'ghost'"
          size="sm"
          :aria-label="contact.isFavorite ? 'Remove from favorites' : 'Add to favorites'"
          :data-testid="`contact-fav-${contact.id}`"
          @click="onToggleFavorite(contact)"
        />
      </UCard>
    </div>
  </div>
</template>
