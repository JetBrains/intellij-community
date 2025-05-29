// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.html.webSymbols.PolySymbolsHtmlQueryConfigurator.HtmlAttributeDescriptorBasedSymbol
import com.intellij.html.webSymbols.PolySymbolsHtmlQueryConfigurator.HtmlElementDescriptorBasedSymbol
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolsScope
import com.intellij.polySymbols.PolySymbolsScopeWithCache
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object WebSymbolsHtmlQueryHelper {

  @JvmStatic
  fun getStandardHtmlElementSymbolsScope(
    project: Project,
  ): PolySymbolsScope =
    StandardHtmlElementSymbolsScope(project)

  @JvmStatic
  fun getStandardHtmlAttributeSymbolsScopeForTag(
    project: Project,
    tagName: String,
  ): PolySymbolsScope =
    StandardHtmlAttributeSymbolsScope(project, tagName)

  private class StandardHtmlElementSymbolsScope(project: Project) : PolySymbolsScopeWithCache<Project, Unit>(null, project, project, Unit) {

    override fun initialize(consumer: (PolySymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      HtmlDescriptorUtils.getHtmlNSDescriptor(project)
        ?.getAllElementsDescriptors(null)
        ?.map { HtmlElementDescriptorBasedSymbol(it, null) }
        ?.forEach(consumer)

      cacheDependencies.add(ModificationTracker.NEVER_CHANGED)
    }

    override fun provides(qualifiedKind: PolySymbolQualifiedKind): Boolean =
      qualifiedKind == PolySymbol.HTML_ELEMENTS

    override fun createPointer(): Pointer<StandardHtmlElementSymbolsScope> =
      hardPointer(this)

  }

  private class StandardHtmlAttributeSymbolsScope(project: Project, tagName: String) : PolySymbolsScopeWithCache<Project, String>(null, project, project, tagName) {

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
      qualifiedKind == PolySymbol.HTML_ATTRIBUTES

    override fun createPointer(): Pointer<StandardHtmlAttributeSymbolsScope> =
      hardPointer(this)

  }

}