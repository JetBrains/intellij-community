// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.platform.testintegration

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

abstract class AbstractLightTestFramework: LightTestFramework {

    override fun qualifiedName(namedDeclaration: KtNamedDeclaration): String? =
        when(namedDeclaration) {
            is KtClassOrObject -> namedDeclaration.fqName?.asString()
            is KtNamedFunction -> namedDeclaration.containingClassOrObject?.fqName?.asString()
            else -> null
        }

    protected val framework: TestFramework?
        get() = findTestFrameworkByName(name)

    protected fun internalDetectFramework(namedDeclaration: KtNamedDeclaration): LightTestFrameworkResult {
        val testFramework = framework
        if (testFramework == null) return UnsureLightTestFrameworkResult

        return when (namedDeclaration) {
            is KtClassOrObject -> {
                if (isNotACandidateFastCheck(namedDeclaration)) {
                    return NoLightTestFrameworkResult
                }

                when (isAUnitTestClass(namedDeclaration)) {
                    true -> ResolvedLightTestFrameworkResult(testFramework)
                    false -> UnsureLightTestFrameworkResult
                    null -> NoLightTestFrameworkResult
                }
            }
            is KtNamedFunction -> {
                // fast check if KtNamedFunction can't be a test function
                if (isNotACandidateFastCheck(namedDeclaration)) {
                    return NoLightTestFrameworkResult
                }

                when (namedDeclaration.getParentOfType<KtClassOrObject>(true)?.run { isAUnitTestClass(this) }) {
                    true -> when (isAUnitTestMethod(namedDeclaration)) {
                        true -> ResolvedLightTestFrameworkResult(testFramework)
                        false -> UnsureLightTestFrameworkResult
                        null -> NoLightTestFrameworkResult
                    }
                    false -> UnsureLightTestFrameworkResult
                    null -> NoLightTestFrameworkResult
                }

            }
            else -> UnsureLightTestFrameworkResult
        }
    }

    // fast check if KtClassOrObject can't be a test class
    protected open fun isNotACandidateFastCheck(classOrObject: KtClassOrObject) =
        with(classOrObject) {
            isPrivate() ||
                    isAnnotation() ||
                    // no super class, no annotations and no methods
                    annotationEntries.isEmpty() &&
                    superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().isEmpty() &&
                    declarations.filterIsInstance<KtNamedFunction>().none { it.isPublic }
        }

    protected open fun isNotACandidateFastCheck(namedFunction: KtNamedFunction) =
        with(namedFunction) {
            isTopLevel
                    || !isPublic
                    || isAbstract()
                    || isLocal
                    || isExtensionDeclaration()
                    || getStrictParentOfType<KtObjectDeclaration>()?.isObjectLiteral() == true
        }

    protected fun detectFramework(ktFile: KtFile): LightTestFrameworkResult {
        val testFramework = framework
        if (testFramework == null) return UnsureLightTestFrameworkResult

        return CachedValuesManager.getCachedValue(ktFile) {
            CachedValueProvider.Result
                .create(
                    {
                        val anyNotUnitTestClass = ktFile.declarations.filterIsInstance<KtClassOrObject>().any {
                            !isNotACandidateFastCheck(it) && isAUnitTestClass(it) == false
                        }
                        if (anyNotUnitTestClass) return@create UnsureLightTestFrameworkResult

                        val anyUnitTestClass = ktFile.declarations.filterIsInstance<KtClassOrObject>().any {
                            !isNotACandidateFastCheck(it) && isAUnitTestClass(it) == true
                        }

                        if (anyUnitTestClass) ResolvedLightTestFrameworkResult(testFramework) else UnsureLightTestFrameworkResult
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
        }.invoke()
    }

    abstract fun isAUnitTestClass(namedDeclaration: KtClassOrObject): Boolean?

    abstract fun isAUnitTestMethod(namedDeclaration: KtNamedFunction): Boolean?

    protected fun hasClass(qualifiedName: String, namedDeclaration: KtNamedDeclaration) =
        JavaPsiFacade.getInstance(namedDeclaration.project)
            .findClass(qualifiedName, namedDeclaration.resolveScope) != null

    protected fun KtFile.isResolvable(fqName: String, shortName: String): Boolean {
        return fqName.endsWith(shortName) && importDirectives.filter { it.isValidImport }.any {
            // TODO: aliases
            val name = it.importedFqName?.asString()
            name != null &&
                    fqName == if (!it.isAllUnder) name else "$name.$shortName"
        }
    }

    private fun findTestFrameworkByName(name: String): TestFramework? =
        TestFramework.EXTENSION_NAME.extensionList.firstOrNull{
            it.name == name
        }

    protected inline fun <reified T : Any, E: KtElement> cached(element: E, crossinline function: (E) -> T?): T? {
        return CachedValuesManager.getCachedValue(element) {
            CachedValueProvider.Result.create({ function(element) }, PsiModificationTracker.MODIFICATION_COUNT)
        }.invoke()
    }
}