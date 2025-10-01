// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.promotion.communityToUnified

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization
import com.intellij.openapi.updateSettings.impl.*
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
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

  private fun readMockUpdateIfEnabled(): PlatformUpdates.Loaded? {
    return try {
      if (!Registry.`is`("py.promo.use.test.update", false)) return null
      val path: Path = Path.of(PathManager.getHomePath(), "py_promo_test.xml")
      if (!Files.isRegularFile(path)) {
        LOG.info("Mock PY promo update enabled, but file not found: $path")
        null
      }
      else {
        val text = Files.readString(path)
        val currentBuild = ApplicationInfo.getInstance().build
        val productCode = currentBuild.productCode
        val node = JDOMUtil.load(text)
                     .getChild("product")
                     ?.getChild("channel")
                   ?: throw IllegalArgumentException("//channel missing")
        val channel = UpdateChannel(node, productCode)
        val newBuild = channel.builds.firstOrNull()
                       ?: throw IllegalArgumentException("//build missing")
        val patches = newBuild.patches.firstOrNull()
          ?.let { UpdateChain(listOf(it.fromBuild, newBuild.number), it.size) }

        return PlatformUpdates.Loaded(newBuild, channel, patches)
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to read/parse mock PY promo update", t)
      null
    }
  }

  private suspend fun findAvailableUpdate(): PlatformUpdates.Loaded? {
    return withContext(Dispatchers.IO) {
      lock.withLock {
        withTimeout(Duration.ofSeconds(5)) {
          if (availableUpdate != null) {
            return@withTimeout availableUpdate
          }

          // Try mocked update if registry flag is enabled
          readMockUpdateIfEnabled()?.let { mockUpdate ->
            if (mockUpdate.newBuild.number.productCode == "PY") {
              availableUpdate = mockUpdate
              return@withTimeout mockUpdate
            }
          }

          val updateCheckerFacade = service<UpdateCheckerFacade>()
          val product = updateCheckerFacade.loadProductData(null)
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
          if (platformUpdates is PlatformUpdates.Loaded && platformUpdates.newBuild.number.productCode == "PY") {
            availableUpdate = platformUpdates
            return@withTimeout platformUpdates
          }
          return@withTimeout null
        }
      }
    }
  }
}