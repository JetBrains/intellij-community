package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.RefDelegate
import com.intellij.driver.model.TextDataList
import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathSearcher
import com.jetbrains.performancePlugin.remotedriver.xpath.convertToHtml
import java.awt.Component
import java.nio.file.*

@Suppress("unused")
@Service(Service.Level.APP)
internal class RobotService {
  @Suppress("MemberVisibilityCanBePrivate")
  val robot: SmoothRobot = SmoothRobot()

  private val xpathSearcher: XpathSearcher = XpathSearcher(TextToKeyCache)

  fun findAll(xpath: String): List<RefDelegate<Component>> {
    return xpathSearcher.findComponents(xpath, null)
  }

  fun findAll(xpath: String, component: Component): List<RefDelegate<Component>> {
    return xpathSearcher.findComponents(xpath, component)
  }

  fun findAllText(component: Component): TextDataList {
    return TextParser.parseComponent(component, TextToKeyCache).let { TextDataList().apply { addAll(it) } }
  }

  fun saveHierarchy(folderPath: String, fileName: String = "ui.html") {
    val html = xpathSearcher.modelCreator.create(null).convertToHtml()
    Paths.get(folderPath).resolve(fileName).toFile().writeText(html)

    staticFiles.forEach { staticFilePath ->
      this::class.java.classLoader.getResource(staticFilePath)?.let { resource ->
        Paths.get(folderPath).resolve(staticFilePath).toFile().apply { resolve("..").mkdirs() }.writeBytes(resource.readBytes())
      }
    }
  }

  private val staticFiles = listOf(
    "static/scripts.js",
    "static/styles.css",
    "static/updateButton.js",
    "static/xpathEditor.js",
    "static/img/show.png",
    "static/img/locator.png",
  )
}