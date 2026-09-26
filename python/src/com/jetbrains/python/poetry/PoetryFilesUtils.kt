// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonSelectableInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.apache.tuweni.toml.Toml
import org.jetbrains.annotations.ApiStatus.Internal
import org.toml.lang.psi.TomlKeyValue
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 *  This source code is edited by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

private val LOGGER = Logger.getInstance("#com.jetbrains.python.sdk.poetry")

suspend fun getPyProjectTomlForPoetry(virtualFile: VirtualFile): VirtualFile? =
  withContext(Dispatchers.IO) {
    readAction {
      try {
        Toml.parse(virtualFile.inputStream).getTable("tool.poetry")?.let { virtualFile }
      }
      catch (e: IOException) {
        LOGGER.info(e)
        null
      }
    }
  }

/**
 * Finds the Python version string specified in the toml file.
 *
 * @param tomlFile The VirtualFile representing the toml file.
 * @param project The Project in which the toml file exists.
 * @return The Python version specified in the toml file, or null if not found.
 */
@Internal
suspend fun poetryFindPythonVersionFromToml(tomlFile: VirtualFile, project: Project): String? {
  val versionElement = readAction {
    val tomlPsiFile = tomlFile.findPsiFile(project) ?: return@readAction null
    (PsiTreeUtil.collectElements(tomlPsiFile, object : PsiElementFilter {
      override fun isAccepted(element: PsiElement): Boolean {
        if (element is TomlKeyValue) {
          if (element.key.text == "python") {
            return true
          }
        }
        return false
      }
    }).firstOrNull() as? TomlKeyValue)?.value?.text
  }

  return versionElement?.substring(1, versionElement.length - 1)
}

/**
 * Service class that stores Python versions specified in a pyproject.toml file.
 */
@Internal
@Service(Service.Level.PROJECT)
class PoetryPyProjectTomlPythonVersionsService : Disposable {
  private val modulePythonVersions: ConcurrentMap<VirtualFile, PyVersionSpecifiers> = ConcurrentHashMap()

  companion object {
    fun getInstance(project: Project): PoetryPyProjectTomlPythonVersionsService = project.service()
  }

  fun setVersion(moduleFile: VirtualFile, stringVersion: String) {
    modulePythonVersions[moduleFile] = PyVersionSpecifiers(stringVersion)
  }

  fun getVersionString(moduleFile: VirtualFile): String = getVersion(moduleFile).constraintSpec

  fun <P : PathHolder> validateInterpretersVersions(moduleFile: VirtualFile, interpreters: Flow<List<PythonSelectableInterpreter<P>>?>): Flow<List<PythonSelectableInterpreter<P>>?> {
    val version = getVersion(moduleFile)
    return interpreters.map { list -> list?.filter { version.isValid(it.pythonInfo.languageLevel) } }
  }

  private fun getVersion(moduleFile: VirtualFile): PyVersionSpecifiers =
    modulePythonVersions[moduleFile] ?: PyVersionSpecifiers.ANY_SUPPORTED

  override fun dispose() {}
}