// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.poetry.VersionType.Companion.getVersionType
import com.jetbrains.python.psi.LanguageLevel
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
@Service
class PoetryPyProjectTomlPythonVersionsService : Disposable {
  private val modulePythonVersions: ConcurrentMap<VirtualFile, PoetryPythonVersion> = ConcurrentHashMap()

  companion object {
    val instance: PoetryPyProjectTomlPythonVersionsService
      get() = service()
  }

  fun setVersion(moduleFile: VirtualFile, stringVersion: String) {
    modulePythonVersions[moduleFile] = PoetryPythonVersion(stringVersion)
  }

  fun getVersionString(moduleFile: VirtualFile): String = getVersion(moduleFile).stringVersion

  fun validateSdkVersions(moduleFile: VirtualFile, sdks: List<Sdk>): List<Sdk> =
    sdks.filter { getVersion(moduleFile).isValid(it.versionString) }

  fun validateInterpretersVersions(moduleFile: VirtualFile, interpreters: Flow<List<PythonSelectableInterpreter>>): Flow<List<PythonSelectableInterpreter>> {
    val version = getVersion(moduleFile)
    return interpreters.map { list -> list.filter { version.isValid(it.languageLevel) } }
  }

  private fun getVersion(moduleFile: VirtualFile): PoetryPythonVersion =
    modulePythonVersions[moduleFile] ?: PoetryPythonVersion("")

  override fun dispose() {}
}

@Internal
enum class VersionType {
  LESS,
  LESS_OR_EQUAL,
  EQUAL,
  MORE_OR_EQUAL,
  MORE;

  companion object {
    fun String.getVersionType(): VersionType? =
      when (this) {
        "<" -> LESS
        "<=" -> LESS_OR_EQUAL
        "=", "" -> EQUAL
        "^", ">=" -> MORE_OR_EQUAL
        ">" -> MORE
        else -> null
      }
  }
}


private fun getDefaultValueByType(type: VersionType): Int? =
  when (type) {
    VersionType.LESS, VersionType.MORE_OR_EQUAL -> 0
    VersionType.LESS_OR_EQUAL, VersionType.MORE -> 20
    VersionType.EQUAL -> null
  }

private fun Triple<Int, Int?, Int?>.compare(versionTriple: Pair<VersionType, Triple<Int, Int?, Int?>>): Int {
  val type = versionTriple.first
  val version = versionTriple.second

  return this.first.compareTo(version.first).takeIf { it != 0 }
         ?: this.second?.compareTo(version.second ?: (getDefaultValueByType(type) ?: this.second ?: 0)).takeIf { it != 0 }
         ?: this.third?.compareTo(version.third ?: (getDefaultValueByType(type) ?: this.third ?: 0)).takeIf { it != 0 }
         ?: 0
}

@Internal
data class PoetryPythonVersion(val stringVersion: String) {
  val descriptions: List<Pair<VersionType, Triple<Int, Int?, Int?>>>

  init {
    descriptions = parseVersion(stringVersion)
  }

  private fun parseVersion(versionString: String): List<Pair<VersionType, Triple<Int, Int?, Int?>>> {
    if (versionString.isEmpty()) return emptyList()
    val versionParts = versionString.split(",")
    val result = mutableListOf<Pair<VersionType, Triple<Int, Int?, Int?>>>()

    for (part in versionParts) {
      val firstDigit = part.indexOfFirst { it.isDigit() }
      if (firstDigit == -1) continue
      val type = part.substring(0, firstDigit).trim().getVersionType() ?: continue
      val version = part.substring(firstDigit).trim()
      val versionTriple = PoetryVersionValue.create(version).getOrNull()?.version
      versionTriple?.let { result.add(Pair(type, versionTriple)) }
    }
    return result
  }

  fun isValid(versionString: String?): Boolean {
    if (versionString.isNullOrBlank()) return false
    val baseInterpreterVersion = PoetryVersionValue.create(versionString).getOrNull()?.version ?: return false
    if (baseInterpreterVersion.first < 3 || baseInterpreterVersion.first == 3 && baseInterpreterVersion.second?.let { it < 6 } == true) return false
    for (description in descriptions) {
      val type = description.first
      val compareResult = baseInterpreterVersion.compare(description)
      when (type) {
        VersionType.LESS -> if (compareResult >= 0) return false
        VersionType.LESS_OR_EQUAL -> if (compareResult > 0) return false
        VersionType.EQUAL -> if (compareResult != 0) return false
        VersionType.MORE_OR_EQUAL -> if (compareResult < 0) return false
        VersionType.MORE -> if (compareResult <= 0) return false
      }
    }
    return true
  }

  fun isValid(languageLevel: LanguageLevel): Boolean {
    val languageLevelString = languageLevel.toString()
    return isValid(languageLevelString)
  }
}

@JvmInline
value class PoetryVersionValue private constructor(val version: Triple<Int, Int?, Int?>) {
  companion object {
    fun create(versionString: String): Result<PoetryVersionValue> {
      try {
        val integers = versionString.split(".").map { it.toInt() }
        return when (integers.size) {
          1 -> Result.success(PoetryVersionValue(Triple(integers[0], null, null)))
          2 -> Result.success(PoetryVersionValue(Triple(integers[0], integers[1], null)))
          3 -> Result.success(PoetryVersionValue(Triple(integers[0], integers[1], integers[2])))
          else -> Result.failure(NumberFormatException())
        }
      }
      catch (e: NumberFormatException) {
        return Result.failure(e)
      }
    }
  }
}