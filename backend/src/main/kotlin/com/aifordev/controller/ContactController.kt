package com.aifordev.controller

import com.aifordev.dto.ContactResponse
import com.aifordev.dto.ErrorResponse
import com.aifordev.dto.UpdateFavoriteRequest
import com.aifordev.security.UserPrincipal
import com.aifordev.service.ContactNotFoundException
import com.aifordev.service.ContactService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class ContactController(
    private val contactService: ContactService,
) {
    @GetMapping("/contacts")
    fun listContacts(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(name = "favorite", required = false) favorite: Boolean?,
        @RequestParam(name = "search", required = false) search: String?,
    ): ResponseEntity<List<ContactResponse>> =
        ResponseEntity.ok(contactService.listContacts(UUID.fromString(principal.userId), favorite, search))

    @GetMapping("/contacts/{id}")
    fun getContact(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
    ): ResponseEntity<ContactResponse> = ResponseEntity.ok(contactService.getContact(UUID.fromString(principal.userId), id))

    @PatchMapping("/contacts/{id}")
    fun updateContactFavorite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @RequestBody request: UpdateFavoriteRequest,
    ): ResponseEntity<ContactResponse> = ResponseEntity.ok(contactService.updateFavorite(UUID.fromString(principal.userId), id, request))

    @ExceptionHandler(ContactNotFoundException::class)
    fun handleContactNotFound(ex: ContactNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(message = ex.message ?: "Contact not found"))
}
