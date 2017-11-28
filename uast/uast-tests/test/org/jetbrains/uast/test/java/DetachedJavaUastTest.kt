// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import junit.framework.TestCase
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.Test
import java.io.File

class DetachedJavaUastTest : AbstractJavaUastTest() {

  override fun check(testName: String, file: UFile) {
    val psiFile = file.psi
    val declarations = PsiTreeUtil.collectElements(psiFile, { it is PsiDeclarationStatement })
    if (declarations.isNotEmpty()) {
      println("declarations in $testName[${declarations.size}]")
      for (declaration in declarations) {
//            println("   " + declaration.text)
//            println("   as U:" + declaration.toUElement())
        TestCase.assertNotNull("convert ${declaration.text} in $testName\"", declaration.toUElement())
        val createStatementFromText = elementFactory.createStatementFromText(declaration.text, declaration)
        TestCase.assertTrue("convert ${declaration.text} in $testName\"", createStatementFromText is PsiDeclarationStatement)
        TestCase.assertNotNull("convert2 ${declaration.text} in $testName\"", createStatementFromText.toUElement())
      }
    }
  }

  //  @Test
  fun ntestAll() {
    val elementFactory = JavaPsiFacade.getElementFactory(project)
    for (file in File("/Users/nickl-mac/IdeaProjects/IDEA").walkTopDown()
      .filter { it.isFile && it.extension == "java" && !it.absolutePath.contains("testData") }) {
      try {
//        val psiFile = myFixture.configureByText(file.name, file.readText())
        val psiFile: PsiFile = PsiFileFactory.getInstance(project).createFileFromText(JavaLanguage.INSTANCE, file.readText())
        val uFile = psiFile.toUElement(UFile::class.java)!!
//        if (declarations.isNotEmpty()) {
//          uFile.collectElements(UDeclarationsExpression::class.java).let { udeclarations ->
//            TestCase.assertTrue("nubmer of declarations  ${udeclarations.size} >= ${declarations.size} " +
//                                "declarations = ${declarations.joinToString { "[${it.text}]" }} in ${file.absolutePath}",
//                                udeclarations.size >= declarations.size)
//          }
//
//        }
        check(file.absolutePath, uFile)
      }
      catch (e: Exception) {
        throw Exception("error processing ${file.absolutePath}", e)
      }

    }

//    myFixture.configureByFile()

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

fun <T : UElement> UElement.collectElements(clazz: Class<T>): List<T> {
  val result = SmartList<T>()
  this.accept(object : UastVisitor {
    override fun visitElement(node: UElement): Boolean {
      if (clazz.isAssignableFrom(node.javaClass))
        result.add(node as T)
      return false
    }
  })
  return result
}