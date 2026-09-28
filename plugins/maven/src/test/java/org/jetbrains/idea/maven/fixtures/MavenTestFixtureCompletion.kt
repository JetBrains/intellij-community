// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.assertDoNotContain
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.configTest
import com.intellij.maven.testFramework.fixtures.createPomXml
import com.intellij.openapi.application.EDT
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.junit.jupiter.api.Assertions.assertNotNull
import java.util.TreeSet
import java.util.function.Function

// Code-completion variant collection and assertions.

suspend fun MavenDomTestFixture.assertCompletionVariants(f: VirtualFile, vararg expected: String?) {
  assertCompletionVariants(f, LOOKUP_STRING, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  val actual = getCompletionVariants(f, lookupElementStringFunction)
  assertUnorderedElementsAreEqual(actual, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsInclude(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  assertContain(getCompletionVariants(f, lookupElementStringFunction), *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsInclude(f: VirtualFile, vararg expected: String?) {
  assertCompletionVariantsInclude(f, LOOKUP_STRING, *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsDoNotInclude(f: VirtualFile, vararg expected: String?) {
  assertDoNotContain(getCompletionVariants(f), *expected)
}

suspend fun MavenDomTestFixture.assertCompletionVariantsNoCache(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>, vararg expected: String?) {
  val actual = getCompletionVariantsNoCache(f, lookupElementStringFunction)
  assertUnorderedElementsAreEqual(actual, *expected)
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
  assertNotNull(actual, "Expected $expectedList but got null")
  assertUnorderedElementsAreEqual(actual!!, expectedList)
}

fun MavenDomTestFixture.getCompletionVariants(fixture: CodeInsightTestFixture, lookupElementStringFunction: Function<LookupElement, String?>): List<String?>? {
  val variants = fixture.lookupElements ?: return null
  return variants.map { lookupElementStringFunction.apply(it) }
}

/** Wraps [xml] into a full `pom.xml` document the same way [com.intellij.maven.testFramework.fixtures.createProjectPom] does, for `fixture.checkResult(...)`. */
fun MavenDomTestFixture.createPomXml(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): String {
  return createPomXml(modelVersion, xml, false)
}

@Language("XML")
fun MavenDomTestFixture.createPomXml(
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: @NonNls String?,
  omitModelVersionTag: Boolean = false,
): @NonNls String {
  return createPomXml(modelVersion, xml, omitModelVersionTag)
}