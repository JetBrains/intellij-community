// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.lexer

import com.intellij.codeInsight.completion.CompletionUtilCore
import com.intellij.lang.Language
import com.intellij.lang.LanguageHtmlScriptContentProvider
import com.intellij.lang.LanguageUtil
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.xml.XmlElementType
import com.intellij.psi.xml.XmlTokenType.*
import com.intellij.util.text.CharArrayUtil
import com.intellij.xml.util.HtmlUtil
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import kotlin.streams.asSequence

interface HtmlEmbeddedContentSupport {

  @JvmDefault
  fun isEnabled(lexer: BaseHtmlLexer): Boolean = true

  @JvmDefault
  fun createEmbeddedContentProviders(lexer: BaseHtmlLexer): List<HtmlEmbeddedContentProvider> = emptyList()

  @JvmDefault
  fun getCustomAttributeEmbedmentTokens(): TokenSet = TokenSet.EMPTY

  @JvmDefault
  fun getCustomTagEmbedmentTokens(): TokenSet = TokenSet.EMPTY

  @JvmDefault
  fun getScriptTagEmbedmentInfo(): Map<Language, HtmlEmbedmentInfo> = emptyMap()

  companion object {
    internal val EP_NAME: ExtensionPointName<HtmlEmbeddedContentSupport> = ExtensionPointName.create(
      "com.intellij.html.embeddedContentSupport")

    @JvmStatic
    fun getStyleTagEmbedmentInfo(language: Language): HtmlEmbedmentInfo? =
      if (LanguageUtil.isInjectableLanguage(language))
        EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME.extensions()
          .map { it.elementType }
          .filter { language.`is`(it.language) }
          .map { elementType ->
            HtmlEmbedmentInfo(elementType, language)
          }
          .findFirst()
          .orElse(null)
      else null

    @JvmStatic
    fun getScriptTagEmbedmentInfo(language: Language): HtmlEmbedmentInfo? =
      if (LanguageUtil.isInjectableLanguage(language))
        find(ourScriptTagEmbedmentInfo.value, language)
      else null

    /**
     * Use this method to register support in ParsingTestCases only
     */
    @TestOnly
    @JvmStatic
    fun register(application: Application, disposable: Disposable, vararg supports: Class<out HtmlEmbeddedContentSupport>) {
      val name = EP_NAME.name
      val extensionPoint = application.extensionArea.let {
        val point = it.getExtensionPointIfRegistered<HtmlEmbeddedContentSupport>(name)
        if (point == null) {
          it.registerDynamicExtensionPoint(name, HtmlEmbeddedContentSupport::class.java.name,
                                           ExtensionPoint.Kind.INTERFACE)
          it.getExtensionPoint(name)
        }
        else {
          point
        }
      }
      supports.asSequence().map { it.getDeclaredConstructor().newInstance() }
        .forEach { extensionPoint.registerExtension(it, disposable) }
    }
  }
}

private val ourScriptTagEmbedmentInfo =
  ClearableLazyValue.createAtomic {
    try {
      HtmlEmbeddedContentSupport.EP_NAME.extensions().asSequence()
    }
    catch (e: IllegalArgumentException) {
      // Tolerate missing extension point for parser tests
      emptySequence()
    }
      .plus(HtmlDefaultEmbeddedContentSupport())
      .flatMap { it.getScriptTagEmbedmentInfo().asSequence() }
      .distinctBy { it.key }
      .associateBy({ it.key }, { it.value })
  }.also {
    try {
      ExtensionPointUtil.dropLazyValueOnChange(it, HtmlEmbeddedContentSupport.EP_NAME, null)
    }
    catch (e: IllegalArgumentException) {
      // Tolerate missing extension point for parser tests
    }
  }


private fun find(map: Map<Language, HtmlEmbedmentInfo>, language: Language): HtmlEmbedmentInfo? {
  var l: Language? = language
  do {
    map[l]?.let { return it }
    l = l?.baseLanguage
  }
  while (l != null)
  return null
}

interface HtmlEmbeddedContentProvider {
  fun handleToken(tokenType: IElementType, range: TextRange)
  fun createEmbedment(tokenType: IElementType): HtmlEmbedment?
  fun clearEmbedment()
  fun hasState(): Boolean
  fun getState(): Any?
  fun restoreState(state: Any?)
}

class HtmlEmbedmentInfo(val elementTypeProvider: Function<BaseHtmlLexer?, IElementType?>,
                        val highlightingLexerProvider: Function<BaseHtmlLexer?, Lexer?>) {
  constructor(elementType: IElementType, syntaxHighlighterLanguage: Language) :
    this({ elementType },
         { SyntaxHighlighterFactory.getSyntaxHighlighter(syntaxHighlighterLanguage, it?.project, null).highlightingLexer })

}

class HtmlEmbedment(private val elementTypeProvider: Function<BaseHtmlLexer?, IElementType?>,
                    private val highlightingLexerProvider: Function<BaseHtmlLexer?, Lexer?>,
                    val range: TextRange,
                    val baseLexerState: Int) {

  fun getElementType(lexer: HtmlLexer): IElementType? = elementTypeProvider.apply(lexer)
  fun createHighlightingLexer(lexer: HtmlHighlightingLexer): Lexer? = highlightingLexerProvider.apply(lexer)
}

abstract class BaseHtmlEmbeddedContentProvider(protected val lexer: BaseHtmlLexer) : HtmlEmbeddedContentProvider {

  @JvmField
  internal var embedment = false

  private val myNamesEqual: (CharSequence?, CharSequence?) -> Boolean =
    if (lexer.isCaseInsensitive) StringUtil::equalsIgnoreCase else StringUtil::equals

  protected fun namesEqual(name1: CharSequence?, name2: CharSequence?): Boolean = myNamesEqual(name1, name2)

  override fun createEmbedment(tokenType: IElementType): HtmlEmbedment? =
    if (embedment && isStartOfEmbedment(tokenType)) {
      createEmbedmentInfo()?.let {
        val startOffset = lexer.delegate.tokenStart
        val endInfo = findTheEndOfEmbedment()
        HtmlEmbedment(it.elementTypeProvider, it.highlightingLexerProvider,
                      TextRange(startOffset, endInfo.first), endInfo.second)
      }
    }
    else null

  override fun clearEmbedment() {
    embedment = false
  }

  protected abstract fun isStartOfEmbedment(tokenType: IElementType): Boolean
  protected abstract fun createEmbedmentInfo(): HtmlEmbedmentInfo?
  protected abstract fun findTheEndOfEmbedment(): Pair<Int, Int>

  override fun hasState(): Boolean = embedment

  override fun getState(): Any? =
    if (hasState()) BaseState(embedment) else null

  override fun restoreState(state: Any?) {
    if (state == null) {
      embedment = false
      return
    }
    embedment = (state as? BaseState)?.embedment == true
  }

  open class BaseState(val embedment: Boolean)

}


open class HtmlTokenEmbeddedContentProvider
@JvmOverloads constructor(lexer: BaseHtmlLexer,
                          private val token: IElementType,
                          highlightingLexerSupplier: Supplier<Lexer?>,
                          elementTypeOverrideSupplier: Supplier<IElementType?> = Supplier { token })
  : BaseHtmlEmbeddedContentProvider(lexer) {

  private val info = HtmlEmbedmentInfo(Function { elementTypeOverrideSupplier.get() }, Function { highlightingLexerSupplier.get() })

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean = tokenType == this.token

  override fun createEmbedmentInfo(): HtmlEmbedmentInfo? = info

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    baseLexer.advance()
    val result = Pair(baseLexer.tokenStart, baseLexer.state)
    baseLexer.restore(position)
    return result
  }

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    embedment = tokenType == this.token
  }

}

abstract class HtmlAttributeEmbeddedContentProvider(lexer: BaseHtmlLexer)
  : BaseHtmlEmbeddedContentProvider(lexer) {

  private var myTagNameRead = false
  private var myWithinTag = false
  private var myTagName: CharSequence? = null
  private var myAttributeName: CharSequence? = null

  protected val attributeName: CharSequence?
    get() = myAttributeName
  protected val tagName: CharSequence?
    get() = myTagName

  protected abstract fun isInterestedInTag(tagName: CharSequence): Boolean

  protected abstract fun isInterestedInAttribute(attributeName: CharSequence): Boolean

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    when (tokenType) {
      XML_START_TAG_START -> {
        myTagNameRead = false
        embedment = false
        myWithinTag = false
        myTagName = null
        myAttributeName = null
      }
      XML_NAME -> {
        val baseLexer = lexer.delegate
        if (!myTagNameRead) {
          val tagName = range.subSequence(baseLexer.bufferSequence)
          myWithinTag = isInterestedInTag(tagName)
          if (myWithinTag) {
            this.myTagName = tagName
          }
          myTagNameRead = true
        }
        else if (myWithinTag) {
          val attributeName = range.subSequence(baseLexer.bufferSequence)
          embedment = isInterestedInAttribute(attributeName)
          if (embedment) {
            this.myAttributeName = attributeName
          }
          else {
            this.myAttributeName = null
          }
        }
      }
      XML_TAG_END, XML_END_TAG_START, XML_EMPTY_ELEMENT_END -> {
        myTagNameRead = tokenType === XML_END_TAG_START
        myWithinTag = false
        myTagName = null
        myAttributeName = null
        embedment = false
      }
    }
  }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean =
    myAttributeName != null && isAttributeEmbedmentToken(tokenType)

  protected open fun isAttributeEmbedmentToken(tokenType: IElementType): Boolean =
    lexer.isAttributeEmbedmentToken(tokenType)

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    while (true) {
      if (!isAttributeEmbedmentToken(baseLexer.tokenType ?: break)) break
      baseLexer.advance()
    }
    val result = Pair(baseLexer.tokenStart, baseLexer.state)
    baseLexer.restore(position)
    return result
  }

  override fun restoreState(state: Any?) {
    if (state == null) {
      myTagNameRead = false
      myWithinTag = false
      myTagName = null
      myAttributeName = null
      embedment = false
      return
    }
    if (state !is AttributeState) return
    myTagNameRead = state.tagNameRead
    myWithinTag = state.withinTag
    myTagName = state.tagName
    myAttributeName = state.attributeName
    embedment = state.embedment
  }

  override fun hasState(): Boolean = myTagNameRead || myWithinTag || myTagName != null || myAttributeName != null || embedment

  override fun getState(): Any? =
    if (hasState())
      AttributeState(myTagNameRead, myWithinTag, myTagName, myAttributeName, embedment)
    else
      null

  open class AttributeState(val tagNameRead: Boolean, val withinTag: Boolean, val tagName: CharSequence?,
                            val attributeName: CharSequence?, embedment: Boolean) : BaseState(embedment)

}

abstract class HtmlTagEmbeddedContentProvider(lexer: BaseHtmlLexer) : BaseHtmlEmbeddedContentProvider(lexer) {

  private var myAttributeValue: CharSequence? = null
  private var myAttributeName: CharSequence? = null
  private var myTagName: CharSequence? = null
  private var myTagNameRead: Boolean = false
  private var myWithinTag: Boolean = false
  private var myReadAttributeValue: Boolean = false

  protected val attributeValue: CharSequence?
    get() = myAttributeValue
  protected val attributeName: CharSequence?
    get() = myAttributeName
  protected val tagName: CharSequence?
    get() = myTagName
  protected val withinTag: Boolean
    get() = myWithinTag

  protected abstract fun isInterestedInTag(tagName: CharSequence): Boolean

  protected abstract fun isInterestedInAttribute(attributeName: CharSequence): Boolean

  override fun handleToken(tokenType: IElementType, range: TextRange) {
    when (tokenType) {
      XML_START_TAG_START -> {
        myTagNameRead = false
        myReadAttributeValue = false
        myWithinTag = false
      }
      XML_NAME -> {
        val baseLexer = lexer.delegate
        if (!myTagNameRead) {
          val tagName = range.subSequence(baseLexer.bufferSequence)
          myWithinTag = isInterestedInTag(tagName)
          if (myWithinTag) {
            myTagName = tagName
          }
          else {
            myTagName = null
          }
          myTagNameRead = true
          myAttributeName = null
          myAttributeValue = null
        }
        else if (myWithinTag) {
          val attributeName = range.subSequence(baseLexer.bufferSequence)
          myReadAttributeValue = isInterestedInAttribute(attributeName)
          if (myReadAttributeValue) {
            this.myAttributeName = attributeName
            this.myAttributeValue = attributeName
          }
        }
        embedment = false
      }
      XML_ATTRIBUTE_VALUE_TOKEN -> {
        if (myReadAttributeValue) {
          myAttributeValue = range.subSequence(lexer.delegate.bufferSequence)
        }
      }
      XML_TAG_END, XML_EMPTY_ELEMENT_END -> {
        embedment = tokenType == XML_TAG_END && myWithinTag
        myWithinTag = false
        myTagNameRead = false
        myReadAttributeValue = false
        if (!embedment) {
          myTagName = null
          myAttributeName = null
          myAttributeValue = null
        }
      }
      XML_END_TAG_START -> {
        myTagNameRead = true
        myReadAttributeValue = false
        myWithinTag = false
      }
    }
  }

  override fun createEmbedment(tokenType: IElementType): HtmlEmbedment? =
    super.createEmbedment(tokenType)?.takeIf { !it.range.isEmpty }

  override fun isStartOfEmbedment(tokenType: IElementType): Boolean =
    myTagName != null && isTagEmbedmentStartToken(tokenType)

  protected open fun isTagEmbedmentStartToken(tokenType: IElementType): Boolean =
    lexer.isTagEmbedmentStartToken(tokenType)

  override fun findTheEndOfEmbedment(): Pair<Int, Int> {
    tagName!!
    val baseLexer = lexer.delegate
    val position = baseLexer.currentPosition
    val bufferEnd = baseLexer.bufferEnd
    var lastState: Int
    var lastStart: Int

    val buf = baseLexer.bufferSequence
    val bufArray = CharArrayUtil.fromSequenceWithoutCopying(buf)

    while (true) {
      FindTagEnd@ while (baseLexer.tokenType.let { it != null && it !== XML_END_TAG_START }) {
        if (baseLexer.tokenType === XML_COMMENT_CHARACTERS) {
          // we should terminate on first occurence of </
          val end = baseLexer.tokenEnd

          for (i in baseLexer.tokenStart until end) {
            if (i + 1 < end
                && (if (bufArray != null) bufArray[i] else buf[i]) == '<'
                && (if (bufArray != null) bufArray[i + 1] else buf[i + 1]) == '/') {
              baseLexer.start(buf, i, bufferEnd, 0)
              baseLexer.tokenType
              break@FindTagEnd
            }
          }
        }
        baseLexer.advance()
      }
      lastState = baseLexer.state
      lastStart = baseLexer.tokenStart
      if (baseLexer.tokenType == null) break
      baseLexer.advance()
      while (WHITESPACES.contains(baseLexer.tokenType)) {
        baseLexer.advance()
      }
      if (baseLexer.tokenType == null) break
      if (baseLexer.tokenType === XML_NAME) {
        val tokenText = buf.subSequence(baseLexer.tokenStart, baseLexer.tokenEnd)
        if (namesEqual(tagName, tokenText)
            || namesEqual(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, tokenText)) {
          break // really found end
        }
      }
    }
    baseLexer.restore(position)
    return Pair(lastStart, lastState)
  }

  override fun restoreState(state: Any?) {
    if (state == null) {
      myTagNameRead = false
      myWithinTag = false
      myTagName = null
      myAttributeName = null
      myReadAttributeValue = false
      myAttributeValue = null
      embedment = false
      return
    }
    if (state !is TagState) return
    myTagNameRead = state.tagNameRead
    myWithinTag = state.withinTag
    myTagName = state.tagName
    myAttributeName = state.attributeName
    myReadAttributeValue = state.readAttributeValue
    myAttributeValue = state.attributeValue
    embedment = state.embedment
  }

  override fun hasState(): Boolean = myTagNameRead || myWithinTag || myTagName != null || myAttributeName != null || embedment

  override fun getState(): Any? =
    if (hasState())
      TagState(myTagNameRead, myWithinTag, myTagName, myAttributeName, myReadAttributeValue, myAttributeValue, embedment)
    else
      null

  open class TagState(val tagNameRead: Boolean, val withinTag: Boolean, val tagName: CharSequence?,
                      val attributeName: CharSequence?, val readAttributeValue: Boolean,
                      val attributeValue: CharSequence?, embedment: Boolean) : BaseState(embedment)


}

internal class HtmlDefaultEmbeddedContentSupport : HtmlEmbeddedContentSupport {

  override fun createEmbeddedContentProviders(lexer: BaseHtmlLexer): List<HtmlEmbeddedContentProvider> =
    listOf(HtmlDefaultTagEmbeddedContentProvider(lexer))

  override fun getCustomAttributeEmbedmentTokens(): TokenSet = ATTRIBUTE_EMBEDMENT_TOKENS

  override fun getCustomTagEmbedmentTokens(): TokenSet = TAG_EMBEDMENT_START_TOKENS

  override fun getScriptTagEmbedmentInfo(): Map<Language, HtmlEmbedmentInfo> {
    @Suppress("UNCHECKED_CAST")
    // TODO make dynamic
    return LanguageHtmlScriptContentProvider.getAllProviders().asSequence()
      .mapNotNull { provider ->
        Pair(Language.findLanguageByID(provider.key),
             HtmlEmbedmentInfo(Function { provider.instance.scriptElementType }, Function { provider.instance.highlightingLexer }))
      }
      .plus(Pair(HTMLLanguage.INSTANCE, HtmlEmbedmentInfo(XmlElementType.HTML_EMBEDDED_CONTENT, HTMLLanguage.INSTANCE)))
      .filter { it.first != null }
      .toMap() as Map<Language, HtmlEmbedmentInfo>
  }

  companion object {

    private val ATTRIBUTE_EMBEDMENT_TOKENS = TokenSet.create(XML_ATTRIBUTE_VALUE_TOKEN, XML_ENTITY_REF_TOKEN, XML_CHAR_ENTITY_REF)
    private val TAG_EMBEDMENT_START_TOKENS = TokenSet.create(
      XML_DATA_CHARACTERS, XML_CDATA_START, XML_COMMENT_START, XML_START_TAG_START, XML_REAL_WHITE_SPACE, XML_END_TAG_START,
      TokenType.WHITE_SPACE, XML_ENTITY_REF_TOKEN, XML_CHAR_ENTITY_REF
    )
  }
}

private class HtmlDefaultTagEmbeddedContentProvider(lexer: BaseHtmlLexer) : HtmlTagEmbeddedContentProvider(lexer) {

  private val infoCache = HashMap<Pair<String, String?>, HtmlEmbedmentInfo?>()

  override fun isInterestedInTag(tagName: CharSequence): Boolean =
    namesEqual(tagName, HtmlUtil.SCRIPT_TAG_NAME) || namesEqual(tagName, HtmlUtil.STYLE_TAG_NAME)

  override fun isInterestedInAttribute(attributeName: CharSequence): Boolean =
    namesEqual(attributeName, HtmlUtil.TYPE_ATTRIBUTE_NAME)
    || (namesEqual(attributeName, HtmlUtil.LANGUAGE_ATTRIBUTE_NAME) && namesEqual(tagName, HtmlUtil.SCRIPT_TAG_NAME))

  override fun createEmbedmentInfo(): HtmlEmbedmentInfo? {
    val attributeValue = attributeValue?.trim()?.toString()
    return infoCache.getOrPut(Pair(tagName!!.toString(), attributeValue)) {
      if (namesEqual(tagName, HtmlUtil.STYLE_TAG_NAME)) {
        styleLanguage(attributeValue)?.let {
          HtmlEmbeddedContentSupport.getStyleTagEmbedmentInfo(it)
        }
      }
      else {
        scriptEmbedmentInfo(attributeValue)
        ?: HtmlEmbedmentInfo(Function { XML_DATA_CHARACTERS }, Function { null })
      }
    }
  }

  private fun styleLanguage(styleLang: String?): Language? {
    val cssLanguage = Language.findLanguageByID("CSS")
    if (styleLang != null && !styleLang.equals("text/css", ignoreCase = true)) {
      cssLanguage
        ?.dialects
        ?.firstOrNull { dialect ->
          dialect.mimeTypes.any { it.equals(styleLang, ignoreCase = true) }
        }
        ?.let { return it }
    }
    return cssLanguage
  }

  private fun scriptEmbedmentInfo(mimeType: String?): HtmlEmbedmentInfo? =
    if (mimeType != null)
      Language.findInstancesByMimeType(if (lexer.isCaseInsensitive) StringUtil.toLowerCase(mimeType) else mimeType)
        .asSequence()
        .plus(if (StringUtil.containsIgnoreCase(mimeType, "template")) listOf(HTMLLanguage.INSTANCE) else emptyList())
        .map { HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(it) }
        .firstOrNull { it != null }
    else
      Language.findLanguageByID("JavaScript")?.let {
        HtmlEmbeddedContentSupport.getScriptTagEmbedmentInfo(it)
      }


}