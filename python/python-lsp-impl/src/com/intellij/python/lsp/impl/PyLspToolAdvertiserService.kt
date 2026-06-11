// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.impl

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.pyright.PyrightPyTool
import com.intellij.python.pyright.PyrightUtil
import com.intellij.python.pytools.PyTool
import com.intellij.python.pytools.PyToolsState
import com.intellij.python.pytools.ui.configuration.PyExternalToolsConfigurable
import com.intellij.python.ruff.RuffPyTool
import com.intellij.python.ruff.RuffUtil
import com.intellij.python.ty.TyPyTool
import com.intellij.python.ty.TyUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.findPythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import kotlin.time.Duration.Companion.seconds

private const val NOTIFICATION_GROUP_ID = "Python LSP Tools"
private const val DONT_ASK_PROPERTY_PREFIX = "python.lsp.tool.dont.ask."

private data class LspToolInfo(
  val tool: PyTool,
  val icon: Icon,
  val aliasIcons: Map<PyPackageName, Icon> = emptyMap(),
)

private data class AdvertisedTool(
  val info: LspToolInfo,
  val displayName: @NlsSafe String,
  val icon: Icon,
)

private fun lspTools(): List<LspToolInfo> = listOf(
  LspToolInfo(
    tool = RuffPyTool.getInstance(),
    icon = RuffUtil.getDefaultRuffIcon(),
  ),
  //LspToolInfo(
  //  tool = PyreflyPyTool.getInstance(),
  //  icon = PyreflyUtil.getDefaultPyreflyIcon(),
  //),
  LspToolInfo(
    tool = PyrightPyTool.getInstance(),
    icon = PyrightUtil.getDefaultPyrightIcon(),
    aliasIcons = mapOf(PyPackageName.from("basedpyright") to PyrightUtil.getDefaultBasedPyrightIcon()),
  ),
  LspToolInfo(
    tool = TyPyTool.getInstance(),
    icon = TyUtil.getDefaultTyIcon(),
  ),
)

/**
 * Service that detects LSP tools installed in the user's virtual environment
 * and shows a balloon notification offering to enable them.
 *
 * Also listens for package installation events to suggest enabling tools when installed.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class PyLspToolAdvertiserService(private val project: Project, private val cs: CoroutineScope) {

  private val shownForTools = mutableSetOf<String>()

  init {
    // Subscribe to package management events to detect when LSP tools are installed
    project.messageBus.connect(cs).subscribe(
      PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC,
      object : PythonPackageManagementListener {
        override fun packagesChanged(sdk: Sdk) {
          thisLogger().debug("Packages changed for SDK '${sdk.name}', triggering LSP tool check")
          // Defer until no modal dialog is open. If the user installs a tool from within
          // the settings dialog (modal), the settings haven't been applied yet at this point,
          // so isEnabled() would return false and trigger a spurious notification. Scheduling
          // with ModalityState.nonModal() means the check runs only after the dialog is
          // closed and settings are applied, so isEnabled() reflects the user's choice.
          ApplicationManager.getApplication().invokeLater({
            cs.launch { checkAndAdvertise() }
          }, ModalityState.nonModal())
        }
      }
    )
  }

  /**
   * Checks for installed LSP tools and shows notifications for any that are found
   * but not yet enabled.
   */
 suspend fun checkAndAdvertise() {
    val installedPackages = getInstalledPackages()
    val pyToolsState = PyToolsState.getInstance(project)

    val toolsToAdvertise = lspTools().mapNotNull { info ->
      val tool = info.tool
      // Detect which alias is actually installed; a tool may have several (e.g. pyright / basedpyright).
      val installedAlias = tool.aliases.firstOrNull { it.name in installedPackages }
      val name = tool.presentableName
      val isEnabled = pyToolsState.isEnabled(tool)
      if (installedAlias != null && (isEnabled || isDontAskSet(tool))) {
        thisLogger().debug("LSP tool '$name': installed=true, enabled=$isEnabled, dontAsk=${isDontAskSet(tool)}")
      }
      // shownForTools (keyed on the stable presentable name) suppresses repeat notifications when
      // checkAndAdvertise re-runs on package-change events.
      if (installedAlias == null || isEnabled || isDontAskSet(tool) || name in shownForTools) {
        return@mapNotNull null
      }
      // Show the package the user installed: an installed alias keeps its own name, while the
      // canonical package falls back to the tool's presentable name (proper casing, e.g. "Pyright").
      val displayName = if (installedAlias == tool.packageName) name else installedAlias.name
      val icon = info.aliasIcons[installedAlias] ?: info.icon
      AdvertisedTool(info, displayName, icon)
    }

    if (toolsToAdvertise.size > 1) {
      for (advertised in toolsToAdvertise) {
        thisLogger().info("LSP tool '${advertised.info.tool.presentableName}' installed but not enabled")
        shownForTools.add(advertised.info.tool.presentableName)
      }
      showMultipleToolsNotification(toolsToAdvertise)
    }
    else if (toolsToAdvertise.size == 1) {
      val advertised = toolsToAdvertise.single()
      thisLogger().info("LSP tool '${advertised.info.tool.presentableName}' installed but not enabled, showing notification")
      shownForTools.add(advertised.info.tool.presentableName)
      showNotification(advertised)
    }
  }

  private suspend fun getInstalledPackages(): Set<String> = project.modules
    .mapNotNull { it.findPythonSdk() }
    .distinct()
    .also { sdks -> thisLogger().debug("Scanning ${sdks.size} SDK(s) for installed packages") }
    .flatMapTo(mutableSetOf()) { sdk ->
      PythonPackageManager
        .forSdk(project, sdk)
        .listInstalledPackages()
        .map { it.name.lowercase() }
    }

  private fun showMultipleToolsNotification(tools: List<AdvertisedTool>) {
    val toolNames = tools.dropLast(1).joinToString { it.displayName } +
                    " and " + tools.last().displayName
    thisLogger().info("Notifying user about multiple LSP tools: $toolNames")
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        PyBundle.message("lsp.tool.advertiser.multiple.title"),
        PyBundle.message("lsp.tool.advertiser.multiple.message", toolNames),
        NotificationType.INFORMATION
      )
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("lsp.tool.advertiser.settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
      })
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("lsp.tool.advertiser.ignore")) {
        for (tool in tools) {
          setDontAsk(tool.info.tool)
        }
      })

    notification.notify(project)
  }

  private fun showNotification(advertised: AdvertisedTool) {
    val info = advertised.info
    val name = advertised.displayName
    thisLogger().info("Notifying user about LSP tool: $name")
    val notification = NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        PyBundle.message("lsp.tool.advertiser.title", name),
        PyBundle.message("lsp.tool.advertiser.message", name),
        NotificationType.INFORMATION
      )
      .setIcon(advertised.icon)
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("lsp.tool.advertiser.yes")) {
        // Mirror the External Tools page's "Enable" toggle: flip the framework-level state and
        // run the tool's lifecycle hook (which starts the LSP server).
        PyToolsState.getInstance(project).setEnabled(info.tool, true)
        info.tool.onEnabledChanged(project, true)
      })
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("lsp.tool.advertiser.no")) {
        setDontAsk(info.tool)
      })
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("lsp.tool.advertiser.settings")) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PyExternalToolsConfigurable::class.java)
      })

    notification.notify(project)
  }

  private fun isDontAskSet(tool: PyTool): Boolean {
    return PropertiesComponent.getInstance(project).getBoolean(DONT_ASK_PROPERTY_PREFIX + tool.presentableName, false)
  }

  private fun setDontAsk(tool: PyTool) {
    thisLogger().debug("Setting don't-ask for LSP tool: ${tool.presentableName}")
    PropertiesComponent.getInstance(project).setValue(DONT_ASK_PROPERTY_PREFIX + tool.presentableName, true)
  }

  companion object {
    fun getInstance(project: Project): PyLspToolAdvertiserService =
      project.service<PyLspToolAdvertiserService>()
  }
}

class PyLspToolAdvertiserStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    thisLogger().debug("PyLspToolAdvertiserStartupActivity: waiting 15s before first check")
    delay(15.seconds)
    PyLspToolAdvertiserService.getInstance(project).checkAndAdvertise()
  }
}
