// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiTask
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.JViewport
import javax.swing.SwingUtilities

object TextParser {
  private val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
  private val logger = thisLogger()

  fun parseComponent(component: Component): List<TextData> {
    val containerComponent = findContainerComponent(component) ?: return emptyList()
    val x = containerComponent.locationOnScreen.x - component.locationOnScreen.x
    val y = containerComponent.locationOnScreen.y - component.locationOnScreen.y
    val data = mutableListOf<TextData>()

    val g = DataExtractorGraphics2d(graphics, data, Point(x, y), TextToKeyCache)
    parseData(g, containerComponent)
    return (data + gatherExtraText(component)).distinct()
  }

  private fun gatherExtraText(component: Component): List<TextData> {
    return gatherExtraTextRec(component, component)
  }

  private fun gatherExtraTextRec(baseComponent: Component, currentComponent: Component): List<TextData> {
    val thisText = TextExtractorExtension.EP_NAME.extensionList.flatMap { it.extractTextFromComponent(currentComponent) }.apply {
      forEach {
        it.point = SwingUtilities.convertPoint(currentComponent, it.point, baseComponent)
      }
    }
    return thisText + ((currentComponent as? Container)?.components?.flatMap { gatherExtraTextRec(baseComponent, it) } ?: emptyList())
  }

  fun parseCellRenderer(component: Component): List<TextData> {
    val data = mutableListOf<TextData>()

    val g = CellReaderGraphics2d(graphics, data)
    parseData(g, component)
    return data
  }

  private fun parseData(g: ExtractorGraphics2d, component: Component) {
    GuiActionRunner.execute(object : GuiTask() {
      override fun executeInEDT() {
        try {
          component.paint(g)
        }
        catch (ce: ProcessCanceledException){
          throw ce
        }
        catch (e: Exception) {
          logger.info("Text parsing error. Can't do paint on ${component::class.java.simpleName}", e)
        }
      }
    })
  }

  private fun findContainerComponent(component: Component): Component? {
    if (component.parent is JViewport) {
      return component.parent
    }
    return component
  }
}