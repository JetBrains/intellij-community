// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.fileActions.export

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.fileActions.MarkdownFileActionFormat
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.File
import javax.swing.JComponent

@ApiStatus.Experimental
interface MarkdownExportProvider {
  val formatDescription: MarkdownFileActionFormat

  fun exportFile(project: Project, mdFile: VirtualFile, outputFile: String)

  fun validate(project: Project, file: VirtualFile): @Nls String?

  fun createSettingsComponent(project: Project, suggestedTargetFile: File): JComponent? = null

  companion object {
    private val EP_NAME: ExtensionPointName<MarkdownExportProvider> =
      ExtensionPointName.create("org.intellij.markdown.markdownExportProvider")

    val allProviders: List<MarkdownExportProvider> = EP_NAME.extensionList
  }
}
