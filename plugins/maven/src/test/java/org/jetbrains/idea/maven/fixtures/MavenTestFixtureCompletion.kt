// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertDoesntContain
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.junit.Assert.assertNotNull
import java.util.TreeSet
import java.util.function.Function

// Code-completion variant collection and assertions.

suspend fun MavenDomTestFixture.assertCompletionVariants(f: VirtualFile, vararg expected: String?) {
  assertCompletionVariants(f, LOOKUP_STRING, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  val actual = getCompletionVariants(f, lookupElementStringFunction)
  assertSameElements(actual, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsInclude(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  assertContainsElements(getCompletionVariants(f, lookupElementStringFunction), *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsInclude(f: VirtualFile, vararg expected: String?) {
  assertCompletionVariantsInclude(f, LOOKUP_STRING, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsDoNotInclude(f: VirtualFile, vararg expected: String?) {
  assertDoesntContain(getCompletionVariants(f), *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsNoCache(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  val actual = getCompletionVariantsNoCache(f, lookupElementStringFunction)
  assertSameElements(actual, *expected)
}

suspend fun MavenDomTestFixture.getCompletionVariants(f: VirtualFile): List<String?> {
  return getCompletionVariants(f) { li: LookupElement -> li.lookupString }
}

suspend fun MavenDomTestFixture.getCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
  configTest(f)
  return withContext(Dispatchers.EDT) {
    fixture.completeBasic().map { lookupElementStringFunction.apply(it) }
  }
}

suspend fun MavenDomTestFixture.getCompletionVariantsNoCache(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
  configTest(f)
  return withContext(Dispatchers.EDT) {
    fixture.complete(CompletionType.BASIC, 2).map { lookupElementStringFunction.apply(it) }
  }
}

suspend fun MavenDomTestFixture.getDependencyCompletionVariants(f: VirtualFile): Set<String> {
  return getDependencyCompletionVariants(f) { MavenDependencyCompletionUtil.getPresentableText(it!!) }
}

suspend fun MavenDomTestFixture.getDependencyCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<in MavenRepoArtifactInfo?, String>): Set<String> {
  configTest(f)
  val variants = fixture.completeBasic()
  val result: MutableSet<String> = TreeSet()
  for (each in variants) {
    val o = each.getObject()
    if (o is MavenRepoArtifactInfo) {
      result.add(lookupElementStringFunction.apply(o))
    }
  }
  return result
}

fun MavenDomTestFixture.assertCompletionVariants(fixture: CodeInsightTestFixture, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  val actual = getCompletionVariants(fixture, lookupElementStringFunction)
  val expectedList = expected.toList()
  assertNotNull("Expected $expectedList but got null", actual)
  assertSameElements(actual!!, expectedList)
}

fun MavenDomTestFixture.getCompletionVariants(fixture: CodeInsightTestFixture, lookupElementStringFunction: Function<LookupElement, String?>): List<String?>? {
  val variants = fixture.lookupElements ?: return null
  return variants.map { lookupElementStringFunction.apply(it) }
}

/** Wraps [xml] into a full `pom.xml` document the same way [com.intellij.maven.testFramework.fixtures.createProjectPom] does, for `fixture.checkResult(...)`. */
fun MavenDomTestFixture.createPomXml(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): String {
  return MavenTestCase.createPomXml(modelVersion, xml, false)
}

@Language("XML")
fun MavenDomTestFixture.createPomXml(
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: @NonNls String?,
  omitModelVersionTag: Boolean = false,
): @NonNls String {
  return createPomXml(modelVersion, xml, omitModelVersionTag)
}

@Language("XML")
fun createPomXml(
  modelVersion: String,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: @NonNls String?,
  omitModelVersionTag: Boolean,
): @NonNls String {
  val projectStartTag = """
        <?xml version="1.0"?>
        <project xmlns="http://maven.apache.org/POM/$modelVersion"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/$modelVersion http://maven.apache.org/xsd/maven-$modelVersion.xsd">
      """.trimIndent()
  return if (omitModelVersionTag) {
    "$projectStartTag\n$xml</project>"
  }
  else {
    "$projectStartTag\n  <modelVersion>$modelVersion</modelVersion>\n$xml</project>"
  }
}