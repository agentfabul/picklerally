package com.picklerally.core.net

import com.picklerally.core.characters.CharacterId
import com.picklerally.core.court.CourtSpec
import com.picklerally.core.physics.SwipeVector
import com.picklerally.core.sim.OnlineMatch1v1
import com.picklerally.core.sim.RallyPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnlineMatch1v1Test {

    @Test
    fun `room code generator matches PKL-### format and validates`() {
        repeat(15) {
            val code = RoomCodeGenerator.generate()
            assertTrue(RoomCodeGenerator.isValid(code), "Expected valid code, got $code")
        }
        assertFalse(RoomCodeGenerator.isValid("PKL-42"))
        assertFalse(RoomCodeGenerator.isValid("ABC-123"))
        assertTrue(RoomCodeGenerator.isValid("pkl-482"))
    }

    @Test
    fun `perspective mirroring places guest at bottom and host at top`() {
        val hostMatch = OnlineMatch1v1(isHost = true, localUid = "host-1")
        val guestMatch = OnlineMatch1v1(isHost = false, localUid = "guest-2")

        val hostSnap = hostMatch.buildSnapshot()
        guestMatch.applyHostSnapshot(hostSnap)
        repeat(30) {
            guestMatch.update(CourtSpec.FIXED_TIMESTEP_SEC)
        }

        val expectedHostXOnGuest = CourtSpec.WORLD_WIDTH_FT - hostSnap.hostX
        val expectedHostYOnGuest = CourtSpec.WORLD_HEIGHT_FT - hostSnap.hostY

        assertEquals(expectedHostXOnGuest, guestMatch.remotePlayer.x, 0.25f)
        assertEquals(expectedHostYOnGuest, guestMatch.remotePlayer.y, 0.25f)
        assertTrue(guestMatch.localPlayer.y < CourtSpec.NET_Y)
        assertTrue(guestMatch.remotePlayer.y > CourtSpec.NET_Y)
    }

    @Test
    fun `host emits 20 Hz snapshots while stepping 60 Hz physics`() {
        val hostMatch = OnlineMatch1v1(isHost = true, localUid = "host-1")
        var snapshotCount = 0
        repeat(60) {
            if (hostMatch.update(CourtSpec.FIXED_TIMESTEP_SEC) != null) {
                snapshotCount += 1
            }
        }
        assertEquals(20, snapshotCount, "1.0 second of 60 Hz simulation should emit 20 snapshots")
    }

    @Test
    fun `guest input sequence deduplicates swings and mirrors move target`() {
        val hostMatch = OnlineMatch1v1(isHost = true, localUid = "host-1")
        val guestMatch = OnlineMatch1v1(isHost = false, localUid = "guest-2")

        // Host serves to start rally
        val serveSwipe = SwipeVector(dxFt = 0f, dyFt = 3.5f, durationSec = 0.2f)
        assertNull(hostMatch.triggerLocalSwipe(serveSwipe))
        assertEquals(RallyPhase.RALLY_LIVE, hostMatch.rallyPhase)

        // Guest moves in bottom-perspective coordinates -> Host mirrors to upper court
        val moveInput = guestMatch.setLocalMoveTarget(10f, 12f)
        hostMatch.applyRemoteGuestInput(moveInput)
        assertEquals(18f, hostMatch.remotePlayer.targetX, 0.01f)
        assertEquals(44f, hostMatch.remotePlayer.targetY, 0.01f)

        // Duplicate sequence swing must not re-trigger on Host
        val guestSwing = PlayerInputSnapshot(
            uid = "guest-2",
            sequence = 2L,
            targetX = 10f,
            targetY = 12f,
            swingType = "DRIVE",
            aimX = 0.5f,
            aimY = 5.5f,
            swipeDurationSec = 0.16f
        )
        hostMatch.applyRemoteGuestInput(guestSwing)
        hostMatch.applyRemoteGuestInput(guestSwing)
        assertNotNull(hostMatch.buildSnapshot())
    }

    @Test
    fun `host disconnect closes guest match gracefully`() {
        val guestMatch = OnlineMatch1v1(
            isHost = false,
            localUid = "guest-2",
            localChar = CharacterId.LOLA,
            remoteChar = CharacterId.RIO
        )
        guestMatch.onRoomMetaUpdated(RoomStatus.HOST_LEFT)

        assertTrue(guestMatch.hostDisconnected)
        assertEquals(RallyPhase.MATCH_OVER, guestMatch.rallyPhase)
        assertEquals("Host disconnected", guestMatch.lastCallout)
    }
}
