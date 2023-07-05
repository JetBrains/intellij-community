package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.TextData
import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathSearcher
import org.assertj.swing.core.Robot
import java.awt.Component

@Suppress("unused")
@Service(Service.Level.APP)
internal class RobotService {
  @Suppress("MemberVisibilityCanBePrivate")
  val robot: SmoothRobot = SmoothRobot()

  private val xpathSearcher: XpathSearcher = XpathSearcher(TextToKeyCache)

  fun findAll(xpath: String): List<Component> {
    return xpathSearcher.findComponents(xpath, null)
  }

  fun findAll(xpath: String, component: Component): List<Component> {
    return xpathSearcher.findComponents(xpath, component)
  }

  fun findAllText(component: Component): List<TextData> {
    return TextParser.parseComponent(component, TextToKeyCache)
  }
}