import { useApiClient } from "~/api/client"
import type { components } from "~/api/generated/schema"

type Contact = components["schemas"]["ContactResponse"]

export const useContacts = () => {
  const api = useApiClient()

  async function loadContacts(favorite?: boolean, search?: string): Promise<Contact[]> {
    const query: { favorite?: boolean; search?: string } = {}
    if (favorite !== undefined) query.favorite = favorite
    if (search) query.search = search
    const hasParams = Object.keys(query).length > 0
    const { data, error } = await api.GET("/api/contacts", {
      params: { query: hasParams ? query : undefined },
    })
    if (error || !data) throw new Error(String(error) || "Failed to load contacts")
    return data
  }

  async function loadContact(id: string): Promise<Contact> {
    const { data, error } = await api.GET("/api/contacts/{id}", {
      params: { path: { id } },
    })
    if (error || !data) throw new Error(String(error) || "Failed to load contact")
    return data
  }

  async function toggleFavorite(id: string, isFavorite: boolean): Promise<Contact> {
    const { data, error } = await api.PATCH("/api/contacts/{id}", {
      params: { path: { id } },
      body: { isFavorite },
    })
    if (error || !data) throw new Error(String(error) || "Failed to update contact")
    return data
  }

  return { loadContacts, loadContact, toggleFavorite }
}
