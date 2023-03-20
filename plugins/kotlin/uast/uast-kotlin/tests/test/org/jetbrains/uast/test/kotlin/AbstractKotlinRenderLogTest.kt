// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.tree.LeafPsiElement
import junit.framework.TestCase.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.assertedCast
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.kotlin.KotlinUastLanguagePlugin
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.test.common.kotlin.checkDescriptorsLeak
import org.jetbrains.uast.test.common.kotlin.RenderLogTestBase
import org.jetbrains.uast.visitor.UastVisitor
import org.junit.Assert
import java.io.File
import java.util.*

interface AbstractKotlinRenderLogTest : RenderLogTestBase {
    override fun getTestFile(testName: String, ext: String) =
        File(File(TEST_KOTLIN_MODEL_DIR, testName).canonicalPath + '.' + ext)

    override fun check(testName: String, file: UFile) {
        check(testName, file, true)
    }

    fun check(testName: String, file: UFile, checkParentConsistency: Boolean) {
        super.check(testName, file)

        if (checkParentConsistency) {
            checkParentConsistency(file)
        }

        file.checkContainingFileForAllElements()
        file.checkJvmDeclarationsImplementations()
        file.checkDescriptorsLeak()
    }

    private fun checkParentConsistency(file: UFile) {
        val parentMap = mutableMapOf<PsiElement, MutableMap<String, String>>()

        operator fun MutableMap<PsiElement, MutableMap<String, String>>.get(psi: PsiElement, cls: String?) =
            parentMap.getOrPut(psi) { mutableMapOf() }[cls]

        operator fun MutableMap<PsiElement, MutableMap<String, String>>.set(psi: PsiElement, cls: String, v: String) {
            parentMap.getOrPut(psi) { mutableMapOf() }[cls] = v
        }

        file.accept(object : UastVisitor {
            private val parentStack = Stack<UElement>()

            override fun visitElement(node: UElement): Boolean {
                val parent = node.uastParent
                if (parent == null) {
                    Assert.assertTrue("Wrong parent of $node", parentStack.empty())
                }
                else {
                    Assert.assertEquals("Wrong parent of $node", parentStack.peek(), parent)
                }
                node.sourcePsiElement?.let {
                    parentMap[it, node.asLogString()] = parentStack.reversed().joinToString { it.asLogString() }
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
                val uElement = KotlinUastLanguagePlugin().convertElementWithParent(element, null)
                val expectedParents = parentMap[element, uElement?.asLogString()]
                if (expectedParents != null) {
                    assertNotNull("Expected to be able to convert PSI element $element", uElement)
                    val parents = generateSequence(uElement!!.uastParent) { it.uastParent }.joinToString { it.asLogString() }
                    assertEquals("Inconsistent parents for ${uElement.asRenderString()}(${uElement.asLogString()})(${uElement.javaClass}) (converted from $element[${element.text}])", expectedParents, parents)
                }
                super.visitElement(element)
            }
        })
    }

    private fun UFile.checkContainingFileForAllElements() {
        accept(object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {
                if (node is PsiElement) {
                    node.containingFile.assertedCast<KtFile> { "containingFile should be KtFile for ${node.asLogString()}" }
                }

                val anchorPsi = (node as? UAnchorOwner)?.uastAnchor?.sourcePsi
                if (anchorPsi != null) {
                    anchorPsi.containingFile.assertedCast<KtFile> { "uastAnchor.containingFile should be KtFile for ${node.asLogString()}" }
                }

                return false
            }
        })
    }

    private fun UFile.checkDescriptorsLeak() {
        accept(object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {
                checkDescriptorsLeak(node)
                return false
            }
        })
    }

    private fun UFile.checkJvmDeclarationsImplementations() {
        accept(object : UastVisitor {
            override fun visitElement(node: UElement): Boolean {

                if (node is UAnchorOwner) {
                    node.uastAnchor?.let { visitElement(it) }
                }

                node.sourcePsi?.let {
                    assertTrue("sourcePsi should be physical but ${it.javaClass} found for [${it.text}] " +
                               "for ${node.javaClass}->${node.uastParent?.javaClass}", it is LeafPsiElement || it is KtElement|| it is LeafPsiElement)
                }
                node.javaPsi?.let {
                    assertTrue("javaPsi should be light but ${it.javaClass} found for [${it.text}] " +
                               "for ${node.javaClass}->${node.uastParent?.javaClass}", it !is KtElement)
                }

                return false
            }
        })
    }
}
