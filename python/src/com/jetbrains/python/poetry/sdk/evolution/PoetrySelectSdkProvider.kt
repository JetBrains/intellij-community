package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.readText
import com.intellij.python.community.impl.poetry.backend.PyPoetryBundle
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.sdk.backend.evolution.EvoSelectSdkProvider
import com.intellij.python.sdk.backend.evolution.evoActionLeaf
import com.intellij.python.sdk.backend.evolution.evoWarning
import com.intellij.python.sdk.backend.evolution.getPythonVersion
import com.intellij.python.sdk.backend.evolution.resolvePythonExecutable
import com.intellij.python.sdk.common.evolution.EvoLeafDto
import com.intellij.python.sdk.common.evolution.EvoLoadResultDto
import com.intellij.python.sdk.common.evolution.EvoSectionDto
import com.jetbrains.python.PyBundle
import com.jetbrains.python.getOrNull
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.runPoetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.Path
import kotlin.io.path.name

internal class PoetrySelectSdkProvider : EvoSelectSdkProvider {
  override val id: String get() = "Poetry"
  override val label: String get() = "Poetry"
  override val icon: Icon get() = PythonIcons.Python.Origami

  override suspend fun loadSections(module: Module): EvoLoadResultDto {
    getPoetryExecutable()
    ?: return evoWarning(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"))

    val pyProjectTomlFile = withContext(Dispatchers.IO) {
      PyProjectToml.findPyProjectTomlFile(module)?.virtualFile
    } ?: return evoWarning(PyPoetryBundle.message("evolution.pyproject.toml.file.is.required.for.poetry"))

    val projectDir = pyProjectTomlFile.parent.toNioPath()
    val envList = runPoetry(projectDir, "env", "list", "--full-path").getOrNull()
                  ?: return evoWarning(PyPoetryBundle.message("evolution.poetry.env.list.failed"))

    val environments = envList.lineSequence()
      .map { line -> Path.of(line.removeSuffix("(Activated)").trim()) }
      .filter { it.name.isNotBlank() }
      .toList()
    val envByFolders = environments.groupBy { it.parent }.toMutableMap()

    val projectName = withContext(Dispatchers.IO) {
      PyProjectToml.parse(pyProjectTomlFile.readText())?.project?.name
    }
    val poetryVirtualenvsPath = runPoetry(projectDir, "config", "virtualenvs.path").getOrNull()?.let { Path(it.trim()) }
    val specials = poetryVirtualenvsPath?.let { envByFolders.remove(it) }

    val envSections = envByFolders.map { (basePath, envs) ->
      EvoSectionDto(
        label = basePath?.toString() ?: "undefined",
        leaves = envs.map { poetryLeaf(it.name, it.resolvePythonExecutable()?.getPythonVersion()) },
      )
    }

    val systemPythons = SystemPythonService().findSystemPythons()
      .groupBy { it.pythonInfo.languageLevel }.keys.sortedDescending()
    val prefix = specials?.firstOrNull()?.name?.substringBeforeLast("-") ?: "$projectName-sha256"
    val specialSection = EvoSectionDto(
      label = "$poetryVirtualenvsPath/$prefix",
      leaves = systemPythons.map { languageLevel ->
        val installed = specials?.firstOrNull { it.toString().endsWith(languageLevel.toPythonVersion()) }
          ?.let { it.resolvePythonExecutable()?.getPythonVersion() }
        poetryLeaf(languageLevel.toPythonVersion(), installed)
      },
    )

    return EvoLoadResultDto.Ok(envSections + specialSection)
  }
}

private fun poetryLeaf(title: String, version: String?): EvoLeafDto =
  evoActionLeaf(title = title, secondaryText = version, icon = AllIcons.Nodes.Favorite)
