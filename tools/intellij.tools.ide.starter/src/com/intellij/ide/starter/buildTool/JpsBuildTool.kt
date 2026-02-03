package com.intellij.ide.starter.buildTool

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.utils.XmlBuilder
import org.w3c.dom.Element
import java.nio.file.Path
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import kotlin.io.path.notExists
import kotlin.io.path.readText

class JpsBuildTool(testContext: IDETestContext) : BuildTool(BuildToolType.JPS, testContext) {
  private val ideaDir: Path
    get() = testContext.resolvedProjectHome.resolve(".idea")

  private val compilerXmlPath: Path
    get() = ideaDir.resolve("compiler.xml")

  private val workspaceXmlPath: Path
    get() = ideaDir.resolve("workspace.xml")

  fun setBuildProcessHeapSize(heapSizeMb: Int): JpsBuildTool {
    return patchXmlConfigFile(
      configFile = compilerXmlPath,
      componentName = "CompilerConfiguration",
      optionName = "BUILD_PROCESS_HEAP_SIZE",
      optionValue = "$heapSizeMb"
    )
  }

  fun enableParallelCompilation(): JpsBuildTool {
    return patchXmlConfigFile(
      configFile = workspaceXmlPath,
      componentName = "CompilerWorkspaceConfiguration",
      optionName = "PARALLEL_COMPILATION",
      optionValue = "true")
  }

  private fun patchXmlConfigFile(
    configFile: Path,
    componentName: String,
    optionName: String,
    optionValue: String,
    checkExpression: String = "option name=\"$optionName\" value=\"$optionValue\""
  ): JpsBuildTool {
    if (configFile.notExists()) return this
    val content = configFile.readText()
    if (content.contains(checkExpression)) return this

    val xmlDoc = XmlBuilder.parse(configFile)
    xmlDoc.documentElement.normalize()
    val xp: XPath = XPathFactory.newInstance().newXPath()

    if (content.contains(componentName)) {

      if (configFile.readText().contains(optionName)) {
        val node = xp.evaluate("//component/option[@name='$optionName']", xmlDoc, XPathConstants.NODE) as Element
        node.removeAttribute("value")
        node.setAttribute("value", optionValue)
      }
      else {
        val componentNode = xp.evaluate("//component[@name='$componentName']", xmlDoc, XPathConstants.NODE) as Element
        val optionElement = xmlDoc.createElement("option")
        optionElement.setAttribute("name", optionName)
        optionElement.setAttribute("value", optionValue)
        componentNode.appendChild(optionElement)
      }
    }
    else {
      val firstNode = xmlDoc.firstChild
      val componentElement = xmlDoc.createElement("component")
      componentElement.setAttribute("name", componentName)
      val optionElement = xmlDoc.createElement("option")
      optionElement.setAttribute("name", optionName)
      optionElement.setAttribute("value", optionValue)
      firstNode.appendChild(componentElement).appendChild(optionElement)
    }
    XmlBuilder.writeDocument(xmlDoc, configFile)

    return this
  }

  fun addBuildVmOption(key: String, value: String): JpsBuildTool {
    if (compilerXmlPath.notExists()) return this
    val content = compilerXmlPath.readText()
    val newOption = "-D$key=$value"
    if (content.contains(newOption)) return this

    val xmlDoc = XmlBuilder.parse(compilerXmlPath)
    xmlDoc.documentElement.normalize()
    val xp: XPath = XPathFactory.newInstance().newXPath()

    if (content.contains("CompilerConfiguration")) {

      if (compilerXmlPath.readText().contains("BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")) {

        val optionNode = xp.evaluate("//component/option[@name='BUILD_PROCESS_ADDITIONAL_VM_OPTIONS']", xmlDoc,
                                     XPathConstants.NODE) as Element
        val oldValue = optionNode.getAttribute("value")

        val newValue = "$oldValue $newOption"
        optionNode.removeAttribute("value")
        optionNode.setAttribute("value", newValue)
      }
      else {
        val componentNode = xp.evaluate("//component[@name='CompilerConfiguration']", xmlDoc, XPathConstants.NODE) as Element
        val optionElement = xmlDoc.createElement("option")
        optionElement.setAttribute("name", "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")
        optionElement.setAttribute("value", newOption)
        componentNode.appendChild(optionElement)
      }
    }
    else {
      val firstNode = xmlDoc.firstChild
      val componentElement = xmlDoc.createElement("component")
      componentElement.setAttribute("name", "CompilerConfiguration")
      val optionElement = xmlDoc.createElement("option")
      optionElement.setAttribute("name", "BUILD_PROCESS_ADDITIONAL_VM_OPTIONS")
      optionElement.setAttribute("value", newOption)
      firstNode.appendChild(componentElement).appendChild(optionElement)
    }
    XmlBuilder.writeDocument(xmlDoc, compilerXmlPath)

    return this
  }
}