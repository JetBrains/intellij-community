// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.KotlinTestHelpers
import org.jetbrains.kotlin.idea.k2.navigation.AbstractKotlinNavigationToLibrarySourceTest.Companion.signatureText
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest.Companion.checkReferenceResolve
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest.Companion.readResolveData
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation
import java.io.File

abstract class AbstractMultiModuleNavigationTest: AbstractMultiModuleTest() {
    protected fun dataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataDirectory(): File {
        return File(TestMetadataUtil.getTestDataPath(this::class.java))
    }

    fun doTest(unused: String) {
        setupMppProjectFromDirStructure(dataFile())
        val actionFile = project.findFileWithCaret()
        val virtualFilePath = actionFile.virtualFile!!.toNioPath()

        val ignoreDirective = IgnoreTests.DIRECTIVES.of(pluginMode)

        IgnoreTests.runTestIfNotDisabledByFileDirective(virtualFilePath, ignoreDirective) {
            val directiveFileText = actionFile.text
            withCustomCompilerOptions(directiveFileText, project, module) {
                doNavigationFixTest(fileName())
            }
        }
    }

    private fun doNavigationFixTest(dirPath: String) {
        KotlinTestHelpers.registerChooserInterceptor(testRootDisposable)

        val actionFile = project.findFileWithCaret()
        val virtualFile = actionFile.virtualFile!!
        configureByExistingFile(virtualFile)
        val actionFileText = actionFile.text

        doSingleResolveTest(actionFileText, actionFile)
    }

    private fun doSingleResolveTest(fileText: String, psiFile: PsiFile) {
        forEachCaret { index, offset ->
            val expectedResolveData = readResolveData(fileText, getExpectedReferences(fileText, index))
            val psiReference = wrapReference(psiFile.findReferenceAt(offset))
            checkReferenceResolve(
                expectedResolveData,
                offset,
                psiReference,
                render = { this.render(it) },
                replacePlaceholders = false
            )
        }
    }

    open fun wrapReference(reference: PsiReference?): PsiReference? = reference

    private fun forEachCaret(action: (index: Int, offset: Int) -> Unit) {
        val offsets = editor.caretModel.allCarets.map { it.offset }
        val singleCaret = offsets.size == 1
        for ((index, offset) in offsets.withIndex()) {
            action(if (singleCaret) -1 else index + 1, offset)
        }
    }

    protected open fun getExpectedReferences(text: String, index: Int): List<String> {
        val additionalPrefix = "_${pluginMode.name}"
        val prefix = "REF"
        return AbstractReferenceResolveTest.Companion.getExpectedReferences(text, index, prefix + additionalPrefix).ifEmpty {
            AbstractReferenceResolveTest.Companion.getExpectedReferences(text, index, prefix)
        }
    }

    private fun render(element: PsiElement): String {
        fun StringBuilder.renderPackageName(declaration: KtNamedDeclaration) {
            if (declaration is KtClassOrObject) {
                append(declaration.fqName?.asString() ?: "<local>")
            } else {
                val classOrObject = declaration.containingClassOrObject
                if (classOrObject != null) {
                    renderPackageName(classOrObject)
                } else {
                    append("<??>")
                }
            }
        }

        return when (val navigationElement = element.navigationElement) {
            is KtNamedDeclaration -> buildString {
                append("(")
                renderPackageName(navigationElement)
                append(" @ ")
                val virtualFile = navigationElement.containingFile.virtualFile
                val jarFileSystem = virtualFile.fileSystem as? JarFileSystem
                append(jarFileSystem?.let {
                    val root = VfsUtilCore.getRootFile(virtualFile)
                    "${it.protocol}://${root.name}${JarFileSystem.JAR_SEPARATOR}${VfsUtilCore.getRelativeLocation(virtualFile, root)}"
                } ?: virtualFile.name)
                append(")")
                append(navigationElement.signatureText())
            }
            else -> {
                element.renderAsGotoImplementation()
            }
        }
    }

}