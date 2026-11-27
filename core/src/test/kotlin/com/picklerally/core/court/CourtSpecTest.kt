package com.picklerally.core.court

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CourtSpecTest {

    @Test
    fun `court dimensions match official USA Pickleball measurements`() {
        assertEquals(20f, CourtSpec.COURT_RIGHT_X - CourtSpec.COURT_LEFT_X)
        assertEquals(44f, CourtSpec.COURT_TOP_Y - CourtSpec.COURT_BOTTOM_Y)
        assertEquals(7f, CourtSpec.NET_Y - CourtSpec.LOWER_NVZ_Y)
        assertEquals(7f, CourtSpec.UPPER_NVZ_Y - CourtSpec.NET_Y)
        assertEquals(15f, CourtSpec.LOWER_NVZ_Y - CourtSpec.COURT_BOTTOM_Y)
        assertEquals(15f, CourtSpec.COURT_TOP_Y - CourtSpec.UPPER_NVZ_Y)
    }

    @Test
    fun `net height sags cleanly from 36 inches at sidelines to 34 inches at center`() {
        assertEquals(3.0f, CourtSpec.netHeightAt(CourtSpec.COURT_LEFT_X), 0.0001f)
        assertEquals(3.0f, CourtSpec.netHeightAt(CourtSpec.COURT_RIGHT_X), 0.0001f)
        assertEquals(34f / 12f, CourtSpec.netHeightAt(CourtSpec.COURT_CENTER_X), 0.0001f)
    }

    @Test
    fun `classifyPoint identifies kitchen, service boxes, net, and out of bounds`() {
        assertEquals(CourtZone.OUT_OF_BOUNDS, CourtSpec.classifyPoint(1f, 28f))
        assertEquals(CourtZone.OUT_OF_BOUNDS, CourtSpec.classifyPoint(14f, 52f))
        assertEquals(CourtZone.NET_LINE, CourtSpec.classifyPoint(14f, CourtSpec.NET_Y))
        assertEquals(CourtZone.LOWER_KITCHEN, CourtSpec.classifyPoint(14f, 24f))
        assertEquals(CourtZone.UPPER_KITCHEN, CourtSpec.classifyPoint(14f, 32f))
        assertEquals(CourtZone.LOWER_LEFT_SERVICE, CourtSpec.classifyPoint(9f, 12f))
        assertEquals(CourtZone.LOWER_RIGHT_SERVICE, CourtSpec.classifyPoint(19f, 12f))
        assertEquals(CourtZone.UPPER_LEFT_SERVICE, CourtSpec.classifyPoint(9f, 42f))
        assertEquals(CourtZone.UPPER_RIGHT_SERVICE, CourtSpec.classifyPoint(19f, 42f))
    }

    @Test
    fun `kitchen and service box helpers handle court boundaries accurately`() {
        assertTrue(CourtSpec.isInBounds(CourtSpec.COURT_LEFT_X, CourtSpec.COURT_BOTTOM_Y))
        assertTrue(CourtSpec.isInBounds(CourtSpec.COURT_RIGHT_X, CourtSpec.COURT_TOP_Y))
        assertFalse(CourtSpec.isInBounds(CourtSpec.COURT_LEFT_X - 0.1f, CourtSpec.NET_Y))

        assertTrue(CourtSpec.isInKitchen(10f, 21f, CourtSide.LOWER))
        assertTrue(CourtSpec.isInKitchen(10f, 35f, CourtSide.UPPER))
        assertFalse(CourtSpec.isInKitchen(10f, 20.9f, CourtSide.LOWER))

        assertTrue(CourtSpec.isInServiceBox(8f, 15f, CourtSide.LOWER, ServiceBoxSide.LEFT))
        assertFalse(CourtSpec.isInServiceBox(8f, 15f, CourtSide.LOWER, ServiceBoxSide.RIGHT))
    }
}
