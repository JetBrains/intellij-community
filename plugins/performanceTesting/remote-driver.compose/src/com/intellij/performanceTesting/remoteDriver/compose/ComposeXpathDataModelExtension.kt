// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.remoteDriver.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.intellij.ui.scale.JBUIScale
import com.jetbrains.performancePlugin.jmxDriver.InvokerService
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelExtension
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.Collections

/**
 * A lightweight wrapper component that represents a Compose SemanticsNode.
 * This allows the remote driver to treat each Compose UI element as a distinct component
 * with its own bounds and location, even though they all belong to the same ComposePanel.
 *
 * Also we need to take into account scaling since compose returns physical pixels and AWT scaled.
 */
private class ComposeSemanticsNodeWrapper(
  private val node: SemanticsNode,
  private val panel: ComposePanel,
) : Component() {
  private val scale = JBUIScale.sysScale(panel)

  override fun getLocationOnScreen(): Point {
    val bounds = node.boundsInRoot
    return Point(
      (bounds.left / scale).toInt() + panel.locationOnScreen.x,
      (bounds.top / scale).toInt() + panel.locationOnScreen.y
    )
  }

  override fun getSize(): Dimension {
    return node.boundsInWindow.size.let { Dimension((it.width / scale).toInt(), (it.height / scale).toInt()) }
  }

  override fun getX(): Int {
    return (node.boundsInWindow.left / scale).toInt()
  }

  override fun getY(): Int {
    return (node.boundsInWindow.top / scale).toInt()
  }

  override fun getWidth(): Int {
    return (node.boundsInWindow.width / scale).toInt()
  }

  override fun getHeight(): Int {
    return (node.boundsInWindow.height / scale).toInt()
  }

  override fun isShowing(): Boolean {
    return panel.isShowing && (node.config.getOrNull(SemanticsProperties.Disabled) == null)
  }

  override fun isVisible(): Boolean {
    return panel.isVisible && (node.config.getOrNull(SemanticsProperties.Disabled) == null)
  }

  override fun toString(): String {
    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    val role = node.config.getOrNull(SemanticsProperties.Role)
    return "ComposeSemanticsNode(role=$role, text=$text)"
  }
}

class ComposeXpathDataModelExtension : XpathDataModelExtension {
  override val isRemDevExtension: Boolean = false

  @OptIn(ExperimentalComposeUiApi::class)
  override fun postProcessElement(doc: Document, component: Component, element: Element, parentElement: Element) {
    val composePanel = when (component) {
      is JewelComposePanelWrapper -> component.composePanel
      else -> return
    }

    // Clear the cache before processing to remove stale references from previous UI snapshots
    // The UI hierarchy is rebuilt on each request, so old wrappers are no longer needed
    wrapperCache.clear()

    try {
      val semanticsOwners = composePanel.semanticsOwners
      semanticsOwners.forEach { owner ->
        traverseComposeTree(doc, element, owner.rootSemanticsNode, composePanel)
      }
    }
    catch (_: Exception) {
      return
    }
  }

  private fun traverseComposeTree(doc: Document, parentElement: Element, node: SemanticsNode, composePanel: ComposePanel) {
    val composeElement = doc.createElement("div")
    composeElement.setAttribute("class", "ComposeNode")
    composeElement.setAttribute("javaclass", "androidx.compose.ui.semantics.SemanticsNode")

    val wrapperComponent = ComposeSemanticsNodeWrapper(node, composePanel)
    composeElement.setUserData("component", wrapperComponent, null)

    val ref = InvokerService.getInstance().putReference(wrapperComponent)
    wrapperCache.add(wrapperComponent)

    composeElement.setAttribute("refId", ref.id)
    composeElement.setAttribute("hashCode", ref.identityHashCode.toString())
    composeElement.setAttribute("asString", ref.asString())

    val rdTarget = InvokerService.getInstance().rdTarget
    composeElement.setAttribute("rdTarget", rdTarget.toString())

    val role = node.config.getOrNull(SemanticsProperties.Role)?.toString() ?: "ComposeNode"

    val textContent = buildString {
      node.config.getOrNull(SemanticsProperties.Text)?.forEach { text ->
        if (isNotEmpty()) append(" || ")
        append(text.text)
      }
    }

    val textNodeContent = StringBuilder("$role. ")

    if (textContent.isNotEmpty()) {
      composeElement.setAttribute("text", textContent)
      composeElement.setAttribute("visible_text", textContent)
      textNodeContent.append("text: '$textContent'. ")
    }

    node.config.getOrNull(SemanticsProperties.EditableText)?.let { editableText ->
      composeElement.setAttribute("editabletext", editableText.text)
      if (textContent.isEmpty()) {
        textNodeContent.append("editabletext: '${editableText.text}'. ")
      }
    }

    composeElement.appendChild(doc.createTextNode(textNodeContent.toString()))

    node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { descriptions ->
      val contentDesc = descriptions.joinToString(" ")
      composeElement.setAttribute("contentdescription", contentDesc)
    }

    node.config.getOrNull(SemanticsProperties.Role)?.let { role ->
      composeElement.setAttribute("role", role.toString())
    }

    node.config.getOrNull(SemanticsProperties.TestTag)?.let { testTag ->
      composeElement.setAttribute("testtag", testTag)
    }

    node.config.getOrNull(SemanticsProperties.ToggleableState)?.let { state ->
      composeElement.setAttribute("toggleablestate", state.toString())
    }

    node.config.getOrNull(SemanticsProperties.Selected)?.let { selected ->
      composeElement.setAttribute("selected", selected.toString())
    }

    node.config.getOrNull(SemanticsProperties.Focused)?.let { focused ->
      composeElement.setAttribute("focused", focused.toString())
    }

    node.config.getOrNull(SemanticsProperties.Disabled)?.let {
      composeElement.setAttribute("disabled", "true")
    }

    node.config.getOrNull(SemanticsProperties.Heading)?.let {
      composeElement.setAttribute("heading", "true")
    }

    node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.let { progressInfo ->
      composeElement.setAttribute("progresscurrent", progressInfo.current.toString())
      composeElement.setAttribute("progressrange", "${progressInfo.range.start}-${progressInfo.range.endInclusive}")
    }

    parentElement.appendChild(composeElement)

    node.children.forEach { child ->
      traverseComposeTree(doc, composeElement, child, composePanel)
    }
  }
}

// Cache to maintain strong references to wrapper components
// This prevents garbage collection of wrappers that are registered with InvokerService
private val wrapperCache = Collections.synchronizedList(mutableListOf<ComposeSemanticsNodeWrapper>())