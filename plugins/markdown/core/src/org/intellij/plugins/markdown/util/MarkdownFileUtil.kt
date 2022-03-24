// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.io.File
import java.nio.file.Path

internal object MarkdownFileUtil {
  fun PsiElement.getContainingDirectoryFile(): File? {
    val file = containingFile?.containingDirectory?.virtualFile
    return file?.fileSystem?.getNioPath(file)?.toFile()
  }

  fun getDirectory(project: Project?, document: Document): File? {
    return PsiDocumentManager.getInstance(project ?: return null).getPsiFile(document)?.getContainingDirectoryFile()
  }

  // the preview can't resolve html images with relative path at the moment
  fun getPathForHtmlImage(path: String): String {
    return Path.of(path).toAbsolutePath().normalize().toString()
  }

  fun getPathForMarkdownImage(path: String, currentDirectory: File?): String {
    return FileUtil.toCanonicalPath(FileUtilRt.getRelativePath(currentDirectory, File(path)) ?: path)
  }
}
