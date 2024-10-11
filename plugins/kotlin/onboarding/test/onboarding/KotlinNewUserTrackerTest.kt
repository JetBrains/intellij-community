// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package onboarding

import com.intellij.internal.statistic.DeviceIdManager
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_IDEA_USER_DURATION
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_USER_DURATION
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_USER_RESET
import org.jetbrains.kotlin.onboarding.KotlinNewUserTracker.Companion.NEW_USER_SURVEY_DELAY
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.test.*

class KotlinNewUserTrackerTest {
    private val now = Instant.now()
    private val today = LocalDate.now()

    private fun Long.isRecentEpochTimestamp(): Boolean =
        Duration.between(Instant.ofEpochSecond(this), Instant.now()) < Duration.ofSeconds(30)

    private fun LocalDate.daysSinceDate(): Int = ChronoUnit.DAYS.between(this, today).toInt()

    private fun createInstance(
        installationDate: LocalDate? = LocalDate.now().minusDays(NEW_IDEA_USER_DURATION.toDays() + 1)
    ): KotlinNewUserTracker {
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
        assertFalse(instance.shouldShowNewUserDialog())
        assertFalse(instance.isNewKtUser())
        assertTrue(instance.state.firstKtFileOpened == 0L)
        assertTrue(instance.state.newKtUserSince == 0L)
        assertTrue(instance.state.firstKtFileOpened == 0L)
        assertNull(instance.getFirstKotlinUsageDate())
    }

    @Test
    fun `Existing idea users should be marked as new if they open a kotlin file for the first time`() {
        val instance = createInstance()
        instance.onKtFileOpened()
        assertTrue(instance.state.firstKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.state.lastKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.isNewKtUser())
        assertFalse(instance.shouldShowNewUserDialog())
        assertNotNull(instance.getFirstKotlinUsageDate())
        // We are very careful and check <= 1 here in case the test changes over at that time, which is almost never going to happen
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() <= 1)
    }

    @Test
    fun `New idea users should be marked as new if they open a kotlin file for the first time`() {
        val instance = createInstance(LocalDate.now().plusDays(1))
        instance.onKtFileOpened()
        assertTrue(instance.state.firstKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.state.lastKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.isNewKtUser())
        assertFalse(instance.shouldShowNewUserDialog())
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
        assertFalse(instance.shouldShowNewUserDialog())

        instance.onKtFileOpened()
        assertTrue(instance.state.lastKtFileOpened.isRecentEpochTimestamp())
        assertTrue(instance.state.newKtUserSince.isRecentEpochTimestamp())
        assertEquals(originalFirstKtFileOpened, instance.state.firstKtFileOpened)
        // User is now new
        assertTrue(instance.isNewKtUser())
        // User has not passed the new user period
        assertFalse(instance.shouldShowNewUserDialog())
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() <= 1)
    }

    @Test
    fun `Show new user dialog if enough time has passed since becoming new user`() {
        val instance = createInstance()
        instance.state.newKtUserSince = (now - NEW_USER_SURVEY_DELAY + Duration.ofHours(1)).epochSecond
        assertFalse(instance.shouldShowNewUserDialog())
        val newUserDays = NEW_USER_SURVEY_DELAY.toDays()
        assertTrue(instance.getFirstKotlinUsageDate()!!.daysSinceDate() in (newUserDays - 1..newUserDays + 1))
    }

    @Test
    fun `Do not show new user dialog if not enough time has passed since becoming new user`() {
        val instance = createInstance()
        instance.state.newKtUserSince = (now - NEW_USER_SURVEY_DELAY - Duration.ofHours(1)).epochSecond
        assertFalse(instance.shouldShowNewUserDialog())
    }

    @Test
    fun `Do not show new user dialog if too much time has passed since becoming new user`() {
        val instance = createInstance()
        instance.state.newKtUserSince = (now - NEW_USER_DURATION - Duration.ofHours(1)).epochSecond
        assertFalse(instance.shouldShowNewUserDialog())
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
    fun `Never be a new idea user if DeviceId could not be read`() {
        val instance = createInstance(null)
        assertFalse(instance.isNewIdeaUser())
    }

    @Test
    fun `New IDEA users should be detected as new if they have recently installed IDEA`() {
        val instance = createInstance(LocalDate.now().minusDays(NEW_IDEA_USER_DURATION.toDays() - 2))
        assertTrue(instance.isNewIdeaUser())
    }

    @Test
    fun `New IDEA users should no longer be classed as new if they have installed IDEA too long ago`() {
        val instance = createInstance(LocalDate.now().minusDays(NEW_IDEA_USER_DURATION.toDays() + 2))
        assertFalse(instance.isNewIdeaUser())
    }
}
