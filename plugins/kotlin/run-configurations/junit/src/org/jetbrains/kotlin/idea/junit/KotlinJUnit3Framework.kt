// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.junit

import com.intellij.execution.junit.JUnit3Framework
import com.intellij.execution.junit.JUnitUtil
import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.lang.Language
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfType
import com.intellij.util.ThreeState
import com.intellij.util.ThreeState.*
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.stubindex.KotlinClassShortNameIndex
import org.jetbrains.kotlin.idea.testIntegration.framework.AbstractKotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtClassOrObject
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework.Companion.asKtNamedFunction
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class KotlinJUnit3Framework: JUnit3Framework(), KotlinPsiBasedTestFramework {
    private val psiBasedDelegate = object : AbstractKotlinPsiBasedTestFramework() {
        override val markerClassFqn: String
            get() = JUnitUtil.TEST_CASE_CLASS

        override val disabledTestAnnotation: String
            get() = throw UnsupportedOperationException("JUnit3 does not support Ignore methods")

        override val allowTestMethodsInObject: Boolean
            get() = true // suite methods can be static

        override fun checkTestClass(declaration: KtClassOrObject): ThreeState =
            when (val checkState = super.checkTestClass(declaration)) {
                UNSURE -> CachedValuesManager.getCachedValue(declaration) {
                    CachedValueProvider.Result.create(
                        checkJUnit3TestClass(declaration),
                        OuterModelsModificationTrackerManager.getTracker(declaration.project)
                    )
                }

                else -> checkState
            }

        override fun isTestMethod(declaration: KtNamedFunction): Boolean {
            if (!super.isTestMethod(declaration)) return false
            if (declaration.name?.startsWith("test") != true) return false
            val classOrObject = declaration.getParentOfType<KtClassOrObject>(true) ?: return false
            if (classOrObject is KtClass && classOrObject.isInner()) return false

            return isTestClass(classOrObject)
        }

        override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean = false

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

        private fun checkJUnit3TestClass(declaration: KtClassOrObject): ThreeState {
            if (!isFrameworkAvailable(declaration)) return NO
            val name = declaration.name
            if (name == null) return NO
            return checkJUnit3TestClass(
                declaration,
                PsiShortNamesCache.getInstance(declaration.project).withoutLanguages(KotlinLanguage.INSTANCE),
                declaration.resolveScope,
                mutableSetOf(name)
            )
        }

        private fun checkJUnit3TestClass(
            declaration: KtClassOrObject,
            shortNamesCache: PsiShortNamesCache,
            resolveScope: GlobalSearchScope,
            visitedShortNames: MutableSet<String>
        ): ThreeState {
            if (declaration.isPrivate()) return NO
            if (declaration is KtClass && declaration.isInner()) return NO
            val objects = if (declaration is KtObjectDeclaration) listOf(declaration) else declaration.companionObjects
            if (objects.flatMap { it.declarations }.filterIsInstance<KtNamedFunction>().any { it.name == "suite" }) {
                return UNSURE // suites don't need to extend TestClass
            }
            if (declaration is KtObjectDeclaration) return NO // private constructor can't be instantiated
            val superTypeListEntries = declaration.superTypeListEntries
            for (superTypeEntry in superTypeListEntries) {
                if (superTypeEntry is KtSuperTypeCallEntry) {
                    val containingFile = declaration.containingKtFile
                    val constructorReferenceExpression = superTypeEntry.calleeExpression.constructorReferenceExpression
                    val typeReference = constructorReferenceExpression?.parentOfType<KtTypeReference>()
                    val superName = typeReference?.getTypeText()

                    // circular dependency detected
                    if (superName == null || !visitedShortNames.add(superName)) return NO

                    if (checkNameMatch(containingFile, TEST_CLASS_FQN, superName)) {
                        return YES
                    }

                    containingFile.declarations.firstOrNull { it is KtClassOrObject && it.name == superName }?.let {
                        return checkJUnit3TestClass(it as KtClassOrObject, shortNamesCache, resolveScope, visitedShortNames)
                    }

                    val classOrObjects = KotlinClassShortNameIndex.get(superName, declaration.project, resolveScope)
                    for (classOrObject in classOrObjects) {
                        val fqName = classOrObject.fqName ?: continue
                        if (checkNameMatch(containingFile, setOf(fqName.asString()), superName)) {
                            return checkJUnit3TestClass(classOrObject, shortNamesCache, resolveScope, visitedShortNames)
                        }
                    }

                    for (psiClass in shortNamesCache.getClassesByName(superName, resolveScope)) {
                        val qualifiedName = psiClass.qualifiedName ?: continue
                        if (checkNameMatch(containingFile, setOf<@NlsSafe String>(qualifiedName), superName)) {
                            return checkJUnit3TestClass(psiClass, shortNamesCache, resolveScope, visitedShortNames)
                        }
                    }
                    // it could be the only super class
                    return UNSURE
                }
            }
            return NO
        }

        private fun checkJUnit3TestClass(
            psiClass: PsiClass,
            shortNamesCache: PsiShortNamesCache,
            resolveScope: GlobalSearchScope,
            visitedShortNames: MutableSet<String>
        ): ThreeState {
            if (psiClass is KtLightElement<*, *>) {
                val ktClassOrObject = psiClass.kotlinOrigin as? KtClassOrObject ?: return NO
                return checkJUnit3TestClass(ktClassOrObject, shortNamesCache, resolveScope, visitedShortNames)
            }
            val psiJavaFile = psiClass.containingFile as? PsiJavaFile ?: return NO
            val superClass = psiClass.superClass ?: return NO
            // circular dependency detected
            val superShortName = superClass.name
            if (superShortName == null || !visitedShortNames.add(superShortName)) return NO
            if (checkNameMatch(psiJavaFile, TEST_CLASS_FQN, superShortName)) {
                return YES
            }

            return checkJUnit3TestClass(superClass, shortNamesCache, resolveScope, visitedShortNames)
        }

        private fun checkNameMatch(file: PsiJavaFile, fqNames: Set<String>, shortName: String): Boolean {
            if (shortName in fqNames || "${file.packageName}.$shortName" in fqNames) return true
            val importStatements = (file.importList ?: ((file as? ClsFileImpl)?.decompiledPsiFile as? PsiJavaFile)?.importList)?.importStatements ?: return false
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

    override fun checkTestClass(declaration: KtClassOrObject): ThreeState = psiBasedDelegate.checkTestClass(declaration)

    override fun isTestClass(clazz: PsiElement): Boolean =
        when (val checkTestClass = checkTestClass(clazz)) {
            UNSURE -> super.isTestClass(clazz)
            else -> checkTestClass == YES
        }

    override fun findSetUpMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findSetUpMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findSetUp)
        }

    override fun findTearDownMethod(clazz: PsiElement): PsiElement? =
        when (checkTestClass(clazz)) {
            UNSURE -> super.findTearDownMethod(clazz)
            NO -> null
            else -> clazz.asKtClassOrObject()?.let(psiBasedDelegate::findTearDown)
        }

    override fun isIgnoredMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isIgnoredMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isIgnoredMethod) ?: false
        }

    override fun isTestMethod(element: PsiElement?): Boolean =
        when (checkTestClass(element)) {
            UNSURE -> super.isTestMethod(element)
            NO -> false
            else -> element.asKtNamedFunction()?.let(psiBasedDelegate::isTestMethod) ?: false
        }

    override fun getLanguage(): Language = KotlinLanguage.INSTANCE

    override fun isTestMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isTestMethod(declaration)

    override fun isIgnoredMethod(declaration: KtNamedFunction): Boolean =
        psiBasedDelegate.isIgnoredMethod(declaration)

}

private val TEST_CLASS_FQN = setOf(JUnitUtil.TEST_CASE_CLASS)