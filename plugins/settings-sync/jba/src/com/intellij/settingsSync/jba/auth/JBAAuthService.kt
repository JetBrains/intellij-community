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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.settingsSync.core.SettingsSyncBundle.message
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncLocalSettings
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.jba.SettingsSyncJbaBundle
import com.intellij.settingsSync.jba.SettingsSyncPromotion
import com.intellij.settingsSync.jba.auth.JBAAuthService.Companion.fromJBAData
import com.intellij.ui.JBAccountInfoService
import com.intellij.ui.JBAccountInfoService.AuthStateListener
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.application
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.SettingsSyncIcons
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue.invokeLater
import java.awt.event.ActionEvent
import java.util.ServiceLoader
import java.util.concurrent.CancellationException
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.JRootPane
import javax.swing.border.Border
import javax.swing.event.HyperlinkEvent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<JBAAuthService>()
private const val JBA_USER_ID = "jba"
private const val JBA_PROVIDER_CODE = "jba"

class JBAAuthService(private val cs: CoroutineScope) : SettingsSyncAuthService {
  @Volatile
  private var invalidatedIdToken: String? = null

  private val listenForLogoutLazy = lazy {
    listenForLogout()
  }

  internal fun isTokenValid(token: String?): Boolean {
    return token != null && token != invalidatedIdToken
  }

  private fun listenForLogout() {
    val messageBusConnection = application.messageBus.connect(cs)
    messageBusConnection.subscribe(AuthStateListener.TOPIC, AuthStateListener { jbaData ->
      if (jbaData == null && RemoteCommunicatorHolder.getAuthService() == this) {
        if (SettingsSyncSettings.getInstance().syncEnabled) {
          SettingsSyncSettings.getInstance().syncEnabled = false
          SettingsSyncLocalSettings.getInstance().userId = null
          SettingsSyncLocalSettings.getInstance().providerCode = null
        }
        SettingsSyncEvents.getInstance().fireLoginStateChanged()
      }
    })
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

  val idToken: String?
    get() {
      val token = getAccountInfoService()?.idToken
      if (!isTokenValid(token)) return null
      listenForLogoutLazy.value
      return token
    }
  override val providerCode: String
    get() = JBA_PROVIDER_CODE
  override val providerName: String
    get() = "JetBrains"

  override val icon = SettingsSyncIcons.JetBrains

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
      val executionModalityState: ModalityState = withContext(Dispatchers.EDT) { ModalityState.current() }
      try {
        val retval = suspendCancellableCoroutine<SettingsSyncUserData?> { cont ->
          try {
            cont.invokeOnCancellation {
              cont.resume(null)
            }

            launch {
              withContext(Dispatchers.EDT + executionModalityState.asContextElement()) {
                val dialog = LogInProgressDialog(
                  parentComponent as JComponent,
                  accountInfoService,
                  loginMetadata,
                  cont,
                )
                dialog.show()
              }
            }
          }
          catch (e: Throwable) {
            LOG.error(e)
            cont.resumeWithException(e)
          }
        }
        if (retval == null) {
          // user may authorize via Manage Licenses dialog
          val updatedAccountService = getAccountInfoService()
          if (updatedAccountService != null && isTokenValid(updatedAccountService.idToken)) {
            return@coroutineScope fromJBAData(updatedAccountService.userData)
          }
        }
        return@coroutineScope retval
      } catch (ex: Throwable) {
        LOG.error("An exception occurred while logging in to JBA", ex)
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
    val instance = JBAccountInfoService.getInstance()
    if (instance == null && !AppMode.isRemoteDevHost()) {
      LOG.info("Attempting to load info service from plugin...")
      val descriptorImpl = PluginManagerCore.findPlugin(PluginId.getId("com.intellij.marketplace")) ?: return null
      val accountInfoService = ServiceLoader.load(JBAccountInfoService::class.java, descriptorImpl.classLoader).findFirst().orElse(null)
      LOG.info("Found info service!")
      return accountInfoService
    }
    return instance
  }

  private fun getAllProductCodes(): Set<String> {
    return PluginManagerCore.loadedPlugins.mapNotNullTo(mutableSetOf()) { it.getProductCode() }
  }

  private suspend fun shouldShowCheckLicenses(): Boolean = coroutineScope {
    val service = getAccountInfoService() ?: return@coroutineScope false
    val allProductCodes = getAllProductCodes()
    return@coroutineScope allProductCodes.map { productCode ->
      async {
        when (val result = service.getAvailableLicenses(productCode).await()) {
          is JBAccountInfoService.LicenseListResult.LicenseList -> {
            result.licenses.isNotEmpty()
          }
          is JBAccountInfoService.LicenseListResult.RequestFailed -> {
            LOG.warn("License request failed for $productCode: ${result.errorMessage}")
            true
          }
          is JBAccountInfoService.LicenseListResult.RequestDeclined -> {
            LOG.warn("License request declined for $productCode: ${result.message}")
            true
          }
          is JBAccountInfoService.AuthRequired -> {
            LOG.warn("Authentication required for license check for $productCode")
            true
          }
        }
      }
    }.awaitAll().any()
  }

  override val logoutFunction: (suspend (Component) -> Unit)?
    get() {
      if (RemoteCommunicatorHolder.getExternalProviders().isEmpty())
        return null

      return lambda@{ component ->
        val shouldShowCheckLicenses = try {
          withModalProgress(
            ModalTaskOwner.component(component),
            SettingsSyncJbaBundle.message("check.licenses.progress.text"),
            TaskCancellation.cancellable()
          ) {
            withTimeout(3.seconds) {
              shouldShowCheckLicenses()
            }
          }
        } catch (_: TimeoutCancellationException) {
          LOG.warn("Timeout while checking licenses")
          true
        } catch (_ : CancellationException) {
          return@lambda
        }

        val dialog = ConfirmLogoutDialog(component, shouldShowCheckLicenses)
        if (dialog.showAndGet()) {
          performLogout(component)
        }
      }
    }

  override val contactSupportFunction: (() -> Unit)?
    get() {
      val actionManager = ActionManager.getInstance()
      val contactSupportAction = actionManager.getAction("TechnicalSupport") ?: return null

      return {
        ActionUtil.performAction(
          contactSupportAction,
          AnActionEvent.createEvent(contactSupportAction, DataContext.EMPTY_CONTEXT, null, "SettingsSyncRemoteDataRemovalNotification", ActionUiKind.NONE, null)
        )
      }
    }

  private fun performLogout(component: Component) {
    if (RemoteCommunicatorHolder.getExternalProviders().isEmpty())
      return
    val accountInfoService = getAccountInfoService()
    if (accountInfoService != null) {
      if (AppMode.isRemoteDevHost()) {
        showManageLicensesDialog(component)
      }
      accountInfoService.performLogout()
    }
    else {
      LOG.error("JBA auth service is not available!")
    }
  }

  internal var authRequiredAction: SettingsSyncAuthService.PendingUserAction? = null

  override fun getPendingUserAction(userId: String): SettingsSyncAuthService.PendingUserAction? = authRequiredAction

  companion object {
    fun fromJBAData(jbaData: JBAccountInfoService.JBAData?) : SettingsSyncUserData? {
      if (jbaData == null) {
        return null
      } else {
        return SettingsSyncUserData(
          JBA_USER_ID,
          JBA_PROVIDER_CODE,
          jbaData.email,
          jbaData.email,
        )
      }
    }

    fun showManageLicensesDialog(component: Component) {
      val actionManager = ActionManager.getInstance()
      val registerPluginAction = actionManager.getAction("RegisterPlugins")
      if (registerPluginAction != null) { // community
        ActionUtil.performAction(registerPluginAction, AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, Presentation(), "", ActionUiKind.NONE, null))
      }
      else {
        val registerAction = actionManager.getAction("Register") // ultimate
        if (registerAction != null) {
          val dataContext = DataContext { dataId: String? ->
            when (dataId) {
              "register.request.direct.call" -> true
              else -> null
            }
          }
          ActionUtil.performAction(registerAction, AnActionEvent.createEvent(dataContext, Presentation(), "", ActionUiKind.NONE, null))
        }
        else {
          Messages.showErrorDialog(component, SettingsSyncJbaBundle.message("manage.license.not.found.error.message"))
        }
      }
    }
  }
}

private class LogInProgressDialog(
  parent: JComponent,
  private val accountInfoService: JBAccountInfoService,
  private val loginMetadata: Map<String, String>,
  private val continuation: CancellableContinuation<SettingsSyncUserData?>
) : DialogWrapper(parent, false) {
  private var loginSession: JBAccountInfoService.LoginSession? = null

  init {
    title = SettingsSyncJbaBundle.message("login.title")
    init()
    rootPane.windowDecorationStyle = JRootPane.FRAME

    startLoginSession()
  }

  private fun startLoginSession() {
    try {
      loginSession = accountInfoService.startLoginSession(
        JBAccountInfoService.LoginMode.AUTO,
        null,
        loginMetadata
      )

      loginSession!!.onCompleted()
        .exceptionally { exc ->
          if (exc.cause is CancellationException || exc is CancellationException) {
            LOG.warn("Login cancelled")
          } else {
            LOG.warn("Login failed", exc)
          }
          continuation.resume(null)
          invokeLater {
            close(CANCEL_EXIT_CODE)
          }
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
          continuation.resume(result)
          invokeLater {
            close(OK_EXIT_CODE)
          }
        }
    }
    catch (e: Throwable) {
      LOG.error(e)
      continuation.resumeWithException(e)
      invokeLater {
        close(CANCEL_EXIT_CODE)
      }
    }
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
          loginSession?.close()
        }
      }
      row {
        text(SettingsSyncJbaBundle.message("login.troubles.message")).applyToComponent {
          addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
              JBAAuthService.showManageLicensesDialog(contentPane)
              loginSession?.close()
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

private class ConfirmLogoutDialog(parent: Component, private val shouldCheckLicenses: Boolean) : DialogWrapper(parent, false) {
  private lateinit var confirmCheckbox: Cell<JBCheckBox>
  private lateinit var logOutAction: AbstractAction
  private lateinit var checkLicensesAction: AbstractAction

  init {
    title = message("title.settings.sync")
    init()
  }

  override fun createContentPaneBorder(): Border {
    val insets = JButton().insets
    return JBUI.Borders.empty(20, 20, 14 - insets.bottom, 20)
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        icon(AllIcons.General.QuestionDialog).align(AlignY.TOP)
        panel {
          row {
            text(SettingsSyncJbaBundle.message("confirm.logout.title")).applyToComponent {
              font = JBFont.h4()
            }
          }
          row {
            text(if (shouldCheckLicenses) SettingsSyncJbaBundle.message("confirm.logout.licenses.text") else SettingsSyncJbaBundle.message("confirm.logout.text"))
          }
          if (shouldCheckLicenses) {
            row {
              confirmCheckbox = checkBox(SettingsSyncJbaBundle.message("confirm.logout.checkbox.text"))
                .applyToComponent {
                  addActionListener {
                    logOutAction.isEnabled = isSelected
                  }
                }
            }
          }
        }
      }
    }
  }

  override fun createActions(): Array<Action> {
    val cancelAction = getCancelAction()
    checkLicensesAction = object : AbstractAction(SettingsSyncJbaBundle.message("confirm.logout.check.licenses.button")) {
      override fun actionPerformed(e: ActionEvent) {
        JBAAuthService.showManageLicensesDialog(contentPane)
      }
    }

    logOutAction = object : AbstractAction(SettingsSyncJbaBundle.message("confirm.logout.check.logout.button")) {
      init {
        putValue(DEFAULT_ACTION, true)
      }
      override fun actionPerformed(e: ActionEvent) {
        if (!shouldCheckLicenses || confirmCheckbox.component.isSelected) {
          close(OK_EXIT_CODE)
        }
      }
    }.apply { isEnabled = !shouldCheckLicenses }
    return if (shouldCheckLicenses) {
      arrayOf(logOutAction, cancelAction, checkLicensesAction)
    } else {
      arrayOf(logOutAction, cancelAction)
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return if (shouldCheckLicenses) getButton(checkLicensesAction) else getButton(logOutAction)
  }
}
