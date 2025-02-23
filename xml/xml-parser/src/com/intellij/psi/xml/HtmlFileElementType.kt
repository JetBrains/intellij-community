// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml

import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import kotlin.concurrent.Volatile

open class HtmlFileElementType(
  debugName: String,
  language: Language,
) : IStubFileElementType<PsiFileStub<*>?>(debugName, language) {

  protected constructor() :
    this("html", HTMLLanguage.INSTANCE)

  override fun getStubVersion(): Int =
    getHtmlStubVersion() + 3

  companion object {
    @JvmField
    val INSTANCE: IFileElementType = HtmlFileElementType()

    @Volatile
    private var stubVersion = -1

    @JvmStatic
    fun getHtmlStubVersion(): Int {
      val version = stubVersion
      if (version != -1)
        return version

      val result = enumerate { it is IStubFileElementType<*> && isAcceptable(it) }
        .sumOf { (it as IStubFileElementType<*>).stubVersion }

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
}
