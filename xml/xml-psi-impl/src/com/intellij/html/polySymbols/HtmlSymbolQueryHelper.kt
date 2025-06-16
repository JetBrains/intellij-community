// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.polySymbols

import com.intellij.html.polySymbols.HtmlSymbolQueryConfigurator.HtmlAttributeDescriptorBasedSymbol
import com.intellij.html.polySymbols.HtmlSymbolQueryConfigurator.HtmlElementDescriptorBasedSymbol
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.html.HTML_ATTRIBUTES
import com.intellij.polySymbols.html.HTML_ELEMENTS
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.PolySymbolScopeWithCache
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object HtmlSymbolQueryHelper {

  @JvmStatic
  fun getStandardHtmlElementSymbolsScope(
    project: Project,
  ): PolySymbolScope =
    StandardHtmlElementsScope(project)

  @JvmStatic
  fun getStandardHtmlAttributeSymbolsScopeForTag(
    project: Project,
    tagName: String,
  ): PolySymbolScope =
    StandardHtmlAttributesScope(project, tagName)

  private class StandardHtmlElementsScope(project: Project) : PolySymbolScopeWithCache<Project, Unit>(null, project, project, Unit) {

    override fun initialize(consumer: (PolySymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      HtmlDescriptorUtils.getHtmlNSDescriptor(project)
        ?.getAllElementsDescriptors(null)
        ?.map { HtmlElementDescriptorBasedSymbol(it, null) }
        ?.forEach(consumer)

      cacheDependencies.add(ModificationTracker.NEVER_CHANGED)
    }

    override fun provides(qualifiedKind: PolySymbolQualifiedKind): Boolean =
      qualifiedKind == HTML_ELEMENTS

    override fun createPointer(): Pointer<StandardHtmlElementsScope> =
      hardPointer(this)

  }

  private class StandardHtmlAttributesScope(project: Project, tagName: String) : PolySymbolScopeWithCache<Project, String>(null, project, project, tagName) {

    override fun initialize(consumer: (PolySymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      (HtmlDescriptorUtils.getStandardHtmlElementDescriptor(project, key)
       ?: HtmlDescriptorUtils.getStandardHtmlElementDescriptor(project, "div"))
        ?.getDefaultAttributeDescriptors(null)
        ?.asSequence()
        ?.filter { !it.getName(null).contains(':') }
        ?.map { HtmlAttributeDescriptorBasedSymbol(it, key) }
        ?.forEach(consumer)

      cacheDependencies.add(ModificationTracker.NEVER_CHANGED)
    }

    override fun provides(qualifiedKind: PolySymbolQualifiedKind): Boolean =
      qualifiedKind == HTML_ATTRIBUTES

    override fun createPointer(): Pointer<StandardHtmlAttributesScope> =
      hardPointer(this)

  }

}