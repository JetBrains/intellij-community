// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import junit.framework.TestCase
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.junit.Test
import java.io.File

class DetachedJavaUastTest : AbstractJavaUastTest() {

  private val detachers = mapOf(
    PsiStatement::class.java through PsiElementFactory::createStatementFromText
  )

  override fun check(testName: String, file: UFile) {
    val detachablePsiElements = PsiTreeUtil.collectElementsOfType(file.psi, *detachers.keys.toTypedArray())
      .map { psiElement -> psiElement to detachers.entries.first { it.key.isAssignableFrom(psiElement.javaClass) }.value }

    for ((element, detacher) in detachablePsiElements) {
      val attachedUElement = element.toUElement()
      TestCase.assertNotNull("UElement should be defined for attached element with text '${element.text}' in $testName",
                             attachedUElement)
      val detachedPsiElement = detacher.invoke(elementFactory, element.text, element)
      val detachedUElement = try {
        detachedPsiElement.toUElement()
      }
      catch (e: Exception) {
        throw AssertionError("failed to convert '${detachedPsiElement.text}' to Uast in $testName", e)
      }
      TestCase.assertNotNull("UElement should be defined for detached element with text '${detachedPsiElement.text}' in $testName\"",
                             detachedUElement)
      TestCase.assertEquals("attached and detached elements should be equal at least in type for '${element.text}' in $testName\"",
                            attachedUElement?.javaClass, detachedUElement?.javaClass)
    }

  }

  //  @Test
  fun ntestAll() {
    for (file in File("/Users/nickl-mac/IdeaProjects/IDEA").walkTopDown()
      .filter { it.isFile && it.extension == "java" && !it.absolutePath.contains("testData") }) {
      try {
        val psiFile: PsiFile = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, file.readText())
        val uFile = psiFile.toUElement(UFile::class.java)!!

        check(file.absolutePath, uFile)
      }
      catch (e: Exception) {
        throw Exception("error processing ${file.absolutePath}", e)
      }

    }
  }

  @Test
  fun testDataClass() = doTest("DataClass/DataClass.java")

  @Test
  fun testEnumSwitch() = doTest("Simple/EnumSwitch.java")

  @Test
  fun testLocalClass() = doTest("Simple/LocalClass.java")

  @Test
  fun testReturnX() = doTest("Simple/ReturnX.java")

  @Test
  fun testJava() = doTest("Simple/Simple.java")

  @Test
  fun testFor() = doTest("Simple/For.java")

  @Test
  fun testClass() = doTest("Simple/SuperTypes.java")

  @Test
  fun testTryWithResources() = doTest("Simple/TryWithResources.java")

  @Test
  fun testEnumValueMembers() = doTest("Simple/EnumValueMembers.java")

  @Test
  fun testQualifiedConstructorCall() = doTest("Simple/QualifiedConstructorCall.java")

  @Test
  fun testAnonymousClassWithParameters() = doTest("Simple/AnonymousClassWithParameters.java")

  @Test
  fun testVariableAnnotation() = doTest("Simple/VariableAnnotation.java")

  @Test
  fun testPackageInfo() = doTest("Simple/package-info.java")

}

private infix fun Class<out PsiElement>.through(detacher: (PsiElementFactory, String, PsiElement) -> PsiElement)
  : Pair<Class<PsiElement>, (PsiElementFactory, String, PsiElement) -> PsiElement> = (this as Class<PsiElement>) to detacher
