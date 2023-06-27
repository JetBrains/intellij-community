package com.jetbrains.performancePlugin.remotedriver

import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.server.TextToKeyCache
import com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathSearcher
import org.assertj.swing.core.Robot
import java.awt.Component

@Service(Service.Level.APP)
class RobotService {
  private val robot = SmoothRobot()
  private val xpathSearcher = XpathSearcher(TextToKeyCache)

  fun find(xpath: String): ComponentFixture {
    return ComponentFixture(robot, xpathSearcher.findComponent(xpath, null))
  }

  fun find(xpath: String, inComponent: Component): ComponentFixture {
    return ComponentFixture(robot, xpathSearcher.findComponent(xpath, inComponent))
  }
}

class ComponentFixture(private val robot: Robot, val component: Component) {
  fun click() {
    robot.click(component)
  }
}