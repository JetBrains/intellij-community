// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.frontend.preview

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.ui.preview.MarkdownEditorWithPreview
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanelProvider
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows a banner on a Markdown editor that has no preview at all: no preview provider is available, and live preview is off.
 * The banner tells the user why the JCEF preview is missing.
 * The "Don't show again" link hides the banner for all files.
 */
@ApiStatus.Internal
class MarkdownPreviewUnavailableBannerProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val reason = getPreviewUnavailableReason() ?: return null
    return Function { fileEditor ->
      if (fileEditor is MarkdownEditorWithPreview) createPanel(project, fileEditor, reason) else null
    }
  }

  private fun createPanel(project: Project, fileEditor: FileEditor, reason: Reason): EditorNotificationPanel {
    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
    when (reason) {
      Reason.PLUGIN_DISABLED -> {
        panel.text = MarkdownBundle.message("markdown.preview.unavailable.banner.plugin.disabled")
        panel.createActionLabel(MarkdownBundle.message("markdown.preview.unavailable.banner.enable.plugin")) {
          PluginManagerConfigurable.showPluginConfigurable(project, listOf(JCEF_PLUGIN_ID))
        }
      }
      Reason.JCEF_DISABLED -> {
        panel.text = MarkdownBundle.message("markdown.preview.unavailable.banner.jcef.disabled")
        panel.createActionLabel(MarkdownBundle.message("markdown.preview.unavailable.banner.edit.vm.options"), EDIT_VM_OPTIONS_ACTION_ID)
      }
      Reason.JCEF_NOT_SUPPORTED -> {
        panel.text = MarkdownBundle.message("markdown.preview.unavailable.banner.jcef.not.supported")
      }
    }
    panel.createActionLabel(MarkdownBundle.message("markdown.preview.unavailable.banner.dont.show.again")) {
      PropertiesComponent.getInstance().setValue(DISMISSED_PROPERTY, true)
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
    return panel
  }

  private enum class Reason {
    PLUGIN_DISABLED,
    JCEF_DISABLED,
    JCEF_NOT_SUPPORTED,
  }

  private companion object {
    const val DISMISSED_PROPERTY = "markdown.preview.unavailable.banner.dismissed"
    const val EDIT_VM_OPTIONS_ACTION_ID = "EditCustomVmOptions"

    /** Turns live preview on. Live preview is a preview, so the banner does not show then. */
    const val LIVE_PREVIEW_REGISTRY_KEY = "markdown.live.preview.enabled"

    val JCEF_PLUGIN_ID: PluginId = PluginId.getId("com.intellij.modules.jcef")

    fun getPreviewUnavailableReason(): Reason? {
      if (PropertiesComponent.getInstance().getBoolean(DISMISSED_PROPERTY)) return null
      if (MarkdownHtmlPanelProvider.hasAvailableProviders()) return null
      if (Registry.`is`(LIVE_PREVIEW_REGISTRY_KEY, true)) return null
      return when {
        PluginManagerCore.isDisabled(JCEF_PLUGIN_ID) -> Reason.PLUGIN_DISABLED
        !Registry.`is`("ide.browser.jcef.enabled") -> Reason.JCEF_DISABLED
        else -> Reason.JCEF_NOT_SUPPORTED
      }
    }
  }
}
