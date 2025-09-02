// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import com.jetbrains.performancePlugin.remotedriver.xpath.convertToHtml
import java.nio.file.*

@Suppress("unused")
@Service(Service.Level.APP)
internal class RobotService {
  @Suppress("MemberVisibilityCanBePrivate")
  val robot: SmoothRobot = SmoothRobot()

  fun saveHierarchy(folderPath: String, fileName: String = "ui.html") {
    val html = XpathDataModelCreator().create(null).convertToHtml()
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