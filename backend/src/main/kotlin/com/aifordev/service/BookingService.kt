package com.aifordev.service

import com.aifordev.dto.BookingResponse
import com.aifordev.dto.CreateBookingRequest
import com.aifordev.entity.Booking
import com.aifordev.repository.BookingRepository
import com.aifordev.repository.EventTypeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
class BookingService(
    private val eventTypeRepository: EventTypeRepository,
    private val bookingRepository: BookingRepository,
    private val googleCalendarService: GoogleCalendarService,
    private val availabilityService: AvailabilityService,
    private val emailService: EmailService,
    private val contactService: ContactService,
) {
    @Transactional
    fun createBooking(request: CreateBookingRequest): BookingResponse {
        val eventTypeId = UUID.fromString(request.eventTypeId)
        val eventType =
            eventTypeRepository
                .findById(eventTypeId)
                .orElseThrow { EventTypeNotFoundException("Event type not found") }

        val startTime = Instant.parse(request.startTime)
        val endTime = Instant.parse(request.endTime)

        if (!endTime.isAfter(startTime)) {
            throw IllegalArgumentException("endTime must be after startTime")
        }

        if (endTime.toEpochMilli() - startTime.toEpochMilli() != eventType.duration.toLong() * 60 * 1000) {
            throw IllegalArgumentException("Slot duration must match event type duration (${eventType.duration} minutes)")
        }

        val conflicts =
            bookingRepository.findConflictingBookings(
                eventTypeId = eventTypeId,
                rangeStart = startTime.minusSeconds(eventType.bufferBefore.toLong() * 60),
                rangeEnd = endTime.plusSeconds(eventType.bufferAfter.toLong() * 60),
            )

        if (conflicts.isNotEmpty()) {
            throw SlotAlreadyBookedException("This time slot is already booked")
        }

        val booking =
            Booking(
                eventType = eventType,
                guestName = request.guestName,
                guestEmail = request.guestEmail,
                notes = request.notes,
                startTime = startTime,
                endTime = endTime,
            )

        val saved = bookingRepository.save(booking)

        val userId = eventType.user.id
        if (userId != null) {
            try {
                val calendarEvent =
                    googleCalendarService.createEvent(
                        userId = userId,
                        summary = "${eventType.title} — ${request.guestName}",
                        start = startTime.toString(),
                        end = endTime.toString(),
                        description = request.notes,
                    )
                val googleEventId = calendarEvent["googleEventId"] as? String
                if (googleEventId != null) {
                    saved.googleEventId = googleEventId
                    bookingRepository.save(saved)
                }
            } catch (e: Exception) {
                // Booking succeeds even if calendar event creation fails
            }
        }

        if (userId != null) {
            try {
                contactService.createOrUpdateFromBooking(userId, request.guestName, request.guestEmail, startTime)
            } catch (_: Exception) {
                // Booking succeeds even if contact creation fails
            }
        }

        emailService.sendBookingConfirmationToGuest(toEmailData(saved))
        emailService.sendBookingNotificationToOrganizer(toEmailData(saved))

        return toResponse(saved)
    }

    @Transactional
    fun listBookings(
        userId: UUID,
        status: String?,
    ): List<BookingResponse> {
        val now = Instant.now()
        val bookings =
            when (status) {
                "past" -> bookingRepository.findPastByUserId(userId, now)
                else -> bookingRepository.findUpcomingByUserId(userId, now)
            }
        return bookings.map { toResponse(it) }
    }

    @Transactional
    fun cancelBooking(
        bookingId: UUID,
        requesterUserId: UUID?,
        token: String?,
    ): BookingResponse {
        val booking =
            bookingRepository
                .findById(bookingId)
                .orElseThrow { BookingNotFoundException("Booking not found") }

        val isOwner = requesterUserId != null && booking.eventType.user.id == requesterUserId
        val isGuestWithToken = token != null && token == booking.cancelToken.toString()

        if (!isOwner && !isGuestWithToken) {
            throw BookingAccessDeniedException("You are not authorized to cancel this booking")
        }

        if (booking.status == "CANCELLED") {
            throw BookingAlreadyCancelledException("Booking is already cancelled")
        }

        booking.status = "CANCELLED"
        bookingRepository.save(booking)

        val userId = booking.eventType.user.id
        if (userId != null) {
            booking.googleEventId?.let { googleEventId ->
                try {
                    googleCalendarService.deleteEvent(userId, googleEventId)
                } catch (e: Exception) {
                    // Booking cancellation succeeds even if calendar event deletion fails
                }
            }
        }

        emailService.sendCancellationToGuest(toEmailData(booking))
        emailService.sendCancellationToOrganizer(toEmailData(booking))

        return toResponse(booking)
    }

    private fun toEmailData(booking: Booking): BookingEmailData {
        val owner = booking.eventType.user
        return BookingEmailData(
            guestName = booking.guestName,
            guestEmail = booking.guestEmail,
            eventTitle = booking.eventType.title,
            duration = booking.eventType.duration,
            startTime = booking.startTime,
            endTime = booking.endTime,
            ownerEmail = owner.email,
            ownerName = owner.name ?: owner.email,
            cancelUrl =
                emailService.buildCancelUrl(
                    ownerId = owner.id!!,
                    slug = booking.eventType.slug,
                    cancelToken = booking.cancelToken,
                    bookingId = booking.id!!,
                ),
            notes = booking.notes,
        )
    }

    private fun toResponse(booking: Booking): BookingResponse =
        BookingResponse(
            id = booking.id.toString(),
            eventTypeId = booking.eventType.id.toString(),
            eventTitle = booking.eventType.title,
            guestName = booking.guestName,
            guestEmail = booking.guestEmail,
            notes = booking.notes,
            startTime = booking.startTime.toString(),
            endTime = booking.endTime.toString(),
            status = booking.status,
            cancelToken = booking.cancelToken.toString(),
            createdAt = booking.createdAt.atOffset(ZoneOffset.UTC).toString(),
        )
}
