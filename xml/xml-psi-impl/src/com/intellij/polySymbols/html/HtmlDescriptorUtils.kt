// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.html.elements.HtmlElementSymbolDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlTag
import com.intellij.util.asSafely
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor
import com.intellij.xml.util.XmlUtil
import org.jetbrains.annotations.ApiStatus

object HtmlDescriptorUtils {
  @JvmStatic
  fun getHtmlNSDescriptor(project: Project): HtmlNSDescriptorImpl? {
    return CachedValuesManager.getManager(project).getCachedValue(project) {
      val descriptor = XmlElementFactory.getInstance(project)
        .createTagFromText("<div>")
        .getNSDescriptor(XmlUtil.HTML_URI, true)
      if (descriptor !is HtmlNSDescriptorImpl) {
        return@getCachedValue CachedValueProvider.Result.create<HtmlNSDescriptorImpl>(null, ModificationTracker.EVER_CHANGED)
      }
      CachedValueProvider.Result.create(descriptor, descriptor.getDescriptorFile()!!)
    }
  }

  @JvmStatic
  fun getStandardHtmlAttributeDescriptors(tag: XmlTag): Sequence<XmlAttributeDescriptor> =
    getHtmlElementDescriptor(tag)
      ?.getDefaultAttributeDescriptors(tag)
      ?.asSequence()
      ?.filter { !it.getName(tag).contains(':') }
    ?: emptySequence()

  @JvmStatic
  fun getStandardHtmlAttributeDescriptor(tag: XmlTag, attrName: String): XmlAttributeDescriptor? =
    getHtmlElementDescriptor(tag)
      ?.getDefaultAttributeDescriptor(attrName.adjustCase(tag), tag)
      ?.takeIf { !it.getName(tag).contains(':') }

  private fun getHtmlElementDescriptor(tag: XmlTag): HtmlElementDescriptorImpl? =
    when (val tagDescriptor = tag.descriptor) {
      is HtmlElementDescriptorImpl -> tagDescriptor
      is HtmlElementSymbolDescriptor, is AnyXmlElementDescriptor -> {
        getStandardHtmlElementDescriptor(tag)
        ?: getStandardHtmlElementDescriptor(tag, "div")
      }
      else -> null
    }

  @JvmStatic
  @ApiStatus.Internal
  fun getStandardHtmlElementDescriptor(tag: XmlTag, name: String = tag.localName): HtmlElementDescriptorImpl? {
    val parentTag = tag.parentTag
    if (parentTag != null) {
      parentTag.getNSDescriptor(parentTag.namespace, false)
        .asSafely<HtmlNSDescriptorImpl>()
        ?.getElementDescriptorByName(parentTag.localName.adjustCase(tag))
        ?.asSafely<HtmlElementDescriptorImpl>()
        ?.getElementDescriptor(name.adjustCase(tag), parentTag)
        ?.asSafely<HtmlElementDescriptorImpl>()
        ?.let { return it }
    }
    return getStandardHtmlElementDescriptor(tag.project, name.adjustCase(tag))
  }

  @JvmStatic
  @ApiStatus.Internal
  fun getStandardHtmlElementDescriptor(project: Project, name: String): HtmlElementDescriptorImpl? {
    return getHtmlNSDescriptor(project)
      ?.getElementDescriptorByName(name)
      ?.asSafely<HtmlElementDescriptorImpl>()
  }

  private fun String.adjustCase(tag: XmlTag) =
    if (tag.isCaseSensitive) this else StringUtil.toLowerCase(this)

}