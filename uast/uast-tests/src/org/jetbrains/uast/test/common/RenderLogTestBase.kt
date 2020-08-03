// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.uast.*
import com.intellij.testFramework.assertEqualsToFile
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.Assert
import java.io.File
import java.util.*

interface RenderLogTestBase {
  fun getTestDataPath(): String

  private fun getTestFile(testName: String, ext: String) =
    File(getTestDataPath(), testName.substringBeforeLast('.') + '.' + ext)

  fun check(testName: String, file: UFile) = check(testName, file, true)

  fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
    val renderFile = getTestFile(testName, "render.txt")
    val logFile = getTestFile(testName, "log.txt")

    assertEqualsToFile("Render string", renderFile, file.asRenderString())
    assertEqualsToFile("Log string", logFile, file.asRecursiveLogString())

    if (checkParentConsistency) {
      checkParentConsistency(file)
    }

    file.checkContainingFileForAllElements()
  }

  fun checkParentConsistency(file: UFile) {
    val parentMap = mutableMapOf<PsiElement, String>()

    file.accept(object : UastVisitor {
      private val parentStack = Stack<UElement>()

      override fun visitElement(node: UElement): Boolean {

        val parent = node.uastParent
        if (parent == null) {
          Assert.assertTrue("Wrong null-parent of ${node.javaClass} '${node.sourcePsi?.text?.lineSequence()?.firstOrNull()}'",
                            parentStack.empty())
        }
        else {
          Assert.assertEquals("Wrong parent of ${node.javaClass} '${node.sourcePsi?.text?.lineSequence()?.firstOrNull()}'", parentStack.peek(),
                              parent)
        }
        node.sourcePsi?.let {
          if (it !in parentMap) {
            parentMap[it] = parentStack.reversed().joinToString { it.asLogString() }
          }
        }
        parentStack.push(node)
        return false
      }

      override fun afterVisitElement(node: UElement) {
        super.afterVisitElement(node)
        parentStack.pop()
      }
    })


    file.sourcePsi.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val uElement = element.toUElement()
        val expectedParents = parentMap[element]
        if (expectedParents != null) {
          Assert.assertNotNull("Expected to be able to convert PSI element $element", uElement)
          val parents = generateSequence(uElement!!.uastParent) { it.uastParent }.joinToString { it.asLogString() }
          Assert.assertEquals(
            "Inconsistent parents for $uElement (converted from $element) parent: -> ${uElement.uastParent}",
            expectedParents,
            parents)
        }
        super.visitElement(element)
      }
    })
  }

  fun UFile.checkContainingFileForAllElements() {
    accept(object : UastVisitor {
      override fun visitElement(node: UElement): Boolean {
        node.sourcePsi?.let { sourcePsi ->
          val uElement = sourcePsi.toUElement()!!
          Assert.assertEquals("getContainingUFile should be equal to source for ${uElement.javaClass} for ${sourcePsi.text}",
                              this@checkContainingFileForAllElements,
                              uElement.getContainingUFile())
        }

        val uastAnchor = (node as? UDeclaration)?.uastAnchor
        if (uastAnchor != null) {
          Assert.assertEquals("should be appropriate sourcePsi for uastAnchor for ${node.javaClass} [${node.sourcePsi?.text}] ",
                              node.sourcePsiElement!!.containingFile!!, uastAnchor.sourcePsi?.containingFile)
        }

        val anchorPsi = uastAnchor?.sourcePsi
        if (anchorPsi != null) {
          Assert.assertEquals(anchorPsi.containingFile, node.sourcePsiElement!!.containingFile!!)
        }

        return false
      }
    })
  }
}