// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.jetbrains.performancePlugin.remotedriver.xpath

import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import org.assertj.swing.edt.GuiActionRunner
import org.assertj.swing.edt.GuiTask
import org.assertj.swing.hierarchy.ComponentHierarchy
import org.assertj.swing.hierarchy.ExistingHierarchy
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Component
import java.awt.Container
import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.util.*
import java.util.function.Supplier
import javax.swing.JComponent
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.absoluteValue

class XpathDataModelCreator(val onlyVisibleComponents: Boolean = true) {
  val elementProcessors = XpathDataModelExtension.EP_NAME.extensionList.toMutableList()

  private fun addComponent(
    doc: Document,
    parentElement: Element,
    hierarchy: ComponentHierarchy,
    component: Component,
    targetComponent: Component? = null
  ) {
    val element = createElement(doc, component, targetComponent)
    parentElement.appendChild(element)

    val allChildren = hierarchy.childrenOf(component)
    val filteredChildren = allChildren.filter(componentFilter)
    val exceptionChildren = if (System.getProperty("os.name").startsWith("mac", true)) {
      allChildren.filter {
        it.javaClass.name.contains("DialogWrapperPeerImpl")
        && filteredChildren.contains(it).not()
      }.flatMap { hierarchy.childrenOf(it) }.filter(componentFilter)
    }
    else {
      emptyList()
    }
    mutableSetOf<Component>().apply {
      addAll(filteredChildren)
      addAll(exceptionChildren)
    }.sortedWith(ComponentOrderComparator).forEach {
      addComponent(doc, element, hierarchy, it, targetComponent)
    }
    elementProcessors.forEach { it.postProcessElement(doc, component, element, parentElement) }
  }

  fun createElement(doc: Document, component: Component, targetComponent: Component? = null): Element {

    val element = doc.createElement("div")

    component.fillElement(doc, element, targetComponent)

    element.setUserData("component", component, null)
    return element
  }

  private fun <C : Component> C.fillElement(doc: Document, element: Element, targetComponent: Component? = null) {
    val jClass = if (javaClass.isAnonymousClass) {
      javaClass.superclass.name
    }
    else {
      javaClass.name
    }.substringAfterLast(".").substringAfterLast("$")

    element.setAttribute("class", jClass)
    element.setAttribute("javaclass", javaClass.name)
    element.setAttribute("classhierarchy", getClassHierarchy(javaClass))

    if (this == targetComponent) {
      element.setAttribute("robot_target_element", "true")
    }

    val elementText = StringBuilder().apply { append(jClass).append(". ") }

    val allFields = mutableSetOf<Field>()
    allFields.addAll(Component::class.java.declaredFields.toList())
    var clazz: Class<*> = this::class.java
    while (true) {
      allFields.addAll(clazz.declaredFields.toList())
      if (clazz == Component::class.java) {
        break
      }
      clazz = clazz.superclass
    }
    allFields
      .filter(fieldsFilter)
      .forEach { field ->
        try {
          field.isAccessible = true
          val attributeName = field.name.replace("$", "_").lowercase()
          val value = field.get(this)?.toString()?.let {
            if (it.contains(".svg")) {
              return@let it
                           .substringBeforeLast(".svg")
                           .substringAfterLast("/")
                           .substringAfterLast("\\") + ".svg"
            }
            else {
              return@let it
            }
          }?.apply {
            element.setAttribute(attributeName, this)
          }
          value?.removeInvalidXmlCharacters()?.apply {
            if (textFieldsFilter(attributeName, value)) {
              elementText.append("$attributeName: '$this'. ")
              TextToKeyCache.findKey(value)?.apply {
                elementText.append("${attributeName}.key: '$this'. ")
                element.setAttribute(attributeName + ".key", this)
              }
            }
          }
        }
        catch (ignore: InaccessibleObjectException) {

        }
        finally {
          field.isAccessible = false
        }
      }

    val accessibleName = try {
      accessibleContext?.accessibleName
    }
    catch (e: NullPointerException) {
      null
    }
    if (accessibleName != null) {
      element.setAttribute("accessiblename", accessibleName)
      TextToKeyCache.findKey(accessibleName)?.apply { element.setAttribute("accessiblename.key", this) }
    }

    val tooltipText = getTooltipText(this)
    if (tooltipText != null) {
      element.setAttribute("tooltiptext", tooltipText)
      TextToKeyCache.findKey(tooltipText)?.apply { element.setAttribute("tooltiptext.key", this) }
    }

    if (isShowing) {
      val visibleChildren = if (this is Container) {
        this.components.filter { it.isVisible }.size
      }
      else {
        0
      }
      if ((visibleChildren == 0 || jClass == "JBList")
          && bounds.width > 0 && bounds.height > 0
      ) {
        try {
          val foundText = TextParser.parseComponent(this)
          val text = foundText.joinToString(" || ") { it.text }
          element.setAttribute("visible_text", text)
          if (text.trim().isNotEmpty()) {
            elementText.append("visible_text: '$text'. ")
          }
          val keys = foundText.mapNotNull { it.bundleKey }
          element.setAttribute("visible_text_keys", keys.joinToString(" || ") { it })
        }
        catch (e: Throwable) {
          e.printStackTrace()
        }
      }
    }
    if (isShowing) {
      element.addIcon(
        "show", 17,
        "show(${locationOnScreen.x}, ${locationOnScreen.y}, $width, $height)"
      )
      element.appendChild(doc.createTextNode(" "))
    }
    element.appendChild(doc.createTextNode(elementText.toString()))
    element.addIcon(
      "locator", 17,
      "new XpathEditor(parentElement).generatePath()"
    )

    val xpathEditor = doc.createElement("xpathEditor")

    xpathEditor.appendChild(doc.createElement("input").apply {
      setAttribute("type", "text")
      setAttribute("size", "100")
      setAttribute("oninput", "new XpathEditor(parentElement.parentElement).checkXpath()")
    })
    xpathEditor.appendChild(doc.createElement("label"))
    xpathEditor.setAttribute("class", "hidden")
    element.appendChild(xpathEditor)
  }

  private val textFieldsFilter: (String, String) -> Boolean = { name, value ->
    value.trim().isNotEmpty() && value.length <= 35 && (
      name.contains("title")
      || name.contains("name")
      || name.endsWith("text")
      || value.contains(".svg")
      || name.endsWith("action")
                                                       )
    && name != "hideactiontext"
  }

  private val fieldsFilter: (Field) -> Boolean = {
    (it.name.lowercase(Locale.getDefault()).contains("text")
     || it.name.lowercase(Locale.getDefault()) == "id"
     || it.name.lowercase(Locale.getDefault()).endsWith("name")
     || it.name.lowercase(Locale.getDefault()).contains("action")
     || it.name.lowercase(Locale.getDefault()).contains("icon")
     || it.name.lowercase(Locale.getDefault()).contains("title")
     || it.name.lowercase(Locale.getDefault()) == "visible"
     || it.name.lowercase(Locale.getDefault()) == "enabled"
     || it.name.lowercase(Locale.getDefault()).contains("caption")
    )
    && it.name.lowercase(Locale.getDefault()).contains("listener").not()
    && it.name.lowercase(Locale.getDefault()).endsWith("context").not()

  }

  private val componentFilter: (Component) -> Boolean = {
    (it.isVisible && it.isShowing || !onlyVisibleComponents)
    && it::class.java.simpleName != "Corner"
    && ((it.bounds.width > 0 && it.bounds.height > 0)
        || !onlyVisibleComponents
        || it::class.java.simpleName in listOf("IdeMenuBar", "ActionMenu"))
  }

  private val componentsWithBlockingModalTextSupplier = listOf(
    "com.intellij.openapi.wm.impl.status.TextPanel\$WithIconAndArrows",
    "com.intellij.openapi.wm.impl.status.TextPanel"
  )

  private fun getTooltipText(component: Component): String? {
    if (component is JComponent) {
      if (component::class.java.name in componentsWithBlockingModalTextSupplier) return null
      return try {
        component.toolTipText
        ?: component.getClientProperty("JComponent.helpTooltip")?.let {
          it.javaClass.getDeclaredField("title").apply {
            isAccessible = true
          }.get(it)?.let { title ->
            when (title) {
              is String -> title
              is Supplier<*> -> title.get()?.toString()
              else -> title.toString()
            }
          }
        }
      }
      catch (e: Throwable) {
        null
      }
    }
    else {
      return null
    }
  }

  private fun getClassHierarchy(clazz: Class<*>) = buildString {
    var cl = clazz.superclass
    while (cl != null) {
      if (length > 0) append(" -> ")
      append(cl.name)
      if (JComponent::class.java.name == cl.name) break
      cl = cl.superclass
    }
  }

  private object ComponentOrderComparator : Comparator<Component> {
    override fun compare(c1: Component, c2: Component): Int {
      if (!c1.isShowing) return -1
      if (!c2.isShowing) return 1
      val yDiff = c1.locationOnScreen.y - c2.locationOnScreen.y
      if (yDiff.absoluteValue > 10) {
        return yDiff
      }
      return c1.locationOnScreen.x - c2.locationOnScreen.x
    }

  }


  fun create(rootComponent: Component?, includeRoot: Boolean = false, targetComponent: Component? = null): Document {

    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

    GuiActionRunner.execute(object : GuiTask() {
      override fun executeInEDT() {
        val hierarchy = ExistingHierarchy()
        val rootElement = doc.createElement("div")
        doc.appendChild(rootElement)
        val containers = if (rootComponent != null) {
          if (includeRoot) listOf(rootComponent)
          else hierarchy.childrenOf(rootComponent).takeIf { it.isNotEmpty() } ?: listOf(rootComponent)
        }
        else {
          hierarchy.roots()
        }
        containers.filter { it.isShowing || !onlyVisibleComponents || it.javaClass.name.endsWith("SharedOwnerFrame") }.forEach {
          addComponent(doc, rootElement, hierarchy, it, targetComponent)
        }
      }
    })
    return doc
  }
}

fun Element.addIcon(iconName: String, size: Int, onClickFunction: String) {
  val doc = this.ownerDocument
  val icon = doc.createElement("img")
  icon.setAttribute("src", "static/img/$iconName.png")
  icon.setAttribute("width", size.toString())
  icon.setAttribute("height", size.toString())

  icon.setAttribute(
    "onclick",
    onClickFunction
  )
  appendChild(icon)
}

private fun String.removeInvalidXmlCharacters(): String {
  // Replace any characters not allowed in XML with an empty string
  return replace(Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]"), "")
}