package com.intellij.settingsSync.jba.auth

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.ui.ExitActionType
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.SettingsSyncJbaBundle
import com.intellij.settingsSync.jba.SettingsSyncPromotion
import com.intellij.ui.JBAccountInfoService
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.Component
import java.awt.Dimension
import java.util.ServiceLoader
import java.util.concurrent.CancellationException
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JRootPane
import javax.swing.event.HyperlinkEvent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class JBAAuthService() : SettingsSyncAuthService {

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
    val accountInfoService = getAccountInfoService()
    val loginMetadata = hashMapOf(
      "from.settings.sync" to "true"
    )
    if (SettingsSyncPromotion.promotionShownThisSession) {
      loginMetadata["from.settings.sync.promotion"] = "true"
    }
    if (accountInfoService == null) {
      LOG.error("JBA auth service is not available!")
      return null
    }
    if (isTokenValid(accountInfoService.idToken)) {
      return fromJBAData(accountInfoService.userData)
    }


    return coroutineScope {
      val job: Job? = coroutineContext[Job]
      var dialog: LogInProgressDialog? = null
      val executionModalityState: ModalityState = withContext(Dispatchers.EDT) { ModalityState.current() }
      try {
        launch {
          withContext(Dispatchers.EDT + executionModalityState.asContextElement()) {
            dialog = LogInProgressDialog(parentComponent as JComponent)
            dialog.show()
          }
        }
        val retval = suspendCancellableCoroutine<SettingsSyncUserData?> { cont ->
          try {
            if (job != null)
              dialog?.addJobToCancel(job)
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
        return@coroutineScope retval
      } catch (ex: Throwable) {
        LOG.error("An exception occurred while logging in to JBA", ex)
      } finally {
        withContext(Dispatchers.EDT + executionModalityState.asContextElement() ) {
          dialog?.close(OK_EXIT_CODE, ExitActionType.OK);
        }
      }
      return@coroutineScope null
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
    var instance = JBAccountInfoService.getInstance()
    if (instance == null && !AppMode.isRemoteDevHost()) {
      LOG.info("Attempting to load info service from plugin...")
      val descriptorImpl = PluginManagerCore.findPlugin(PluginId.getId("com.intellij.marketplace")) ?: return null
      val accountInfoService = ServiceLoader.load(JBAccountInfoService::class.java, descriptorImpl.classLoader).findFirst().orElse(null)
      LOG.info("Found info service!")
      return accountInfoService
    }
    return instance
  }
}

private class LogInProgressDialog(parent: JComponent) : DialogWrapper(parent, false) {
  private var job2Cancel: Job? = null

  init {
    title = SettingsSyncJbaBundle.message("login.title")
    init()
    rootPane.windowDecorationStyle = JRootPane.FRAME
  }

  fun addJobToCancel(job: Job) {
    job2Cancel = job
  }

  override fun isProgressDialog() = true

  override fun createCenterPanel(): JComponent {
    val panel = panel {
      row {
        val progressBar = JProgressBar()
        cell(progressBar).resizableColumn().applyToComponent {
          isIndeterminate = true
        }.customize(UnscaledGaps(0, 0, 0, 10))
        progressBar.preferredSize = Dimension(if (SystemInfoRt.isMac) 350 else scale(450), 4)
        button(CommonBundle.getCancelButtonText()) {
          job2Cancel?.cancel()
          this@LogInProgressDialog.doCancelAction()
        }
      }
      row {
        text(SettingsSyncJbaBundle.message("login.troubles.message")).applyToComponent {
          addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
              job2Cancel?.cancel()
              val action = ActionUtil.getAction("Register")!!
              ActionUtil.performActionDumbAwareWithCallbacks(action, AnActionEvent(
                DataContext.EMPTY_CONTEXT, Presentation(), "", ActionUiKind.NONE, null, 0, ActionManager.getInstance()
              ))
            }
          }
          if (SystemInfoRt.isMac) {
            UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, this)
          }
        }.align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 10))
      }
    }
    return panel
  }

  override fun createActions(): Array<out Action?> {
    return emptyArray()
  }
}