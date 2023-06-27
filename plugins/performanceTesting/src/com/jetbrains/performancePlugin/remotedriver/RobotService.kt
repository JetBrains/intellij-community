package com.jetbrains.performancePlugin.remotedriver

import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.server.TextData
import com.jetbrains.performancePlugin.remotedriver.dataextractor.server.TextParser
import com.jetbrains.performancePlugin.remotedriver.dataextractor.server.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathSearcher
import org.assertj.swing.core.Robot
import java.awt.Component

@Service(Service.Level.APP)
internal class RobotService {
  private val robot = SmoothRobot()
  private val xpathSearcher = XpathSearcher(TextToKeyCache)

  fun find(xpath: String): ComponentFixture {
    return ComponentFixture(robot, xpathSearcher, xpathSearcher.findComponent(xpath, null))
  }

  fun findAll(xpath: String): List<ComponentFixture> {
    return xpathSearcher.findComponents(xpath, null).map {
      ComponentFixture(robot, xpathSearcher, it)
    }
  }

  val remoteRobot: Robot
    get() = robot
}

internal class ComponentFixture(val robot: Robot, private val xpathSearcher: XpathSearcher, val component: Component) {
  fun click() {
    robot.click(component)
  }

  fun find(xpath: String): ComponentFixture {
    return ComponentFixture(robot, xpathSearcher, xpathSearcher.findComponent(xpath, component))
  }

  fun findAll(xpath: String): List<ComponentFixture> {
    return xpathSearcher.findComponents(xpath, component).map {
      ComponentFixture(robot, xpathSearcher, it)
    }
  }

  val x: Int = component.x
  val y: Int = component.y
  val width: Int = component.width
  val height: Int = component.height
  fun findAllText(): List<TextData> {
    return TextParser.parseComponent(component, TextToKeyCache)
  }
}
