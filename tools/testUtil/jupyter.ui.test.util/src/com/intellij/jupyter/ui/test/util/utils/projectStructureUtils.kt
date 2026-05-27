package com.intellij.jupyter.ui.test.util.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.popups
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.waitFor
import org.junit.jupiter.api.Assertions.assertEquals
import java.awt.Color
import java.awt.Point
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

internal fun Driver.getFilesInEditorTab(): List<UiComponent> {
    return ideFrame().xx("//div[@class='EditorTabLabel']").list()
}

fun Driver.filePresentInEditorTab(name: String): Boolean {
  return getFilesInEditorTab().any { it.hasText(name) }
}

fun Driver.openStructureView() = ideFrame {
  if (getStructureViewTree().present()) return@ideFrame

  step("Open Structure View") {
    x("//div[@accessiblename='Structure']").click()
  }
}

fun Driver.getStructureViewTree(): JTreeUiComponent {
  return ideFrame().tree("//div[@accessiblename='Structure View tree']")
}

internal fun Driver.openStructureViewOptions() = ideFrame {
  step("Open Structure View options") {
    getStructureViewTree().waitFound().moveMouse()
    actionButton { byAccessibleName("View Options") }.waitFound().click()
  }
}

fun Driver.structureViewShow(name: String) = ideFrame {
  openStructureViewOptions()
  step("Show fields in Structure View") {
    popup().waitFound().waitOneText(name).click()
  }
}

fun Driver.structureViewSortBy(option: String) = ideFrame {
  openStructureViewOptions()
  step("Sort Structure View by $option") {
    popup().waitFound().waitOneText(option).click()
  }
}

fun Driver.structureViewDisableHeadingsNumbers() = ideFrame {
  openStructureViewOptions()
  step("Disable Structure View headings") {
    popup().waitFound().waitOneText("Show Heading Numbers").moveMouse()
    should("Popup with 'None' option should appear") {
      popups().list().last().hasSubtext("None")
    }
    popups().list().last().waitOneText("None").click()
  }
}

fun checkStructureTreeAlignment(tree: JTreeUiComponent) {
  step("Check structure tree alignment") {
    val expandedPaths = tree.collectExpandedPaths()
    val groupsAlignment = mutableMapOf<List<String>, Int>()

    expandedPaths.forEach { nodePath ->
      val path = nodePath.path
      val row = nodePath.row

      val rowPoint = tree.fixture.getRowPoint(row)

      val parent = path.dropLast(1)

      if (groupsAlignment.containsKey(parent).not()) {
        groupsAlignment[parent] = rowPoint.x
      }
      else {
        assertEquals(rowPoint.x, groupsAlignment[parent]!!, "Elements within the same group should have the same horizontal offset")
      }
    }
  }
}

fun Driver.getStructureTreeSize() = getStructureViewTree().collectExpandedPaths().size

// Assumes that headers contain "Header"
fun checkStructureTreeHeadingsNumbers(tree: JTreeUiComponent, headingsEnabled: Boolean = true) {
  step("Check structure tree headings numbers") {
    val expandedPaths = tree.collectExpandedPaths()
    val headersChildren = mutableMapOf<String, Int>()

    expandedPaths.forEach { nodePath ->
      val path = nodePath.path
      val nodeText = path.last()

      if (nodeText.contains("Header").not()) {
        return@forEach
      }

      val currentIndex = nodeText.substringBeforeLast(".", "")
      if (headingsEnabled.not()) {
        assertEquals(currentIndex,  "", "Headers numbers should be disabled")
        return@forEach
      }

      val parentHeader = path.dropLast(1).findLast { it.contains("Header") } ?: ""
      if (headersChildren.containsKey(parentHeader).not()) {
        headersChildren[parentHeader] = 0
      }
      headersChildren[parentHeader] = headersChildren[parentHeader]!! + 1

      var parentIndex = parentHeader.substringBeforeLast(".")
      if (parentIndex.isNotEmpty()) parentIndex += "."
      val expectedIndex = parentIndex + headersChildren[parentHeader]

      assertEquals(expectedIndex, currentIndex, "Headers should have correct indexes")
    }
  }
}

fun Driver.checkFailedExecutionStatusPresent(text: String) = ideFrame {
  fun getDeltaE(c1: Color, c2: Color): Double {
    fun rgbToLab(rInt: Int, gInt: Int, bInt: Int): DoubleArray {
      var r = rInt / 255.0
      var g = gInt / 255.0
      var b = bInt / 255.0

      r = if (r > 0.04045) ((r + 0.055) / 1.055).pow(2.4) else r / 12.92
      g = if (g > 0.04045) ((g + 0.055) / 1.055).pow(2.4) else g / 12.92
      b = if (b > 0.04045) ((b + 0.055) / 1.055).pow(2.4) else b / 12.92

      val x = (r * 0.4124 + g * 0.3576 + b * 0.1805) * 100.0 / 95.047
      val y = (r * 0.2126 + g * 0.7152 + b * 0.0722) * 100.0 / 100.0
      val z = (r * 0.0193 + g * 0.1192 + b * 0.9505) * 100.0 / 108.883

      fun f(t: Double) = if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)

      return doubleArrayOf(
        116.0 * f(y) - 16.0,          // L (Lightness)
        500.0 * (f(x) - f(y)),       // a (Green-Red axis)
        200.0 * (f(y) - f(z))        // b (Blue-Yellow axis)
      )
    }

    val lab1 = rgbToLab(c1.red, c1.green, c1.blue)
    val lab2 = rgbToLab(c2.red, c2.green, c2.blue)

    return sqrt(
      (lab1[0] - lab2[0]).pow(2.0) +
      (lab1[1] - lab2[1]).pow(2.0) +
      (lab1[2] - lab2[2]).pow(2.0)
    )
  }

  val tree = getStructureViewTree()
  val expectedRed = Color(219, 92, 92)

  val statusText = tree.waitOneText(text)
  val basePoint = statusText.point

  waitFor(message = "Waiting for red color at '$text'", timeout = 15.seconds) {
    val area = -10..10
    area.asSequence().flatMap { i ->
      area.asSequence().map { j -> Point(basePoint.x + i, basePoint.y + j) }
    }.any { point ->
      val color = tree.getColor(point, moveMouse = false)
      getDeltaE(expectedRed, color) < 30.0
    }
  }
}