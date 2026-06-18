package com.aifordev.service

import com.aifordev.dto.ContactResponse
import com.aifordev.dto.UpdateFavoriteRequest
import com.aifordev.entity.Contact
import com.aifordev.repository.ContactRepository
import com.aifordev.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class ContactService(
    private val contactRepository: ContactRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun listContacts(
        ownerId: UUID,
        favorite: Boolean?,
        search: String?,
    ): List<ContactResponse> {
        val contacts =
            when {
                search != null && favorite != null ->
                    contactRepository.searchByOwnerIdAndFavorite(ownerId, favorite, search)
                search != null ->
                    contactRepository.searchByOwnerId(ownerId, search)
                favorite != null ->
                    contactRepository.findByOwnerIdAndIsFavorite(ownerId, favorite)
                else ->
                    contactRepository.findByOwnerId(ownerId)
            }
        return contacts.map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun getContact(
        ownerId: UUID,
        contactId: UUID,
    ): ContactResponse {
        val contact =
            contactRepository
                .findByIdAndOwnerId(contactId, ownerId)
                .orElseThrow { ContactNotFoundException("Contact not found") }
        return toResponse(contact)
    }

    @Transactional
    fun updateFavorite(
        ownerId: UUID,
        contactId: UUID,
        request: UpdateFavoriteRequest,
    ): ContactResponse {
        val contact =
            contactRepository
                .findByIdAndOwnerId(contactId, ownerId)
                .orElseThrow { ContactNotFoundException("Contact not found") }
        contact.isFavorite = request.isFavorite
        contact.updatedAt = Instant.now()
        return toResponse(contactRepository.save(contact))
    }

    @Transactional
    fun createOrUpdateFromBooking(
        ownerId: UUID,
        guestName: String,
        guestEmail: String,
        bookingTime: Instant,
    ) {
        val existing = contactRepository.findByOwnerIdAndEmail(ownerId, guestEmail)
        if (existing.isPresent) {
            val contact = existing.get()
            contact.name = guestName
            contact.lastBookedAt = bookingTime
            contact.bookingCount = contact.bookingCount + 1
            contact.updatedAt = Instant.now()
            contactRepository.save(contact)
        } else {
            val owner =
                userRepository
                    .findById(ownerId)
                    .orElseThrow { IllegalArgumentException("User not found") }
            val contact =
                Contact(
                    owner = owner,
                    name = guestName,
                    email = guestEmail,
                    lastBookedAt = bookingTime,
                    bookingCount = 1,
                )
            contactRepository.save(contact)
        }
    }

    private fun toResponse(contact: Contact): ContactResponse =
        ContactResponse(
            id = contact.id.toString(),
            name = contact.name,
            email = contact.email,
            avatarUrl = contact.avatarUrl,
            isFavorite = contact.isFavorite,
            lastBookedAt = contact.lastBookedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
            bookingCount = contact.bookingCount,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(contact.createdAt),
        )
}

class ContactNotFoundException(
    message: String,
) : RuntimeException(message)
