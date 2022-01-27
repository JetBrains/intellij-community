// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.settings

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.project.processOpenedProjects
import org.intellij.plugins.markdown.extensions.CodeFenceGeneratingProvider

internal class MarkdownLafListener: LafManagerListener {
  override fun lookAndFeelChanged(source: LafManager) {
    CodeFenceGeneratingProvider.notifyLAFChanged()
    processOpenedProjects { project ->
      val settings = MarkdownSettings.getInstance(project)
      project.messageBus.syncPublisher(MarkdownSettings.ChangeListener.TOPIC).settingsChanged(settings)
    }
  }
}
