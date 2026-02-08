// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.PlatformUpdates
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.updateSettings.impl.UpdateStrategy
import com.intellij.openapi.util.registry.Registry
import com.intellij.pycharm.community.ide.impl.promo.WelcomeToUnifiedWelcomeScreenBanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.time.Duration

/**
 * Keeps promo state across restarts and performs PY release-channel IDE update checks.
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
internal class PyCommunityToUnifiedPromoService(val serviceScope: CoroutineScope) {

  companion object {
    private const val PROMO_REMIND_ME_LATER_LAST_TIME_CLICKED: String = "PROMO_REMIND_ME_LATER_LAST_TIME_CLICKED"
    private const val REG_PROMO_INTERVAL_SECONDS: String = "py.promo.remind.later.interval.seconds"
    private const val PROMO_UPDATE_DECLINED: String = "PROMO_UPDATE_DECLINED"
    private val LOG = logger<PyCommunityToUnifiedPromoService>()
    fun getInstance(): PyCommunityToUnifiedPromoService = service()
  }

  private val lock = Mutex()

  fun getPromoIntervalMillis(): Long {
    val defaultSeconds = (Duration.ofDays(3).seconds).toInt()
    val seconds = Registry.intValue(REG_PROMO_INTERVAL_SECONDS, defaultSeconds).coerceAtLeast(1)
    return seconds * 1000L
  }

  @Volatile
  private var availableUpdate: PlatformUpdates.Loaded? = null

  fun resetPromoState() {
    val propertiesComponent = PropertiesComponent.getInstance()
    propertiesComponent.unsetValue(PROMO_UPDATE_DECLINED)
    propertiesComponent.unsetValue(PROMO_REMIND_ME_LATER_LAST_TIME_CLICKED)
    propertiesComponent.unsetValue(WelcomeToUnifiedWelcomeScreenBanner.BANNER_CLOSED_PROPERTY)

    Registry.get(REG_PROMO_INTERVAL_SECONDS).resetToDefault()
  }

  private fun getLastSuppressedTimeMillis(): Long =
    PropertiesComponent.getInstance().getLong(PROMO_REMIND_ME_LATER_LAST_TIME_CLICKED, 0L)

  /** Call when user clicks "Remind Me Later" in PlatformUpdateDialog. */
  fun onRemindMeLaterClicked() {
    PropertiesComponent.getInstance().setValue(PROMO_REMIND_ME_LATER_LAST_TIME_CLICKED, System.currentTimeMillis().toString())
  }

  fun isUpdateDeclined(): Boolean = PropertiesComponent.getInstance().getBoolean(PROMO_UPDATE_DECLINED, false)

  fun setUpdateDeclined() = PropertiesComponent.getInstance().setValue(PROMO_UPDATE_DECLINED, true)

  fun isSuppressionStillActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
    val ts = getLastSuppressedTimeMillis()
    if (ts == 0L) return false
    return ts > 0L && nowMillis - ts < getPromoIntervalMillis()
  }

  suspend fun isUpdateAvailable(): Boolean {
    return getAvailableUpdate() != null
  }

  fun toolTipWasSuppressed(): Boolean {
    return getLastSuppressedTimeMillis() != 0L
  }

  suspend fun shouldShowWelcomeScreenBanner(): Boolean {
    return isUpdateAvailable() && !isUpdateDeclined()
  }

  suspend fun shouldShowTooltip(): Boolean {
    return isUpdateAvailable() && !toolTipWasSuppressed() && !isUpdateDeclined()
  }

  suspend fun shouldShowModal(): Boolean {
    return isUpdateAvailable() && toolTipWasSuppressed() && !isSuppressionStillActive() && !isUpdateDeclined()
  }

  suspend fun getAvailableUpdate(): PlatformUpdates.Loaded? {
    return runCatching {
      findAvailableUpdate()
    }.onFailure { throwable ->
      LOG.warn("Unexpected error while checking PY update", throwable)
    }.getOrElse { null }
  }

  private suspend fun findAvailableUpdate(): PlatformUpdates.Loaded? {
    return withContext(Dispatchers.IO) {
      lock.withLock {
        withTimeout(Duration.ofSeconds(5)) {
          if (availableUpdate != null) {
            return@withTimeout availableUpdate
          }

          val product = UpdateCheckerFacade.getInstance().loadProductData(null)
          if (product == null) {
            LOG.info("Failed to check PY release update: product data is null")
            return@withTimeout null
          }
          val build = ApplicationInfo.getInstance().build
          val updateSettings = UpdateSettings.getInstance()
          val customization = object : UpdateStrategyCustomization() {
            override val showWhatIsNewPageAfterUpdate: Boolean
              get() = true
          }
          val platformUpdates = UpdateStrategy(build, product, updateSettings, customization).checkForUpdates()
          if (platformUpdates is PlatformUpdates.Loaded &&
              (platformUpdates.updatedChannel.status == ChannelStatus.RELEASE ||
               Registry.`is`("py.community.to.unified.test.ignore.release.channel")) &&
              platformUpdates.newBuild.number.baselineVersion >= 253) {
            availableUpdate = platformUpdates
            return@withTimeout platformUpdates
          }
          return@withTimeout null
        }
      }
    }
  }
}