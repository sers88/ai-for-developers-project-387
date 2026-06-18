package com.aifordev.contact

import com.aifordev.contract.ContractValidator
import com.aifordev.dto.AuthResponse
import com.aifordev.dto.BookingResponse
import com.aifordev.dto.CreateBookingRequest
import com.aifordev.dto.CreateEventTypeRequest
import com.aifordev.dto.EventTypeResponse
import com.aifordev.dto.RegisterRequest
import com.aifordev.service.EmailService
import com.aifordev.service.GoogleCalendarService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.openapi4j.operation.validator.model.Request
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS1mb3Itand0LXNpZ25pbmctaW4tdGVzdHM",
        "app.jwt.access-token-expiration-ms=900000",
        "app.jwt.refresh-token-expiration-ms=604800000",
        "app.oauth2.google.client-id=test-client-id",
        "app.oauth2.google.client-secret=test-client-secret",
        "app.oauth2.google.redirect-uri=http://localhost:8080/api/auth/oauth2/google/callback",
        "app.oauth2.google.frontend-url=http://localhost:3000",
        "app.frontend-url=http://localhost:3000",
        "app.mail.from=noreply@test.com",
    ],
)
class ContactIntegrationTest {
    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @MockitoBean
    private lateinit var googleCalendarService: GoogleCalendarService

    @MockitoBean
    private lateinit var emailService: EmailService

    private val mapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }

    @BeforeEach
    fun setUp() {
        Mockito.clearInvocations(emailService, googleCalendarService)
        given(googleCalendarService.createEvent(any(), any(), any(), any(), any()))
            .willReturn(mapOf("googleEventId" to "google-evt-123"))
        given(emailService.buildCancelUrl(any(), any(), any(), any()))
            .willReturn("http://localhost:3000/cancel")
    }

    private fun registerAndGetToken(email: String): String {
        val request = RegisterRequest(email = email, password = "password123")
        val response = restTemplate.postForEntity("/api/auth/register", HttpEntity(request), AuthResponse::class.java)
        return response.body!!.accessToken
    }

    private fun authHeaders(token: String): HttpEntity<Any> {
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        return HttpEntity(headers)
    }

    private fun <T> authEntity(
        token: String,
        body: T,
    ): HttpEntity<T> {
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        return HttpEntity(body, headers)
    }

    private fun createEventType(
        token: String,
        title: String = "Consultation",
        duration: Int = 30,
    ): EventTypeResponse {
        val request = CreateEventTypeRequest(title = title, duration = duration)
        return restTemplate.postForEntity("/api/event-types", authEntity(token, request), EventTypeResponse::class.java).body!!
    }

    private fun createBooking(
        token: String,
        eventTypeId: String,
        guestName: String = "Jane Guest",
        guestEmail: String = "jane@example.com",
    ): BookingResponse {
        val start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS)
        val end = start.plus(30, ChronoUnit.MINUTES)
        val request =
            CreateBookingRequest(
                eventTypeId = eventTypeId,
                guestName = guestName,
                guestEmail = guestEmail,
                startTime = start.toString(),
                endTime = end.toString(),
            )
        return restTemplate.postForEntity("/api/bookings", HttpEntity(request), BookingResponse::class.java).body!!
    }

    private fun listContactsRaw(token: String): List<Map<String, Any?>> {
        val response =
            restTemplate.exchange(
                "/api/contacts",
                HttpMethod.GET,
                authHeaders(token),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, response.statusCode)
        return mapper.readValue(response.body!!, object : TypeReference<List<Map<String, Any?>>>() {})
    }

    @Test
    fun `list contacts returns empty array initially`() {
        val token = registerAndGetToken("empty-contacts@example.com")
        val contacts = listContactsRaw(token)
        assertEquals(0, contacts.size)
        ContractValidator.validateResponse("/api/contacts", Request.Method.GET, 200, mapper.writeValueAsString(contacts))
    }

    @Test
    fun `creating a booking creates a contact automatically`() {
        val token = registerAndGetToken("auto-contact@example.com")
        val eventType = createEventType(token)
        createBooking(token, eventType.id, guestName = "Auto Contact", guestEmail = "auto@example.com")

        val contacts = listContactsRaw(token)
        assertEquals(1, contacts.size)
        assertEquals("Auto Contact", contacts[0]["name"])
        assertEquals("auto@example.com", contacts[0]["email"])
        assertFalse(contacts[0]["isFavorite"] as Boolean)
        assertNotNull(contacts[0]["lastBookedAt"])
        assertEquals(1, contacts[0]["bookingCount"])
    }

    @Test
    fun `multiple bookings from same guest increment booking count`() {
        val token = registerAndGetToken("multi-booking-contact@example.com")
        val eventType = createEventType(token)
        createBooking(token, eventType.id, guestName = "Repeat Guest", guestEmail = "repeat@example.com")

        val start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(3, ChronoUnit.HOURS)
        val end = start.plus(30, ChronoUnit.MINUTES)
        val request =
            CreateBookingRequest(
                eventTypeId = eventType.id,
                guestName = "Repeat Guest",
                guestEmail = "repeat@example.com",
                startTime = start.toString(),
                endTime = end.toString(),
            )
        restTemplate.postForEntity("/api/bookings", HttpEntity(request), BookingResponse::class.java)

        val contacts = listContactsRaw(token)
        assertEquals(1, contacts.size)
        assertEquals(2, contacts[0]["bookingCount"])
    }

    @Test
    fun `toggle favorite on contact`() {
        val token = registerAndGetToken("favorite-contact@example.com")
        val eventType = createEventType(token)
        createBooking(token, eventType.id, guestEmail = "fav@example.com")

        val contacts = listContactsRaw(token)
        val contactId = contacts[0]["id"] as String

        val patchResponse =
            restTemplate.exchange(
                "/api/contacts/$contactId",
                HttpMethod.PATCH,
                authEntity(token, mapOf("isFavorite" to true)),
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, patchResponse.statusCode)
        assertEquals(true, patchResponse.body!!["isFavorite"])
        ContractValidator.validateResponse(
            "/api/contacts/{id}",
            Request.Method.PATCH,
            200,
            mapper.writeValueAsString(patchResponse.body),
        )
    }

    @Test
    fun `filter contacts by favorite`() {
        val token = registerAndGetToken("filter-fav@example.com")
        val eventType = createEventType(token)
        createBooking(token, eventType.id, guestName = "Fav 1", guestEmail = "fav1@example.com")

        val start = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(4, ChronoUnit.HOURS)
        val end = start.plus(30, ChronoUnit.MINUTES)
        val request =
            CreateBookingRequest(
                eventTypeId = eventType.id,
                guestName = "Fav 2",
                guestEmail = "fav2@example.com",
                startTime = start.toString(),
                endTime = end.toString(),
            )
        restTemplate.postForEntity("/api/bookings", HttpEntity(request), BookingResponse::class.java)

        val contacts = listContactsRaw(token)
        val contactId = contacts[0]["id"] as String

        restTemplate.exchange(
            "/api/contacts/$contactId",
            HttpMethod.PATCH,
            authEntity(token, mapOf("isFavorite" to true)),
            Map::class.java,
        )

        val favResponse =
            restTemplate.exchange(
                "/api/contacts?favorite=true",
                HttpMethod.GET,
                authHeaders(token),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, favResponse.statusCode)
        val favContacts: List<Map<String, Any?>> =
            mapper.readValue(
                favResponse.body!!,
                object : TypeReference<List<Map<String, Any?>>>() {},
            )
        assertEquals(1, favContacts.size)
        assertEquals("fav1@example.com", favContacts[0]["email"])
    }

    @Test
    fun `get contact by id returns contact`() {
        val token = registerAndGetToken("get-contact@example.com")
        val eventType = createEventType(token)
        createBooking(token, eventType.id, guestEmail = "getme@example.com")

        val contacts = listContactsRaw(token)
        val contactId = contacts[0]["id"] as String

        val getResponse =
            restTemplate.exchange(
                "/api/contacts/$contactId",
                HttpMethod.GET,
                authHeaders(token),
                Map::class.java,
            )

        assertEquals(HttpStatus.OK, getResponse.statusCode)
        assertEquals("getme@example.com", getResponse.body!!["email"])
        ContractValidator.validateResponse(
            "/api/contacts/{id}",
            Request.Method.GET,
            200,
            mapper.writeValueAsString(getResponse.body),
        )
    }

    @Test
    fun `get contact not found returns 404`() {
        val token = registerAndGetToken("contact-notfound@example.com")

        val response =
            restTemplate.exchange(
                "/api/contacts/${java.util.UUID.randomUUID()}",
                HttpMethod.GET,
                authHeaders(token),
                Map::class.java,
            )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `contacts are isolated between users`() {
        val token1 = registerAndGetToken("user1-isolation@example.com")
        val eventType1 = createEventType(token1)
        createBooking(token1, eventType1.id, guestEmail = "u1@example.com")

        val token2 = registerAndGetToken("user2-isolation@example.com")
        val contacts = listContactsRaw(token2)
        assertEquals(0, contacts.size)
    }

    private fun <T> any(): T = org.mockito.ArgumentMatchers.any()
}
