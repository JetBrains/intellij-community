package org.jetbrains.plugins.textmate.actions

import com.google.gson.Gson
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.templates.github.DownloadUtil
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SortedListModel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.ZipUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.textmate.TextMateService
import org.jetbrains.plugins.textmate.configuration.BundleConfigBean
import org.jetbrains.plugins.textmate.configuration.TextMateSettings
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.File

class InstallVSCodePluginAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val model = SortedListModel<Plugin>(Comparator.comparing(Plugin::toString))
    val list = JBList<Plugin>(model)
    updateList(list, model)
    val scroll = ScrollPaneFactory.createScrollPane(list)
    scroll.border = JBUI.Borders.empty()
    val pane = ListWithFilter.wrap(list, scroll, Plugin::toString)

    val builder = JBPopupFactory
      .getInstance()
      .createComponentPopupBuilder(pane, list)
      .setMayBeParent(true)
      .setRequestFocus(true)
      .setFocusable(true)
      .setFocusOwners(arrayOf<Component>(list))
      .setLocateWithinScreenBounds(true)
      .setCancelOnOtherWindowOpen(true)
      .setMovable(true)
      .setResizable(true)
      .setTitle("Install VSCode plugin")
      .setCancelOnWindowDeactivation(false)
      .setCancelOnClickOutside(true)
      .setDimensionServiceKey(project, "install.vscode.plugin", true)
      .setMinSize(Dimension(JBUI.scale(350), JBUI.scale(300)))
      .setCancelButton(IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered))
    val popup = builder.createPopup()

    list.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (list.selectedValue == null) return
        if (e?.keyCode == KeyEvent.VK_ENTER) {
          e.consume()
          install(project, list.selectedValue, popup)
        }
      }
    })

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent?): Boolean {
        if (list.selectedValue == null) return true
        install(project, list.selectedValue, popup)
        return true
      }
    }.installOn(list)


    popup.showCenteredInCurrentWindow(project)
  }

  private fun updateList(list: JBList<Plugin>, model: SortedListModel<Plugin>) {
    list.setPaintBusy(true)
    model.clear()
    ApplicationManager.getApplication().executeOnPooledThread {
      val plugins = fetchPlugins()
      ApplicationManager.getApplication().invokeLater {
        model.addAll(plugins)
        list.setPaintBusy(false)
      }
    }
  }

  private fun fetchPlugins(): List<Plugin> {
    var plugins = emptyList<Plugin>()
    HttpRequests.request("http://vscode.blob.core.windows.net/gallery/index").connect { request ->
      plugins = loadPlugins(request.reader)
    }
    return plugins
  }

  private fun loadPlugins(reader: BufferedReader): List<Plugin> {
    val plugins = mutableListOf<Plugin>()
    val response = Gson().fromJson(reader, Object::class.java)
    val results = (response as? Map<*, *>)?.get("results")
    for (result in (results as? List<*> ?: emptyList<Any>())) {
      val extensions = (result as? Map<*, *>)?.get("extensions")
      for (extension in (extensions as? List<*> ?: emptyList<Any>())) {
        val extensionName = (extension as? Map<*, *>)?.get("extensionName")
        if (extensionName is String) {
          val publisher = (extension as? Map<*, *>)?.get("publisher")
          val publisherName = (publisher as? Map<*, *>)?.get("publisherName")
          if (publisherName is String) {
            val versions = (extension as? Map<*, *>)?.get("versions")
            val version = (versions as? List<*>)?.first()
            val url = (version as? Map<*, *>)?.get("assetUri")
            if (url is String) {
              plugins.add(Plugin(extensionName, publisherName, url))
            }
          }
        }
      }
    }
    return plugins
  }


  private fun install(project: Project, selectedValue: Plugin, popup: JBPopup) {
    popup.closeOk(null)
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing $selectedValue", false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = "Downloading $selectedValue..."
        val temp = File.createTempFile("vscode", "")
        DownloadUtil.downloadAtomically(indicator, "${selectedValue.url}/Microsoft.VisualStudio.Services.VSIXPackage", temp)

        indicator.text = "Unzipping $selectedValue..."
        val extensionDir = File(File(PathManager.getConfigPath(), "vscode"), selectedValue.name)
        ZipUtil.extract(temp, extensionDir, null)

        indicator.text = "Applying $selectedValue"
        val state = TextMateSettings.getInstance().state ?: TextMateSettings.TextMateSettingsState()
        state.bundles.add(BundleConfigBean(selectedValue.toString(), File(extensionDir, "extension").path, true))
        val textMateService = TextMateService.getInstance()
        textMateService.unregisterAllBundles(false)
        textMateService.registerEnabledBundles(true)
      }
    })
  }
}

data class Plugin(val name:String, val publisher:String, val url:String) {
  override fun toString(): String {
    return "$publisher/$name"
  }
}
