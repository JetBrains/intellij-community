package org.jetbrains.kotlin.idea.run

import com.intellij.openapi.components.service
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.*

/**
 * A service for detecting entry points (like "main" function) in classes and objects.
 *
 * Abstracts away the usage of the different Kotlin frontends (detecting "main" requires resolve).
 */
interface KotlinMainFunctionLocatingService {
    fun isMain(function: KtNamedFunction): Boolean

    fun hasMain(declarations: List<KtDeclaration>): Boolean

    /**
     * Few convenience functions to avoid retrieving service by hand.
     */
    companion object {
        private fun getMainFunCandidates(psiClass: PsiClass): Collection<KtNamedFunction> {
            return psiClass.allMethods.map { method: PsiMethod ->
                if (method !is KtLightMethod) return@map null
                if (method.getName() != "main") return@map null
                val declaration =
                    method.kotlinOrigin
                if (declaration is KtNamedFunction) declaration else null
            }.filterNotNull()
        }

        fun findMainInClass(psiClass: PsiClass): KtNamedFunction? {
            val mainLocatingService = service<KotlinMainFunctionLocatingService>()

            return getMainFunCandidates(psiClass).find { mainLocatingService.isMain(it) }
        }

        fun getEntryPointContainer(locationElement: PsiElement): KtDeclarationContainer? {
            val mainLocatingService = service<KotlinMainFunctionLocatingService>()

            val psiFile = locationElement.containingFile
            if (!(psiFile is KtFile && ProjectRootsUtil.isInProjectOrLibSource(psiFile))) return null

            var currentElement = locationElement.declarationContainer(false)
            while (currentElement != null) {
                var entryPointContainer = currentElement
                if (entryPointContainer is KtClass) {
                    entryPointContainer = entryPointContainer.companionObjects.singleOrNull()
                }
                if (entryPointContainer != null && mainLocatingService.hasMain(entryPointContainer.declarations)) return entryPointContainer
                currentElement = (currentElement as PsiElement).declarationContainer(true)
            }

            return null
        }

        private fun PsiElement.declarationContainer(strict: Boolean): KtDeclarationContainer? {
            val element = if (strict)
              PsiTreeUtil.getParentOfType(this, KtClassOrObject::class.java, KtFile::class.java)
            else
              PsiTreeUtil.getNonStrictParentOfType(this, KtClassOrObject::class.java, KtFile::class.java)
            return element
        }
    }
}