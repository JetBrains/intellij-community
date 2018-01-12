/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.uast.test.common

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import junit.framework.TestCase
import org.jetbrains.uast.*
import org.jetbrains.uast.test.env.assertEqualsToFile
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.Assert
import java.io.File
import java.util.*

interface RenderLogTestBase {
  fun getTestFile(testName: String, ext: String): File

  private fun getRenderFile(testName: String) = getTestFile(testName, "render.txt")
  private fun getLogFile(testName: String) = getTestFile(testName, "log.txt")

  fun check(testName: String, file: UFile) {
    check(testName, file, true)
  }

  fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
    val renderFile = getRenderFile(testName)
    val logFile = getLogFile(testName)

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
          Assert.assertTrue("Wrong null-parent of ${node.javaClass} '${node.psi?.text?.lineSequence()?.firstOrNull()}'",
                            parentStack.empty())
        }
        else {
          Assert.assertEquals("Wrong parent of ${node.javaClass} '${node.psi?.text?.lineSequence()?.firstOrNull()}'", parentStack.peek(),
                              parent)
        }
        node.psi?.let {
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


    file.psi.accept(object : PsiRecursiveElementVisitor() {
      override fun visitElement(element: PsiElement) {
        val uElement = ServiceManager.getService(element.project, UastContext::class.java)
          .convertElementWithParent(element, null)
        val expectedParents = parentMap[element]
        if (expectedParents != null) {
          TestCase.assertNotNull("Expected to be able to convert PSI element $element", uElement)
          val parents = generateSequence(uElement!!.uastParent) { it.uastParent }.joinToString { it.asLogString() }
          TestCase.assertEquals(
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
        if (node is PsiElement) {
          val uElement = node.psi.toUElement()!!
          TestCase.assertEquals("getContainingUFile should be equal to source for ${uElement.javaClass}",
                                this@checkContainingFileForAllElements,
                                uElement.getContainingUFile())
        }

        val anchorPsi = (node as? UDeclaration)?.uastAnchor?.psi
        if (anchorPsi != null) {
          TestCase.assertEquals(anchorPsi.containingFile, node.sourcePsiElement!!.containingFile!!)
        }

        return false
      }
    })
  }

}