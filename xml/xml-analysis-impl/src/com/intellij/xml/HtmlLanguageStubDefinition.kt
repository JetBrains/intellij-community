// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml

import com.intellij.psi.StubBuilder
import com.intellij.xml.HtmlLanguageStubVersionUtil.getHtmlStubVersion
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.LanguageStubDefinition
import com.intellij.psi.stubs.StubElementRegistryService
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

class HtmlLanguageStubDefinition : LanguageStubDefinition {
  override val stubVersion: Int
    get() = getHtmlStubVersion() + 3

  override val builder: StubBuilder
    get() = DefaultStubBuilder()
}

object HtmlLanguageStubVersionUtil {
  @Volatile
  private var stubVersion = -1

  @JvmStatic
  fun getHtmlStubVersion(): Int {
    val version = stubVersion
    if (version != -1)
      return version

    val result = IElementType.enumerate { it is IFileElementType && isAcceptable(it) }
      .mapNotNull { StubElementRegistryService.getInstance().getStubDescriptor(it.language)?.stubDefinition }
      .sumOf { it.stubVersion }

    stubVersion = result

    return result
  }

  @JvmStatic
  fun isAcceptable(elementType: IElementType): Boolean {
    val id = elementType.language.id

    //hardcoded values as in BaseHtmlLexer
    //js and css dialect uses the same stub id as the parent language
    return id == "JavaScript" || id == "CSS"
  }
}