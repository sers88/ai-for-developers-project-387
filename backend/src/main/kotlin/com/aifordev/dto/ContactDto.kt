package com.aifordev.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ContactResponse(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val isFavorite: Boolean,
    val lastBookedAt: String?,
    val bookingCount: Int,
    val createdAt: String,
)

data class UpdateFavoriteRequest(
    val isFavorite: Boolean,
)
