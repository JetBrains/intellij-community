package org.intellij.plugins.markdown.parser

import com.intellij.lang.LanguageASTFactory
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.lang.xml.XMLLanguage
import com.intellij.lang.xml.XmlASTFactory
import com.intellij.lang.xml.XmlTemplateTreePatcher
import com.intellij.lexer.EmbeddedTokenTypesProvider
import com.intellij.psi.LanguageFileViewProviders
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.xml.StartTagEndTokenProvider
import com.intellij.testFramework.ParsingTestCase
import org.intellij.plugins.markdown.MarkdownTestingUtil
import org.intellij.plugins.markdown.lang.MarkdownFileViewProviderFactory
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.parser.MarkdownFlavourProvider
import org.intellij.plugins.markdown.lang.parser.MarkdownParserDefinition
import org.intellij.plugins.markdown.lang.psi.MarkdownAstFactory

abstract class MarkdownParsingTestCase(dataPath: String): ParsingTestCase(
  dataPath,
  "md",
  true,
  MarkdownParserDefinition(),
  HTMLParserDefinition()
) {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    registerExtensionPoint(MarkdownFlavourProvider.extensionPoint, MarkdownFlavourProvider::class.java)
    registerExtensionPoint(EmbeddedTokenTypesProvider.EXTENSION_POINT_NAME, EmbeddedTokenTypesProvider::class.java)
    registerExtensionPoint(StartTagEndTokenProvider.EP_NAME, StartTagEndTokenProvider::class.java)
    addExplicitExtension(LanguageFileViewProviders.INSTANCE, MarkdownLanguage.INSTANCE, MarkdownFileViewProviderFactory())
    addExplicitExtension(LanguageASTFactory.INSTANCE, MarkdownLanguage.INSTANCE, MarkdownAstFactory())
    addExplicitExtension(LanguageASTFactory.INSTANCE, XMLLanguage.INSTANCE, XmlASTFactory())
    addExplicitExtension(TemplateDataElementType.TREE_PATCHER, XMLLanguage.INSTANCE, XmlTemplateTreePatcher())
  }

  override fun getTestDataPath(): String {
    return MarkdownTestingUtil.TEST_DATA_PATH
  }
}
