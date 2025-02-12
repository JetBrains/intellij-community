package com.intellij.settingsSync.jba.auth

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.SettingsSyncJbaBundle
import com.intellij.settingsSync.jba.SettingsSyncPromotion
import com.intellij.ui.JBAccountInfoService
import kotlinx.coroutines.*
import java.awt.Component
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class JBAAuthService : SettingsSyncAuthService {

  companion object {
    private val LOG = logger<JBAAuthService>()
    private const val JBA_USER_ID = "jba"
  }

  @Volatile
  private var invalidatedIdToken: String? = null

  private fun isTokenValid(token: String?): Boolean {
    return token != null && token != invalidatedIdToken
  }

  override fun getUserData(userId: String) = fromJBAData(
      if (ApplicationManagerEx.isInIntegrationTest()) {
        DummyJBAccountInfoService.userData
      } else {
        getAccountInfoService()?.userData
      }
    )

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    val userData = getUserData("")
    if (userData != null) {
      return listOf(userData)
    } else {
      return emptyList()
    }
  }

  private fun fromJBAData(jbaData: JBAccountInfoService.JBAData?) : SettingsSyncUserData? {
    if (jbaData == null) {
      return null
    } else {
      return SettingsSyncUserData(
        JBA_USER_ID,
        providerCode,
        jbaData.email,
        jbaData.email,
      )
    }
  }

  val idToken: String?
    get() {
      val token = getAccountInfoService()?.idToken
      if (!isTokenValid(token)) return null
      return token
    }
  override val providerCode: String
    get() = "jba"
  override val providerName: String
    get() = "JetBrains"

  override val icon = AllIcons.Ultimate.IdeaUltimatePromo

  override suspend fun login(parentComponent: Component?): SettingsSyncUserData? {
    val modalTaskOwner = if (parentComponent != null)
      ModalTaskOwner.component(parentComponent)
    else
      ModalTaskOwner.guess()
    return withModalProgress(modalTaskOwner, SettingsSyncJbaBundle.message("login.manual.helper.text"), TaskCancellation.cancellable()) {
      val accountInfoService = getAccountInfoService()
      val loginMetadata = hashMapOf(
        "from.settings.sync" to "true"
      )
      if (SettingsSyncPromotion.promotionShownThisSession) {
        loginMetadata["from.settings.sync.promotion"] = "true"
      }
      if (accountInfoService == null) {
        LOG.error("JBA auth service is not available!")
        return@withModalProgress null
      }
      if (isTokenValid(accountInfoService.idToken)) {
        return@withModalProgress fromJBAData(accountInfoService.userData)
      }
      suspendCancellableCoroutine<SettingsSyncUserData?> { cont ->
        try {
          cont.invokeOnCancellation {
            cont.resume(null)
          }
          accountInfoService
            .startLoginSession(JBAccountInfoService.LoginMode.AUTO, null, loginMetadata)
            .onCompleted()
            .exceptionally { exc ->
            if (exc is CancellationException) {
              LOG.warn("Login cancelled")
            }
            else {
              LOG.warn("Login failed", exc)
            }
            cont.resume(null)
            null
          }.thenApply { loginResult ->
            val result: SettingsSyncUserData? = when (loginResult) {
              is JBAccountInfoService.LoginResult.LoginSuccessful -> {
                fromJBAData(loginResult.jbaUser)
              }
              is JBAccountInfoService.LoginResult.LoginFailed -> {
                LOG.warn("Login failed: ${loginResult.errorMessage}")
                null
              }
              else -> {
                LOG.warn("Unknown login result: $loginResult")
                null
              }
            }
            cont.resume(result)
          }
        }
        catch (e: Throwable) {
          LOG.error(e)
          cont.resumeWithException(e)
        }
      }
    }
  }

  fun invalidateJBA(idToken: String) {
    if (invalidatedIdToken == idToken) return

    LOG.warn("Invalidating JBA Token")
    invalidatedIdToken = idToken
    SettingsSyncEvents.Companion.getInstance().fireLoginStateChanged()
  }

  // Extracted to simplify testing
  fun getAccountInfoService(): JBAccountInfoService? {
    if (ApplicationManagerEx.isInIntegrationTest() || System.getProperty("settings.sync.test.auth") == "true") {
      return DummyJBAccountInfoService
    }
    return JBAccountInfoService.getInstance()
  }
}