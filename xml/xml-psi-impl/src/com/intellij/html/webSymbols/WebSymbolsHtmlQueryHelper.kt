// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html.webSymbols

import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator.HtmlAttributeDescriptorBasedSymbol
import com.intellij.html.webSymbols.WebSymbolsHtmlQueryConfigurator.HtmlElementDescriptorBasedSymbol
import com.intellij.model.Pointer
import com.intellij.model.Pointer.hardPointer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.WebSymbolsScopeWithCache
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object WebSymbolsHtmlQueryHelper {

  @JvmStatic
  fun getStandardHtmlElementSymbolsScope(
    project: Project,
  ): WebSymbolsScope =
    StandardHtmlElementSymbolsScope(project)

  @JvmStatic
  fun getStandardHtmlAttributeSymbolsScopeForTag(
    project: Project,
    tagName: String,
  ): WebSymbolsScope =
    StandardHtmlAttributeSymbolsScope(project, tagName)

  private class StandardHtmlElementSymbolsScope(project: Project) : WebSymbolsScopeWithCache<Project, Unit>(null, project, project, Unit) {

    override fun initialize(consumer: (WebSymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      HtmlDescriptorUtils.getHtmlNSDescriptor(project)
        ?.getAllElementsDescriptors(null)
        ?.map { HtmlElementDescriptorBasedSymbol(it, null) }
        ?.forEach(consumer)

      cacheDependencies.add(ModificationTracker.NEVER_CHANGED)
    }

    override fun provides(qualifiedKind: WebSymbolQualifiedKind): Boolean =
      qualifiedKind == WebSymbol.HTML_ELEMENTS

    override fun createPointer(): Pointer<StandardHtmlElementSymbolsScope> =
      hardPointer(this)

  }

  private class StandardHtmlAttributeSymbolsScope(project: Project, tagName: String) : WebSymbolsScopeWithCache<Project, String>(null, project, project, tagName) {

    override fun initialize(consumer: (WebSymbol) -> Unit, cacheDependencies: MutableSet<Any>) {
      (HtmlDescriptorUtils.getStandardHtmlElementDescriptor(project, key)
       ?: HtmlDescriptorUtils.getStandardHtmlElementDescriptor(project, "div"))
        ?.getDefaultAttributeDescriptors(null)
        ?.asSequence()
        ?.filter { !it.getName(null).contains(':') }
        ?.map { HtmlAttributeDescriptorBasedSymbol(it, key) }
        ?.forEach(consumer)

      cacheDependencies.add(ModificationTracker.NEVER_CHANGED)
    }

    override fun provides(qualifiedKind: WebSymbolQualifiedKind): Boolean =
      qualifiedKind == WebSymbol.HTML_ATTRIBUTES

    override fun createPointer(): Pointer<StandardHtmlAttributeSymbolsScope> =
      hardPointer(this)

  }

}