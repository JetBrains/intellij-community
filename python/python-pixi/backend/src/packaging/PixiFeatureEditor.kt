// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.packaging

import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pixi.backend.workspace.parsePixiToml
import com.jetbrains.python.errorProcessing.PyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.toml.lang.TomlLanguage
import org.toml.lang.psi.TomlPsiFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists

internal suspend fun preservePixiFeature(project: Project, ownerDir: Path, feature: String): PyResult<Unit> {
  val virtualFile = withContext(Dispatchers.IO) {
    val workspace = findPixiWorkspace(ownerDir) ?: return@withContext null
    val manifest = workspace.root.resolve("pixi.toml").takeIf { it.exists() } ?: workspace.root.resolve("pyproject.toml")
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(manifest)?.also { it.refresh(false, false) }
  } ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.workspace.missing"))
  writeCommandAction(project, PyPixiBackendBundle.message("python.pixi.packaging.feature.preserve")) {
    val file = PsiManager.getInstance(project).findFile(virtualFile)?.takeIf { it.language == TomlLanguage }
               ?: return@writeCommandAction PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
    val parsed = parsePixiToml(file.text)
                 ?: return@writeCommandAction PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.invalid"))
    val pixi = parsed.getTable("tool.pixi") ?: parsed
    if (pixi.getTable("feature")?.getTable(listOf(feature)) == null) {
      val prefix = if (virtualFile.name == "pyproject.toml") "tool.pixi." else ""
      val factory = TomlPsiFactory(project)
      file.add(factory.createNewline())
      file.add(factory.createTable("${prefix}feature.${Json.encodeToString(feature)}"))
      file.add(factory.createNewline())
      val document = PsiDocumentManager.getInstance(project).getDocument(file)
      if (document != null) {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        FileDocumentManager.getInstance().saveDocument(document)
      }
    }
    PyResult.success(Unit)
  }.getOr { return it }
  return withContext(Dispatchers.IO) {
    try {
      ManagingFS.getInstance().flushPendingUpdates(virtualFile)
      PyResult.success(Unit)
    }
    catch (e: IOException) {
      PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.packaging.manifest.save.failed", e.localizedMessage))
    }
  }
}
