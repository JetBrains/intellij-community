package com.intellij.tools.devLauncher

import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Provides a way to locate a resource file under source roots by its relative path in an IntelliJ project.
 */
class ModuleResourceFileFinder(private val projectDir: Path) {
  private val moduleFiles: Map<String, Path>
  
  init {
    val modulesXmlFile = projectDir.resolve(".idea/modules.xml")
    require(modulesXmlFile.exists()) { ".idea/modules.xml not found in $projectDir"}
    modulesXmlFile.inputStream().buffered().use { input ->
      val moduleFilesMap = LinkedHashMap<String, Path>()
      val reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(input)
      while (reader.hasNext()) {
        val event = reader.next()
        if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "module") {
          val attributeName = reader.getAttributeLocalName(0)
          require(attributeName == "fileurl") { "Unexpected first attribute in 'module' tag: $attributeName"}
          val imlUrl = reader.getAttributeValue(0)
          val prefix = "file://${'$'}PROJECT_DIR${'$'}/"
          require(imlUrl.startsWith(prefix)) { "Unexpected format of URL: $imlUrl"}
          val imlPath = projectDir.resolve(imlUrl.removePrefix(prefix))
          val fileName = imlPath.name
          val suffix = ".iml"
          require(fileName.endsWith(suffix)) { "Unexpected file extension in file path $imlPath" }
          val moduleName = fileName.removeSuffix(suffix)
          moduleFilesMap[moduleName] = imlPath
        }
      }
      moduleFiles = moduleFilesMap
    }
  }
  
  fun findResourceFile(moduleName: String, relativePath: String): Path? {
    for ((prefix, rootPath) in loadRootsWithPrefixes(moduleName)) {
      val relativePathWithoutPrefix = when {
        prefix == null -> relativePath
        relativePath.startsWith("$prefix/") -> relativePath.removePrefix("$prefix/")
        else -> continue
      }
      val file = rootPath.resolve(relativePathWithoutPrefix)
      if (file.exists()) {
        return file 
      }
    }
    return null
  }

  private fun loadRootsWithPrefixes(moduleName: String): List<Pair<String?, Path>> {
    val imlPath = moduleFiles[moduleName] ?: error("Cannot find module '$moduleName' in project '$projectDir'")
    require(imlPath.exists()) { "Module file $imlPath doesn't exist" }
    val moduleDir = imlPath.parent
    val rootsWithPrefixes = ArrayList<Pair<String?, Path>>()
    imlPath.inputStream().buffered().use { input ->
      val reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(input)
      while (reader.hasNext()) {
        val event = reader.next()
        if (event == XMLStreamConstants.START_ELEMENT && reader.localName == "sourceFolder") {
          require(reader.attributeCount > 1) { "At least two attributes expected in 'sourceFolder' tag in $imlPath, but ${reader.attributeCount} found" }
          val attributeName = reader.getAttributeLocalName(0)
          require(attributeName == "url") { "Unexpected first attribute in 'sourceFolder' tag in $imlPath: $attributeName" }
          val rootUrl = reader.getAttributeValue(0)
          val prefix = "file://"
          require(rootUrl.startsWith(prefix)) { "Unexpected format of URL: $rootUrl" }
          val rootPath = Path(rootUrl.removePrefix(prefix).replace("${'$'}MODULE_DIR${'$'}", moduleDir.pathString))

          val typeAttributeName = reader.getAttributeLocalName(1)
          val typeAttributeValue = reader.getAttributeValue(1)
          val directoryPrefix = when {
            typeAttributeName == "isTestSource" && typeAttributeValue == "false" -> {
              if (reader.attributeCount > 2 && reader.getAttributeLocalName(2) == "packagePrefix") {
                reader.getAttributeValue(2).replace('.', '/').takeIf { it.isNotEmpty() }
              }
              else {
                null
              }
            }
            typeAttributeName == "type" && typeAttributeValue == "java-resource" -> {
              if (reader.attributeCount > 2 && reader.getAttributeLocalName(2) == "relativeOutputPath") {
                reader.getAttributeValue(2).removeSuffix("/").takeIf { it.isNotEmpty() }
              }
              else {
                null
              }
            }
            else -> {
              continue
            }
          }
          rootsWithPrefixes.add(directoryPrefix to rootPath)
        }
      }
    }
    return rootsWithPrefixes
  }
}