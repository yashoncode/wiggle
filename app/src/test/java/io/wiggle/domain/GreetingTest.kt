package io.wiggle.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingTest {

    @Test
    fun `each part of the day gets its own greeting`() {
        assertEquals("Good morning", Greeting.forTime(LocalTime.of(7, 0)))
        assertEquals("Good afternoon", Greeting.forTime(LocalTime.of(13, 0)))
        assertEquals("Good evening", Greeting.forTime(LocalTime.of(19, 0)))
        assertEquals("Good night", Greeting.forTime(LocalTime.of(23, 30)))
        assertEquals("Good night", Greeting.forTime(LocalTime.of(3, 0)))
    }

    @Test
    fun `only the first name is used, and a missing name is left out`() {
        assertEquals("Good morning, Yashwanth", Greeting.forPerson("Yashwanth Kumar", LocalTime.of(8, 0)))
        assertEquals("Good evening", Greeting.forPerson("  ", LocalTime.of(18, 0)))
        assertEquals("Good evening", Greeting.forPerson(null, LocalTime.of(18, 0)))
    }
}
