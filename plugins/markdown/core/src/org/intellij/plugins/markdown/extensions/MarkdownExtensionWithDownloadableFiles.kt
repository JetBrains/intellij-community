package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.settings.MarkdownSettingsUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Experimental
interface MarkdownExtensionWithDownloadableFiles: MarkdownExtensionWithExternalFiles {
  class FileEntry(val filePath: String, val link: Supplier<String?>) {
    constructor(filePath: String, link: String? = null): this(filePath, { link })
  }

  val filesToDownload: Iterable<FileEntry>

  fun downloadFiles(project: Project? = null): Boolean {
    return MarkdownSettingsUtil.downloadExtensionFiles(this, project)
  }
}
