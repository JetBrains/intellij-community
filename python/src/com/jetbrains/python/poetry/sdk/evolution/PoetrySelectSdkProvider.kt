package com.jetbrains.python.poetry.sdk.evolution

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.vfs.readText
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.sdk.ui.evolution.sdk.EvoModuleSdk
import com.intellij.python.sdk.ui.evolution.sdk.resolvePythonExecutable
import com.intellij.python.sdk.ui.evolution.tool.pip.sdk.getPythonVersion
import com.intellij.python.sdk.ui.evolution.ui.EvoSelectSdkProvider
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLazyNodeElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeLeafElement
import com.intellij.python.sdk.ui.evolution.ui.components.EvoTreeSection
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonHomePath
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrNull
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.runPoetry
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name

private class PoetrySelectSdkProvider() : EvoSelectSdkProvider {
  override fun getTreeElement(evoModuleSdk: EvoModuleSdk): EvoTreeElement = EvoTreeLazyNodeElement("Poetry", PythonIcons.Python.Origami) {
    getPoetryExecutable() ?:PyResult.localizedError(PyBundle.message("python.sdk.poetry.execution.exception.no.poetry.message"))

    val pyProjectTomlFile = withContext(Dispatchers.IO) {
      PyProjectToml.findFile(evoModuleSdk.module)
    } ?: return@EvoTreeLazyNodeElement PyResult.localizedError(PyBundle.message("evolution.pyproject.toml.file.is.required.for.poetry"))

    val envList = runPoetry(pyProjectTomlFile.parent.toNioPath(), "env", "list", "--full-path").getOr { return@EvoTreeLazyNodeElement it }

    val environments = envList.lineSequence().map { line -> Path.of(line.removeSuffix("(Activated)").trim()) as PythonHomePath }
    val envByFolders = environments.groupBy { it.parent }.toMutableMap()


    val (projectName, requiresPython) = withContext(Dispatchers.IO) {
      val toml = PyProjectToml.parse(pyProjectTomlFile.readText()).getOrNull()
      (toml?.project?.name) to (toml?.project?.requiresPython)
    }
    val poetryVirtualenvsPath = runPoetry(pyProjectTomlFile.parent.toNioPath(), "config", "virtualenvs.path")
      .getOr { return@EvoTreeLazyNodeElement it }.let { Path(it.trim()) }

    val specials = envByFolders.remove(poetryVirtualenvsPath)

    val envSections = envByFolders.map { (basePath, sdks) ->
      val label = basePath.toString()
      val leafs = sdks.map { sdk ->
        val version = sdk.resolvePythonExecutable()?.getPythonVersion()
        SelectPoetryEnvAction(sdk.name, version)
      }.map { action -> EvoTreeLeafElement(action) }
      EvoTreeSection(ListSeparator(label), leafs)
    }

    val systemPythons = SystemPythonService().findSystemPythons().groupBy { it.pythonInfo.languageLevel }.keys.sortedDescending()
    val prefix = specials?.firstOrNull()?.name?.substringBeforeLast("-") ?: "$projectName-sha256"
    val specialSection = EvoTreeSection(
      label = ListSeparator("$poetryVirtualenvsPath/$prefix"),
      elements = systemPythons.map { languageLevel ->
        val installed = specials?.firstOrNull {
          it.toString().endsWith(languageLevel.toPythonVersion())
        }?.let { installed -> installed.resolvePythonExecutable()?.getPythonVersion() }
        EvoTreeLeafElement(SelectPoetryEnvAction(languageLevel.toPythonVersion(), installed))
      }
    )

    val sections = buildList {
      addAll(envSections)
      add(specialSection)
    }

    Result.success(sections)
  }
}

private class SelectPoetryEnvAction(
  title: String,
  installedVersion: Version?,

  ) : AnAction({ title }, { title },
               if (title.endsWith("(Activated)")) AllIcons.Nodes.Favorite else AllIcons.Nodes.NotFavoriteOnHover
) {
  init {
    templatePresentation.putClientProperty(ActionUtil.SECONDARY_TEXT, installedVersion?.let { it.toString() } ?: "n/a")
  }

  override fun actionPerformed(e: AnActionEvent) {}
}