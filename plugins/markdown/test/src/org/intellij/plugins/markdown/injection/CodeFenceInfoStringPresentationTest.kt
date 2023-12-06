package org.intellij.plugins.markdown.injection

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.DependentLanguage
import com.intellij.lang.Language
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.plugins.markdown.injection.MarkdownCodeFenceUtils.getLanguageInfoString
import org.jetbrains.yaml.YAMLLanguage

class CodeFenceInfoStringPresentationTest : BasePlatformTestCase() {
  fun testBasicLanguage() {
    val lang = object : Language("SomeTestLang") {}
    doTestWithTempLanguages("sometestlang", lang)
  }

  fun testLanguageWithAlias() {
    val lang = object : Language("HCL-Terraform") {}
    doTestWithTempLanguages("terraform", lang)
  }

  fun testDependentLanguageWithBaseLang() {
    val baseLang = object : Language("BaseLang") {}
    val parentDependent = object : Language(baseLang, "ParentDependent"), DependentLanguage {}
    val lang = object : Language(parentDependent, "Dependent"), DependentLanguage {}
    doTestWithTempLanguages("baselang", lang, parentDependent, baseLang)
  }

  fun testDependentLanguageWithoutBaseLang() {
    val parentDependent = object : Language("ParentDependent"), DependentLanguage {}
    val lang = object : Language(parentDependent, "Dependent"), DependentLanguage {}
    doTestWithTempLanguages("dependent", lang, parentDependent)
  }

  fun testCustomInfoString() {
    val lang = object : Language("Some Language") {}

    CodeFenceLanguageProvider.EP_NAME.point.registerExtension(object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(infoString: String) = null

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()

      override fun getInfoStringForLanguage(language: Language, context: PsiElement?): String? {
        return if (language == lang) "lng" else null
      }
    }, testRootDisposable)

    doTestWithTempLanguages("lng", lang)
  }

  fun testCustomInfoStringWithPsiContext() {
    val fileName = "some-config.yml"
    val file = myFixture.configureByText(fileName, """
      name: 123
      list: 
        - a<caret>bc
        - bcde
    """.trimIndent())

    val expectedInfoString = "customconfig"
    CodeFenceLanguageProvider.EP_NAME.point.registerExtension(object : CodeFenceLanguageProvider {
      override fun getLanguageByInfoString(infoString: String) = null

      override fun getCompletionVariantsForInfoString(parameters: CompletionParameters): List<LookupElement> = emptyList()

      override fun getInfoStringForLanguage(language: Language, context: PsiElement?): String? {
        return if (language == YAMLLanguage.INSTANCE && context?.containingFile?.name == fileName) expectedInfoString else null
      }
    }, testRootDisposable)

    val context = file.findElementAt(myFixture.caretOffset)!!
    assertEquals(expectedInfoString, getLanguageInfoString(context.language, context))
  }

  private fun doTestWithTempLanguages(expected: String, vararg languages: Language) {
    try {
      assertEquals(expected, getLanguageInfoString(languages.first(), null))
    }
    finally {
      languages.forEach {
        it.unregisterLanguage(DefaultPluginDescriptor("org.intellij.plugins.markdown"))
      }
    }
  }
}