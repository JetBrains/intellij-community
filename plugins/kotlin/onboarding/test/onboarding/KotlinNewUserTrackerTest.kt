// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package onboarding

import com.intellij.internal.statistic.DeviceIdManager
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_USER_DURATION
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_USER_RESET
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinNewUserTrackerTest {
    private val now = Instant.now()
    private val today = LocalDate.now()

    private fun Long.isRecentEpochTimestamp(): Boolean =
        Duration.between(Instant.ofEpochSecond(this), Instant.now()) < Duration.ofSeconds(30)

    private fun LocalDate.daysSinceDate(): Int = ChronoUnit.DAYS.between(this, today).toInt()

    private fun createInstance(installationDate: LocalDate? = LocalDate.of(2024, 1, 15)): KotlinNewUserTracker {
        val installationId = installationDate?.let {
            DeviceIdManager.generateId(installationDate, 'B')
        }
        val tracker = KotlinNewUserTracker()
        tracker.deviceIdProvider = { installationId }
        return tracker
    }

    @Test
    fun `The empty state should have correct values`() {
        val instance = createInstance()
        assertFalse(instance.isNewKtUser())
        assertTrue(instance.state.firstKtFileOpened == 0L)
        assertTrue(instance.state.newKtUserSince == 0L)
        assertTrue(instance.state.firstKtFileOpened == 0L)
        assertNull(instance.getFirstKotlinUsageDate())
        assertFalse(instance.hasUsedKotlin())
    }

    @Test
    fun `Users who opened a Kotlin file stay Kotlin users after the new user period`() {
        val instance = createInstance()
        instance.onKtFileOpened()
        assertTrue(instance.hasUsedKotlin())
        instance.state.newKtUserSince = (now - NEW_USER_DURATION - Duration.ofHours(1)).epochSecond
        assertFalse(instance.isNewKtUser())
        assertTrue(instance.hasUsedKotlin())
    }

    @Test
    fun `Existing idea users should be marked as new if they open a kotlin file for the first time`() {
        val instance = createInstance()
        instance.onKtFileOpened()
        assertTrue(instance.state.firstKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.state.lastKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.isNewKtUser())
        assertNotNull(instance.getFirstKotlinUsageDate())
        // We are very careful and check <= 1 here in case the test changes over at that time, which is almost never going to happen
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() <= 1)
    }

    @Test
    fun `Old users coming back to Kotlin after a long time should be marked as new users`() {
        val instance = createInstance()
        instance.state.lastKtFileOpened = (now - NEW_USER_RESET - Duration.ofHours(1)).epochSecond
        // this is just some random time that we want to stay unaltered; the actual value does not matter
        val originalFirstKtFileOpened = (now - Duration.ofDays(180)).epochSecond
        instance.state.firstKtFileOpened = originalFirstKtFileOpened
        instance.state.newKtUserSince = (now - NEW_USER_DURATION - Duration.ofHours(1)).epochSecond
        assertFalse(instance.isNewKtUser())

        instance.onKtFileOpened()
        assertTrue(instance.state.lastKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.state.newKtUserSince.isRecentEpochTimestamp())
        assertEquals(originalFirstKtFileOpened, instance.state.firstKtFileOpened)
        // User is now new
        assertTrue(instance.isNewKtUser())
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() <= 1)
    }

    @Test
    fun `First Kotlin usage date follows the new user start`() {
        val instance = createInstance()
        instance.state.newKtUserSince = (now - Duration.ofDays(7) + Duration.ofHours(1)).epochSecond
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() in 6..8)
    }

    @Test
    fun `New user status should expire after some time`() {
        val instance = createInstance()
        assertFalse(instance.isNewKtUser())
        instance.state.newKtUserSince = (now - NEW_USER_DURATION + Duration.ofHours(1)).epochSecond
        assertTrue(instance.isNewKtUser())
        instance.state.newKtUserSince = (now - NEW_USER_DURATION - Duration.ofHours(1)).epochSecond
        assertFalse(instance.isNewKtUser())
    }

    @Test
    fun `Installation date is null if DeviceId could not be read`() {
        val instance = createInstance(null)
        assertNull(instance.getInstallationDate())
    }

    @Test
    fun `Installation date is read from the DeviceId`() {
        val installationDate = LocalDate.of(2024, 3, 15)
        val instance = createInstance(installationDate)
        assertEquals(installationDate, instance.getInstallationDate())
    }
}
