// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace
import org.jetbrains.kotlin.test.util.renderAsGotoImplementation

abstract class AbstractKotlinNavigationToLibrarySourceTest : AbstractReferenceResolveTest() {

    override fun getProjectDescriptor(): KotlinLightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

    override fun performAdditionalResolveChecks(results: List<PsiElement>) {
        for (result in results) {
            val navigationElement = result.navigationElement
            val module = navigationElement.getKaModule(project, useSiteModule = null)
            UsefulTestCase.assertTrue(
                "reference should be resolved to the psi element from ${KaLibrarySourceModule::class} but was resolved to ${module::class}",
                module is KaLibrarySourceModule
            )
        }
    }

    override val replacePlaceholders: Boolean = false

    override fun render(element: PsiElement): String = renderNavigationElement(element)

    override fun tearDown() {
        runAll(
            { project.invalidateCaches() },
            { super.tearDown() },
        )
    }

    companion object {
        fun KtNamedDeclaration.signatureText(): String {
            val firstElement = children.firstOrNull { it !is PsiComment && it !is PsiWhiteSpace } ?: return nameAsSafeName.asString()
            val endOffset = when (this) {
                is KtNamedFunction -> typeReference ?: valueParameterList?.rightParenthesis
                is KtProperty -> typeReference ?: equalsToken?.getPrevSiblingIgnoringWhitespace()
                is KtClassOrObject -> primaryConstructor?.valueParameterList?.rightParenthesis
                    ?: getColon()?.getPrevSiblingIgnoringWhitespace()
                    ?: body?.getPrevSiblingIgnoringWhitespace()
                else -> lastChild
            }
            return containingFile.text.subSequence(firstElement.startOffset, endOffset?.endOffset ?: lastChild!!.endOffset).toString()
                .replace("\n", " ")
        }

        fun renderNavigationElement(element: PsiElement): String {
            fun StringBuilder.renderPackageName(declaration: KtNamedDeclaration) {
                if (declaration is KtClassOrObject) {
                    append(declaration.fqName?.asString() ?: "<local>")
                } else {
                    val classOrObject = declaration.containingClassOrObject
                    if (classOrObject != null) {
                        renderPackageName(classOrObject)
                    } else {
                        append(declaration.fqName?.asString() ?: "<??>")
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
                    append(") ")
                    append(navigationElement.signatureText())
                }
                else -> {
                    element.renderAsGotoImplementation()
                }
            }
        }

    }
}