// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit3Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.Language
import com.intellij.lang.OuterModelsModificationTrackerManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class KotlinJUnit3Framework: JUnit3Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String
            get() = JUnitUtil.TEST_CASE_CLASS

        override val disabledTestAnnotation: String
            get() = "org.junit.Ignore"

        override fun isTestClass(declaration: KtClassOrObject): Boolean {
            return super.isTestClass(declaration) && CachedValuesManager.getCachedValue(declaration) {
                CachedValueProvider.Result.create(isJUnit3TestClass(declaration), OuterModelsModificationTrackerManager.getInstance(declaration.project).tracker)
            }
        }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            if (!super.isTestMethod(declaration)) return false
            if (declaration.name?.startsWith("test") != true) return false
            val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
            if (classOrObject is KtClass && classOrObject.isInner()) return false

            return isTestClass(classOrObject)
        }

        override fun findSetUp(classOrObject: KtClassOrObject): KtNamedFunction? =
            findFunctionWithName(classOrObject.takeIf { isTestClass(it) }, "setUp")

        override fun findTearDown(classOrObject: KtClassOrObject): KtNamedFunction? =
            findFunctionWithName(classOrObject.takeIf { isTestClass(it) }, "tearDown")

        private fun findFunctionWithName(classOrObject: KtClassOrObject?, name: String): KtNamedFunction? {
            if (classOrObject == null) return null
            for (declaration in classOrObject.declarations) {
                if (declaration is KtNamedFunction && declaration.name == name) {
                    return declaration
                }
            }
            return null
        }

        private fun isJUnit3TestClass(declaration: KtClassOrObject): Boolean {
            if (!isFrameworkAvailable(declaration)) return false
            val name = declaration.name
            if (name == null) return false
            return isJUnit3TestClass(
                declaration,
                PsiShortNamesCache.getInstance(declaration.project),
                declaration.resolveScope,
                mutableSetOf(name)
            )
        }

        private fun isJUnit3TestClass(
            declaration: KtClassOrObject,
            shortNamesCache: PsiShortNamesCache,
            resolveScope: GlobalSearchScope,
            visitedShortNames: MutableSet<String>
        ): Boolean {
            if (declaration is KtClass && declaration.isInner()) return false

            val superTypeListEntries = declaration.superTypeListEntries
            for (superTypeEntry in superTypeListEntries) {
                if (superTypeEntry is KtSuperTypeCallEntry) {
                    val containingFile = declaration.containingKtFile
                    val superShortName = superTypeEntry.calleeExpression.constructorReferenceExpression?.getReferencedName()

                    // circular dependency detected
                    if (superShortName == null || !visitedShortNames.add(superShortName)) return false

                    if (checkNameMatch(containingFile, CLASS_ANNOTATION_FQN, superShortName)) {
                        return true
                    }

                    containingFile.declarations.firstOrNull { it is KtClassOrObject && it.name == superShortName }?.let {
                        return isJUnit3TestClass(it as KtClassOrObject, shortNamesCache, resolveScope, visitedShortNames)
                    }

                    val classOrObjects = KotlinClassShortNameIndex.get(superShortName, declaration.project, resolveScope)
                    for (classOrObject in classOrObjects) {
                        val fqName = classOrObject.fqName ?: continue
                        if (checkNameMatch(containingFile, setOf(fqName.asString()), superShortName)) {
                            return isJUnit3TestClass(classOrObject, shortNamesCache, resolveScope, visitedShortNames)
                        }
                    }

                    for (psiClass in shortNamesCache.getClassesByName(superShortName, resolveScope)) {
                        val qualifiedName = psiClass.qualifiedName ?: continue
                        if (checkNameMatch(containingFile, setOf<@NlsSafe String>(qualifiedName), superShortName)) {
                            return isJUnit3TestClass(psiClass, shortNamesCache, resolveScope, visitedShortNames)
                        }
                    }
                }
            }
            return false
        }

        private fun isJUnit3TestClass(
            psiClass: PsiClass,
            shortNamesCache: PsiShortNamesCache,
            resolveScope: GlobalSearchScope,
            visitedShortNames: MutableSet<String>
        ): Boolean {
            if (psiClass is KtLightElement<*, *>) {
                val ktClassOrObject = psiClass.kotlinOrigin as? KtClassOrObject ?: return false
                return isJUnit3TestClass(ktClassOrObject, shortNamesCache, resolveScope, visitedShortNames)
            }
            val psiJavaFile = psiClass.containingFile as? PsiJavaFile ?: return false
            val superClass = psiClass.superClass ?: return false
            // circular dependency detected
            val superShortName = superClass.name
            if (superShortName == null || !visitedShortNames.add(superShortName)) return false
            if (checkNameMatch(psiJavaFile, CLASS_ANNOTATION_FQN, superShortName)) {
                return true
            }

            return isJUnit3TestClass(superClass, shortNamesCache, resolveScope, visitedShortNames)
        }

        private fun checkNameMatch(file: PsiJavaFile, fqNames: Set<String>, shortName: String): Boolean {
            if ("${file.packageName}.$shortName" in fqNames) return true
            val importStatements = file.importList?.importStatements ?: return false
            for (importStatement in importStatements) {
                val importedFqName = importStatement.qualifiedName ?: continue
                if (importedFqName.endsWith(".$shortName") && importedFqName in fqNames) {
                    return true
                }
            }

            return false
        }
    }

    override fun responsibleFor(declaration: KtNamedDeclaration): Boolean =
        psiBasedDelegate.responsibleFor(declaration)

    override fun isTestClass(clazz: PsiElement): Boolean {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return false
        return psiBasedDelegate.isTestClass(ktClassOrObject)
    }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return psiBasedDelegate.findSetUp(ktClassOrObject)
    }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? {
        val ktClassOrObject = clazz.asKtClassOrObject() ?: return null
        return psiBasedDelegate.findTearDown(ktClassOrObject)
    }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(psiBasedDelegate::isIgnoredMethod) ?: false

    override fun isTestMethod(element: PsiElement?): Boolean =
        element.asKtNamedFunction()?.let(psiBasedDelegate::isTestMethod) ?: false

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun isTestClass(declaration: KtClassOrObject): Boolean =
        psiBasedDelegate.isTestClass(declaration)

    override fun isTestMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isTestMethod(declaration)

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isIgnoredMethod(declaration)

    private companion object {
        private val CLASS_ANNOTATION_FQN = setOf(JUnitUtil.TEST_CASE_CLASS)
    }
}
