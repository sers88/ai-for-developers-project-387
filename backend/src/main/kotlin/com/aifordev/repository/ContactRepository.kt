package com.aifordev.repository

import com.aifordev.entity.Contact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ContactRepository : JpaRepository<Contact, UUID> {
    fun findByOwnerId(ownerId: UUID): List<Contact>

    fun findByOwnerIdAndIsFavorite(
        ownerId: UUID,
        isFavorite: Boolean,
    ): List<Contact>

    fun findByIdAndOwnerId(
        id: UUID,
        ownerId: UUID,
    ): Optional<Contact>

    fun findByOwnerIdAndEmail(
        ownerId: UUID,
        email: String,
    ): Optional<Contact>

    @Query(
        """
        SELECT c FROM Contact c
        WHERE c.owner.id = :ownerId
          AND (c.name ILIKE %:search% OR c.email ILIKE %:search%)
        """,
    )
    fun searchByOwnerId(
        @Param("ownerId") ownerId: UUID,
        @Param("search") search: String,
    ): List<Contact>

    @Query(
        """
        SELECT c FROM Contact c
        WHERE c.owner.id = :ownerId
          AND c.isFavorite = :isFavorite
          AND (c.name ILIKE %:search% OR c.email ILIKE %:search%)
        """,
    )
    fun searchByOwnerIdAndFavorite(
        @Param("ownerId") ownerId: UUID,
        @Param("isFavorite") isFavorite: Boolean,
        @Param("search") search: String,
    ): List<Contact>
}
