package com.jetbrains.performancePlugin.remotedriver

import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextData
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
  val context: String = "root"

  private val xpathSearcher: XpathSearcher = XpathSearcher(TextToKeyCache)

  fun findAll(xpath: String): List<RemoteComponent> {
    return xpathSearcher.findComponents(xpath, null).map {
      RemoteComponent(robot, xpath, xpathSearcher, it)
    }
  }
}

@Suppress("MemberVisibilityCanBePrivate")
internal class RemoteComponent(val robot: Robot,
                               val context: String,
                               private val xpathSearcher: XpathSearcher,
                               val component: Component) {
  fun findAll(xpath: String): List<RemoteComponent> {
    return xpathSearcher.findComponents(xpath, component).map {
      RemoteComponent(robot, context + xpath, xpathSearcher, it)
    }
  }

  fun findAllText(): List<TextData> {
    return TextParser.parseComponent(component, TextToKeyCache)
  }
}
