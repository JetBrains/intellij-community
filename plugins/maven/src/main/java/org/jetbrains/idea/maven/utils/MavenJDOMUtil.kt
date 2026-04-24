// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingRegistry
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jdom.IllegalNameException
import org.jdom.Namespace
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedList

object MavenJDOMUtil {
  @JvmStatic
  suspend fun read(file: VirtualFile, handler: ErrorHandler?): Element? {
    val charset = readAction {
      if (!file.isValid) {
        MavenLog.LOG.warn("MavenJDOMUtil.read: file ${file.name} is invalid")
        return@readAction null
      }
      file.charset
    } ?: return null

    val path = Path.of(file.path)
    return read(path, charset, handler)
  }

  @JvmStatic
  suspend fun read(file: Path, charset: Charset, handler: ErrorHandler?): Element? {
    val app = ApplicationManager.getApplication()
    if (app == null || app.isDisposed) {
      MavenLog.LOG.warn("MavenJDOMUtil.read: app is null or disposed")
      return null
    }
    return withContext(Dispatchers.IO) {
      try {
        Files.newInputStream(file).use { inputStream ->
          val text = inputStream.bufferedReader(charset).readText()
          val element = doRead(text, handler)
          if (element == null) {
            MavenLog.LOG.warn("MavenJDOMUtil.read: reading result is null for file ${file}, text '$text'")
          }
          element
        }
      }
      catch (e: IOException) {
        MavenLog.LOG.warn("MavenJDOMUtil.read: file ${file} text is not loaded", e)
        handler?.onReadError(e)
        null
      }
    }
  }

  @JvmStatic
  fun read(bytes: ByteArray, handler: ErrorHandler?): Element? {
    return doRead(CharsetToolkit.bytesToString(bytes, EncodingRegistry.getInstance().defaultCharset), handler)
  }

  private fun doRead(text: String, handler: ErrorHandler?): Element? {
    val stack = LinkedList<Element>()

    val result = arrayOf<Element?>(null)
    // Capture xmlns from root element to support Maven 4's optional modelVersion inference
    var pendingNamespace: String? = null
    val driver = XmlBuilderDriver(text)
    val builder: XmlBuilder = object : XmlBuilder {
      override fun doctype(publicId: CharSequence?, systemId: CharSequence?, startOffset: Int, endOffset: Int) {
      }

      override fun startTag(localName: CharSequence,
                            namespace: String,
                            startoffset: Int,
                            endoffset: Int,
                            headerEndOffset: Int): XmlBuilder.ProcessingOrder {
        val name = localName.toString()
        if (name.isBlank()) return XmlBuilder.ProcessingOrder.TAGS
        val isRootElement = stack.isEmpty()
        val newElement = try {
          if (isRootElement && namespace.isNotEmpty()) {
            pendingNamespace = namespace
          }
          Element(name)
        }
        catch (e: IllegalNameException) {
          Element("invalidName")
        }

        val parent = if (isRootElement) null else stack.last
        if (parent == null) {
          result[0] = newElement
        }
        else {
          parent.addContent(newElement)
        }
        stack.addLast(newElement)

        return XmlBuilder.ProcessingOrder.TAGS_AND_TEXTS
      }

      override fun endTag(localName: CharSequence, namespace: String, startoffset: Int, endoffset: Int) {
        val name = localName.toString()
        if (name.isBlank()) return

        val itr = stack.descendingIterator()
        while (itr.hasNext()) {
          val element = itr.next()

          if (element.name == name) {
            while (stack.removeLast() !== element) {
            }
            break
          }
        }
      }

      override fun textElement(text: CharSequence, physical: CharSequence, startoffset: Int, endoffset: Int) {
        stack.last.addContent(JDOMUtil.legalizeText(text.toString()))
      }

      override fun attribute(name: CharSequence, value: CharSequence, startoffset: Int, endoffset: Int) {
      }

      override fun entityRef(ref: CharSequence, startOffset: Int, endOffset: Int) {
      }

      override fun error(message: String, startOffset: Int, endOffset: Int) {
        handler?.onSyntaxError(message, startOffset, endOffset)
      }
    }

    driver.build(builder)
    // Apply captured namespace to root element
    if (pendingNamespace != null && result[0] != null) {
      result[0]!!.namespace = Namespace.getNamespace(pendingNamespace)
    }
    return result[0]
  }

  @JvmStatic
  fun findChildByPath(element: Element?, path: String): Element? {
    var el = element
    var i = 0
    while (el != null) {
      val dot = path.indexOf('.', i)
      if (dot == -1) {
        return el.getChild(path.substring(i))
      }

      el = el.getChild(path.substring(i, dot))
      i = dot + 1
    }

    return null
  }

  @JvmStatic
  @JvmOverloads
  fun findChildValueByPath(element: Element?, path: String, defaultValue: String? = null): String? {
    val child = findChildByPath(element, path)
    if (child == null) return defaultValue
    val childValue = child.textTrim
    return if (childValue.isEmpty()) defaultValue else childValue
  }

  @JvmStatic
  fun hasChildByPath(element: Element?, path: String): Boolean {
    return findChildByPath(element, path) != null
  }

  @JvmStatic
  fun findChildrenByPath(element: Element?, path: String, subPath: String): List<Element> {
    return collectChildren(findChildByPath(element, path), subPath)
  }

  @JvmStatic
  fun findChildrenValuesByPath(element: Element?, path: String, childrenName: String): List<String> {
    val result: MutableList<String> = ArrayList()
    for (each in findChildrenByPath(element, path, childrenName)) {
      val value = each.textTrim
      if (!value.isEmpty()) {
        result.add(value)
      }
    }
    return result
  }

  private fun collectChildren(container: Element?, subPath: String): List<Element> {
    if (container == null) return emptyList()

    val firstDot = subPath.indexOf('.')

    if (firstDot == -1) {
      return container.getChildren(subPath)
    }

    val childName = subPath.substring(0, firstDot)
    val pathInChild = subPath.substring(firstDot + 1)

    val result: MutableList<Element> = ArrayList()

    for (each in container.getChildren(childName)) {
      val child = findChildByPath(each, pathInChild)
      if (child != null) result.add(child)
    }
    return result
  }

  interface ErrorHandler {
    fun onReadError(e: IOException?)

    fun onSyntaxError(message: String, startOffset: Int, endOffset: Int)
  }
}
