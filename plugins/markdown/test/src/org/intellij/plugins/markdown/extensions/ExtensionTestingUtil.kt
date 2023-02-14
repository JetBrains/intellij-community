// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import com.intellij.util.io.createFile
import org.intellij.plugins.markdown.settings.MarkdownExtensionsSettings
import org.intellij.plugins.markdown.settings.MarkdownSettingsUtil
import org.junit.jupiter.api.fail
import java.nio.file.Path

object ExtensionTestingUtil {
  fun mockPathManager(tempDirectory: Path, parentDisposable: Disposable) {
    val mocked = object: ExtensionsExternalFilesPathManager() {
      override val baseDirectory: Path
        get() = tempDirectory
    }
    ExtensionsExternalFilesPathManager.getInstance()
    ApplicationManager.getApplication().replaceService(ExtensionsExternalFilesPathManager::class.java, mocked, parentDisposable)
  }

  fun createFakeExternalFiles(extension: MarkdownExtensionWithExternalFiles) {
    val directory = ExtensionsExternalFilesPathManager.getInstance().obtainExternalFilesDirectoryPath(extension)
    for (file in extension.externalFiles) {
      val filePath = directory.resolve(file)
      filePath.createFile()
    }
    for (file in directory.toFile().walkTopDown()) {
      println(file)
    }
    println(extension.isAvailable)
  }

  fun downloadExtension(extension: MarkdownExtensionWithDownloadableFiles, project: Project?, retries: Int = 3) {
    val logger = logger<ExtensionTestingUtil>()
    var lastException: Throwable? = null
    repeat(retries) {
      try {
        if (MarkdownSettingsUtil.downloadExtension(extension, project, enableAfterDownload = false)) {
          return
        }
      } catch (exception: Throwable) {
        lastException = exception
        logger.debug("Failed to download extension external files on $it try! Extension: ${extension.id}", exception)
      }
    }
    fail("Failed to download extension external files after $retries attempts. Extension: ${extension.id}")
  }

  fun replaceExtensionsState(state: Map<String, Boolean>, parentDisposable: Disposable) {
    val settings = MarkdownExtensionsSettings.getInstance()
    val originalState = settings.extensionsEnabledState
    Disposer.register(parentDisposable) {
      settings.extensionsEnabledState = originalState
    }
    settings.extensionsEnabledState = state.toMutableMap()
  }
}
