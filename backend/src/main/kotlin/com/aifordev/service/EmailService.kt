package com.aifordev.service

import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: SpringTemplateEngine,
    @Value("\${app.mail.from:noreply@aifordev.com}") private val fromAddress: String,
    @Value("\${app.frontend-url:http://localhost:3000}") private val frontendUrl: String,
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm z", Locale.ENGLISH)

    @Async("emailTaskExecutor")
    fun sendBookingConfirmationToGuest(data: BookingEmailData) {
        val context =
            Context(Locale.ENGLISH).apply {
                setVariable("guestName", data.guestName)
                setVariable("eventTitle", data.eventTitle)
                setVariable("ownerName", data.ownerName)
                setVariable("startTime", formatTime(data))
                setVariable("duration", data.duration)
                setVariable("cancelUrl", data.cancelUrl)
            }
        sendHtml(
            to = data.guestEmail,
            subject = "Booking confirmed: ${data.eventTitle}",
            template = "email/booking-confirmation-guest",
            context = context,
        )
    }

    @Async("emailTaskExecutor")
    fun sendBookingNotificationToOrganizer(data: BookingEmailData) {
        val context =
            Context(Locale.ENGLISH).apply {
                setVariable("ownerName", data.ownerName)
                setVariable("guestName", data.guestName)
                setVariable("guestEmail", data.guestEmail)
                setVariable("eventTitle", data.eventTitle)
                setVariable("startTime", formatTime(data))
                setVariable("notes", data.notes)
            }
        sendHtml(
            to = data.ownerEmail,
            subject = "New booking: ${data.eventTitle} — ${data.guestName}",
            template = "email/booking-notification-organizer",
            context = context,
        )
    }

    @Async("emailTaskExecutor")
    fun sendCancellationToGuest(data: BookingEmailData) {
        val context =
            Context(Locale.ENGLISH).apply {
                setVariable("guestName", data.guestName)
                setVariable("eventTitle", data.eventTitle)
                setVariable("ownerName", data.ownerName)
                setVariable("startTime", formatTime(data))
            }
        sendHtml(
            to = data.guestEmail,
            subject = "Booking cancelled: ${data.eventTitle}",
            template = "email/booking-cancellation-guest",
            context = context,
        )
    }

    @Async("emailTaskExecutor")
    fun sendCancellationToOrganizer(data: BookingEmailData) {
        val context =
            Context(Locale.ENGLISH).apply {
                setVariable("ownerName", data.ownerName)
                setVariable("guestName", data.guestName)
                setVariable("eventTitle", data.eventTitle)
                setVariable("startTime", formatTime(data))
            }
        sendHtml(
            to = data.ownerEmail,
            subject = "Booking cancelled: ${data.eventTitle} — ${data.guestName}",
            template = "email/booking-cancellation-organizer",
            context = context,
        )
    }

    fun buildCancelUrl(
        ownerId: java.util.UUID,
        slug: String,
        cancelToken: java.util.UUID,
        bookingId: java.util.UUID,
    ): String = "$frontendUrl/$ownerId/$slug/cancel?token=$cancelToken&id=$bookingId"

    private fun sendHtml(
        to: String,
        subject: String,
        template: String,
        context: Context,
    ) {
        try {
            val html = templateEngine.process(template, context)
            val mimeMessage: MimeMessage = mailSender.createMimeMessage()
            val helper =
                MimeMessageHelper(mimeMessage, false, "UTF-8").apply {
                    setFrom(fromAddress)
                    setTo(to)
                    setSubject(subject)
                    setText(html, true)
                }
            mailSender.send(mimeMessage)
            logger.info("Sent email '{}' to {}", subject, to)
        } catch (e: MessagingException) {
            logger.error("Failed to send email '{}' to {}: {}", subject, to, e.message)
        } catch (e: Exception) {
            logger.error("Unexpected error sending email '{}' to {}: {}", subject, to, e.message)
        }
    }

    private fun formatTime(data: BookingEmailData): String {
        val zone = ZoneId.of("UTC")
        val start = data.startTime.atZone(zone)
        val end = data.endTime.atZone(zone)
        val dayPart = start.format(dateFormatter)
        val endTime = end.format(DateTimeFormatter.ofPattern("HH:mm z", Locale.ENGLISH))
        return "$dayPart – $endTime"
    }
}
