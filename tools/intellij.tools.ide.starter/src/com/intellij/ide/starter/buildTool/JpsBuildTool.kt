package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.editXmlConfig
import com.intellij.ide.starter.utils.orCreateChildElement
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.util.xmlb.Constants
import org.w3c.dom.Element
import java.nio.file.Path
import kotlin.io.path.notExists

class JpsBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.JPS, testContext) {
  private val ideaDir: Path
    get() = testContext.resolvedProjectHome.resolve(".idea")

  private val compilerXmlPath: Path
    get() = ideaDir.resolve("compiler.xml")

  private val workspaceXmlPath: Path
    get() = ideaDir.resolve("workspace.xml")

  fun setBuildProcessHeapSize(heapSizeMb: Int): JpsBuildTool =
    setOption(compilerXmlPath, COMPILER_CONFIGURATION, "BUILD_PROCESS_HEAP_SIZE", "$heapSizeMb")

  fun enableParallelCompilation(): JpsBuildTool =
    setOption(workspaceXmlPath, "CompilerWorkspaceConfiguration", "PARALLEL_COMPILATION", "true")

  /** Adds `-D[key]=[value]` to the VM options of the build process. The option keeps its other values, once each. */
  fun addBuildVmOption(key: String, value: String): JpsBuildTool =
    editOption(compilerXmlPath, COMPILER_CONFIGURATION, "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS") { option ->
      val newOption = "-D$key=$value"
      val values = option.getAttribute(Constants.VALUE).split(" ").filter { it.isNotEmpty() }
      if (newOption !in values) {
        option.setAttribute(Constants.VALUE, (values + newOption).joinToString(" "))
      }
    }

  /** Puts [value] into the option [option] of the component [component], and drops the value it had. */
  private fun setOption(configFile: Path, component: String, option: String, value: String): JpsBuildTool =
    editOption(configFile, component, option) { it.setAttribute(Constants.VALUE, value) }

  /**
   * Applies [edit] to the option [optionName] of the component [componentName] of [configFile], and creates the
   * component and the option when [configFile] holds none. A file that does not exist stays absent.
   */
  private fun editOption(
    configFile: Path,
    componentName: String,
    optionName: String,
    edit: (Element) -> Unit,
  ): JpsBuildTool {
    if (configFile.notExists()) return this

    // the config of a project can grow large, so the result does not go to the log
    editXmlConfig(configFile, PROJECT_TAG, logContent = false) { xmlDoc ->
      val option = xmlDoc.documentElement
        .orCreateChildElement(ComponentStorageUtil.COMPONENT, componentName)
        .orCreateChildElement(Constants.OPTION, optionName)
      edit(option)
    }
    return this
  }

  private companion object {
    /** The root of a config file of the project level. */
    const val PROJECT_TAG = "project"
    const val COMPILER_CONFIGURATION = "CompilerConfiguration"
  }
}
