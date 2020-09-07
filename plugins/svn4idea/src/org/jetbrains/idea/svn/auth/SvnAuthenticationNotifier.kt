// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth

import com.intellij.concurrency.JobScheduler
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.getWarningIcon
import com.intellij.openapi.ui.Messages.showYesNoDialog
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NamedRunnable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlChunk.link
import com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces
import com.intellij.openapi.vcs.impl.GenericNotifierImpl
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier.showOverChangesView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ThreeState
import com.intellij.util.io.delete
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.proxy.CommonProxy
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.svn.RootsToWorkingCopies
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnConfigurable.selectConfigurationDirectory
import org.jetbrains.idea.svn.SvnConfiguration
import org.jetbrains.idea.svn.SvnUtil.isAuthError
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.ClientFactory
import org.jetbrains.idea.svn.api.Revision
import org.jetbrains.idea.svn.api.Target
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import org.jetbrains.idea.svn.info.InfoClient
import java.awt.Component
import java.awt.Point
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths
import java.util.Collections.synchronizedMap
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

private val LOG = logger<SvnAuthenticationNotifier>()

@NonNls
private val AUTH_KINDS =
  listOf(SvnAuthenticationManager.PASSWORD, "svn.ssh", SvnAuthenticationManager.SSL, "svn.username", "svn.ssl.server", "svn.ssh.server")

@Service
class SvnAuthenticationNotifier(project: Project) :
  GenericNotifierImpl<SvnAuthenticationNotifier.AuthenticationRequest, Url>(
    project,
    SvnVcs.VCS_DISPLAY_NAME,
    message("notification.title.not.logged.into.subversion"),
    NotificationType.ERROR
  ),
  Disposable {

  private val myCopiesPassiveResults = synchronizedMap(mutableMapOf<Url, Boolean>())
  private val myTimer = JobScheduler.getScheduler().scheduleWithFixedDelay(
    { myCopiesPassiveResults.clear() },
    10, 10 * 60, TimeUnit.SECONDS
  )

  @Volatile
  private var myVerificationInProgress = false

  private val vcs: SvnVcs get() = SvnVcs.getInstance(myProject)
  private val rootsToWorkingCopies: RootsToWorkingCopies get() = RootsToWorkingCopies.getInstance(myProject)

  override fun dispose() {
    myTimer.cancel(false)
  }

  override fun ask(obj: AuthenticationRequest, description: String?): Boolean {
    if (myVerificationInProgress) {
      showAlreadyChecking()
      return false
    }
    myVerificationInProgress = true

    val resultRef = Ref<Boolean>()
    val checker = {
      try {
        val result = interactiveValidation(obj.myProject, obj.url, obj.realm, obj.kind)
        LOG.debug("ask result for: ${obj.url} is: $result")
        resultRef.set(result)
        if (result) {
          onStateChangedToSuccess(obj)
        }
      }
      finally {
        myVerificationInProgress = false
      }
    }
    // also do not show auth if thread does not have progress indicator
    return if (getApplication().isReadAccessAllowed || !ProgressManager.getInstance().hasProgressIndicator()) {
      getApplication().executeOnPooledThread(checker)
      false
    }
    else {
      checker()
      resultRef.get()
    }
  }

  private fun showAlreadyChecking() {
    val frameFor = WindowManagerEx.getInstanceEx().findFrameFor(myProject) ?: return
    val component = frameFor.component
    val point = component.mousePosition ?: Point((component.width * 0.7).toInt(), 0)

    SwingUtilities.convertPointToScreen(point, component)
    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(message("popup.content.already.checking"), MessageType.WARNING, null)
      .createBalloon()
      .show(RelativePoint(point), Balloon.Position.below)
  }

  private fun onStateChangedToSuccess(obj: AuthenticationRequest) {
    myCopiesPassiveResults[getKey(obj)] = true
    vcs.invokeRefreshSvnRoots()

    val outdatedRequests = mutableListOf<Url>()
    for (key in allCurrentKeys) {
      val commonAncestor = key.commonAncestorWith(obj.url)
      if (commonAncestor != null && !isEmptyOrSpaces(commonAncestor.host) && !isEmptyOrSpaces(commonAncestor.path)) {
        outdatedRequests.add(key)
      }
    }
    LOG.debug("on state changed ")
    getApplication().invokeLater(
      {
        outdatedRequests.forEach { removeLazyNotificationByKey(it) }
      }, ModalityState.NON_MODAL
    )
  }

  override fun ensureNotify(obj: AuthenticationRequest): Boolean {
    myCopiesPassiveResults.remove(getKey(obj))
    return super.ensureNotify(obj)
  }

  override fun onFirstNotification(obj: AuthenticationRequest) = ProgressManager.getInstance().hasProgressIndicator() && ask(obj, null)
  override fun getKey(obj: AuthenticationRequest) = obj.wcUrl!!

  fun getWcUrl(obj: AuthenticationRequest): Url? {
    if (obj.isOutsideCopies) return null
    if (obj.wcUrl != null) return obj.wcUrl

    val copy = rootsToWorkingCopies.getMatchingCopy(obj.url)
    if (copy != null) {
      obj.isOutsideCopies = false
      obj.wcUrl = copy.url
    }
    else {
      obj.isOutsideCopies = true
    }
    return copy?.url
  }

  /**
   * Bases on presence of notifications!
   */
  fun isAuthenticatedFor(vf: VirtualFile, factory: ClientFactory?): ThreeState {
    val wcCopy = rootsToWorkingCopies.getWcRoot(vf) ?: return ThreeState.UNSURE

    val haveCancellation = getStateFor(wcCopy.url)
    if (haveCancellation) return ThreeState.NO

    val keptResult = myCopiesPassiveResults[wcCopy.url]
    if (java.lang.Boolean.TRUE == keptResult) return ThreeState.YES
    if (java.lang.Boolean.FALSE == keptResult) return ThreeState.NO

    val calculatedResult = if (factory == null) passiveValidation(vcs, wcCopy.url) else passiveValidation(factory, wcCopy.url)
    myCopiesPassiveResults[wcCopy.url] = calculatedResult
    return ThreeState.fromBoolean(calculatedResult)
  }

  override fun getNotificationContent(obj: AuthenticationRequest): @NlsContexts.NotificationContent String {
    val action = link("", message("notification.action.click.to.fix")).toString()
    val content = message("notification.content.not.logged.into.subversion", obj.realm, obj.url.toDecodedString())

    return "$action $content"
  }

  class AuthenticationRequest(val myProject: Project, val kind: String, val url: Url, val realm: String) {
    var wcUrl: Url? = null
    var isOutsideCopies: Boolean = false
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SvnAuthenticationNotifier = project.service()

    // TODO: Looks like passive authentication for command line integration could show dialogs for proxy errors. So, it could make sense to
    // TODO: reuse some logic from validationImpl().
    // TODO: Also SvnAuthenticationNotifier is not called for command line integration (ensureNotify() is called only in SVNKit lifecycle).
    // TODO: Though its logic with notifications seems rather convenient. Fix this.
    private fun passiveValidation(factory: ClientFactory, url: Url) = try {
      factory.create(InfoClient::class.java, false).doInfo(Target.on(url), Revision.UNDEFINED) != null
    }
    catch (ignore: SvnBindException) {
      false
    }

    @JvmStatic
    fun passiveValidation(vcs: SvnVcs, url: Url): Boolean {
      val configuration = vcs.svnConfiguration
      val passiveManager = configuration.getPassiveAuthenticationManager(vcs)
      return validationImpl(vcs.project, url, configuration, passiveManager, false, null, null, false)
    }

    fun interactiveValidation(project: Project, url: Url, realm: String, kind: String): Boolean {
      val configuration = SvnConfiguration.getInstance(project)
      val passiveManager = configuration.getInteractiveManager(SvnVcs.getInstance(project))
      return validationImpl(project, url, configuration, passiveManager, true, realm, kind, true)
    }

    private fun validationImpl(project: Project,
                               url: Url,
                               configuration: SvnConfiguration,
                               manager: SvnAuthenticationManager,
                               checkWrite: Boolean,
                               realm: String?,
                               kind: String?,
                               interactive: Boolean): Boolean {
      // we should also NOT show proxy credentials dialog if at least fixed proxy was used, so
      var proxyToRelease: Proxy? = null
      if (!interactive && configuration.isUseDefaultProxy) {
        val instance = HttpConfigurable.getInstance()
        if (instance.USE_HTTP_PROXY && instance.PROXY_AUTHENTICATION && (isEmptyOrSpaces(instance.proxyLogin) || isEmptyOrSpaces(
            instance.plainProxyPassword))) {
          return false
        }
        if (instance.USE_PROXY_PAC) {
          val select = try {
            CommonProxy.getInstance().select(URI(url.toString()))
          }
          catch (e: URISyntaxException) {
            LOG.info("wrong URL: $url")
            return false
          }

          for (proxy in select) {
            if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP == proxy.type()) {
              val address = proxy.address() as InetSocketAddress
              val password = HttpConfigurable.getInstance().getGenericPassword(address.hostName, address.port)
              if (password == null) {
                CommonProxy.getInstance().noAuthentication("http", address.hostName, address.port)
                proxyToRelease = proxy
              }
            }
          }
        }
      }
      SvnInteractiveAuthenticationProvider.clearCallState()
      val target = Target.on(url)
      try {
        SvnVcs.getInstance(project).getFactory(target).create(InfoClient::class.java, interactive).doInfo(target, Revision.HEAD)
      }
      catch (e: ProcessCanceledException) {
        return false
      }
      catch (e: SvnBindException) {
        if (isAuthError(e)) {
          LOG.debug(e)
          return false
        }
        LOG.info("some other exc", e)
        if (interactive) {
          showAuthenticationFailedWithHotFixes(project, configuration, e)
        }
        return false
      }
      finally {
        if (!interactive && configuration.isUseDefaultProxy && proxyToRelease != null) {
          val address = proxyToRelease.address() as InetSocketAddress
          CommonProxy.getInstance().noAuthentication("http", address.hostName, address.port)
        }
      }

      if (!checkWrite) return true

      if (SvnInteractiveAuthenticationProvider.wasCalled() && SvnInteractiveAuthenticationProvider.wasCancelled()) return false
      if (SvnInteractiveAuthenticationProvider.wasCalled()) return true

      val svnVcs = SvnVcs.getInstance(project)

      val provider = SvnInteractiveAuthenticationProvider(svnVcs, manager)
      val svnAuthentication = provider.requestClientAuthentication(kind, url, realm, true)
      if (svnAuthentication != null) {
        configuration.acknowledge(kind!!, realm!!, svnAuthentication)
        configuration.getAuthenticationManager(svnVcs).acknowledgeAuthentication(kind, url, realm, svnAuthentication)
        return true
      }
      return false
    }

    private fun showAuthenticationFailedWithHotFixes(project: Project, configuration: SvnConfiguration, e: SvnBindException) =
      getApplication().invokeLater(Runnable {
        showOverChangesView(
          project, message("notification.content.authentication.failed", e.message), MessageType.ERROR,
          object : NamedRunnable(message("confirmation.title.clear.authentication.cache")) {
            override fun run() = clearAuthenticationCache(project, null, configuration.configurationDirectory)
          },
          object : NamedRunnable(message("action.title.select.configuration.directory")) {
            override fun run() = selectConfigurationDirectory(
              configuration.configurationDirectory, { configuration.setConfigurationDirParameters(false, it) }, project, null)
          }
        )
      }, ModalityState.NON_MODAL, project.disposed)

    @JvmStatic
    fun clearAuthenticationCache(project: Project, component: Component?, configDirPath: String?) {
      if (configDirPath != null) {
        val result = if (component == null) {
          showYesNoDialog(project, message("confirmation.text.delete.stored.authentication.information"),
                          message("confirmation.title.clear.authentication.cache"), getWarningIcon())
        }
        else {
          showYesNoDialog(component, message("confirmation.text.delete.stored.authentication.information"),
                          message("confirmation.title.clear.authentication.cache"), getWarningIcon())
        }
        if (result == Messages.YES) {
          SvnConfiguration.RUNTIME_AUTH_CACHE.clear()
          clearAuthenticationDirectory(SvnConfiguration.getInstance(project))
        }
      }
    }

    @JvmStatic
    fun clearAuthenticationDirectory(configuration: SvnConfiguration) {
      val authDir = Paths.get(configuration.configurationDirectory, "auth")
      if (authDir.exists()) {
        val process: () -> Unit = {
          val ind = ProgressManager.getInstance().progressIndicator
          ind?.isIndeterminate = true
          ind?.text = message("progress.text.clearing.stored.credentials", authDir)

          authDir.directoryStreamIfExists({ it.fileName.toString() in AUTH_KINDS }) {
            for (dir in it) {
              ind?.text = message("progress.text.deleting", dir)
              dir.delete()
            }
          }
        }
        if (getApplication().isUnitTestMode || !getApplication().isDispatchThread) {
          process()
        }
        else {
          ProgressManager.getInstance().runProcessWithProgressSynchronously(
            process, message("progress.title.clear.authentication.cache"), false, configuration.project)
        }
      }
    }
  }
}
