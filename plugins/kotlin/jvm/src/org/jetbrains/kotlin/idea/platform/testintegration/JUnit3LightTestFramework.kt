// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.platform.testintegration

import com.intellij.execution.junit.JUnitUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

class JUnit3LightTestFramework: AbstractLightTestFramework() {

    override val name: String = "JUnit3"

    override fun detectFramework(namedDeclaration: KtNamedDeclaration): LightTestFrameworkResult {
        if (!hasClass(JUnitUtil.TEST_CASE_CLASS, namedDeclaration) ||
            // @Test is from junit4
            hasClass(JUnitUtil.TEST_ANNOTATION, namedDeclaration) ||
            hasClass(JUnitUtil.TEST5_ANNOTATION, namedDeclaration)
        ) return UnsureLightTestFrameworkResult

        return internalDetectFramework(namedDeclaration)
    }

    override fun isNotACandidateFastCheck(classOrObject: KtClassOrObject) =
        with(classOrObject) {
            isPrivate() ||
                    isAnnotation() ||
                    // no super class and no methods
                    superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().isEmpty()
        }

    override fun isAUnitTestClass(namedDeclaration: KtClassOrObject): Boolean? {
        return cached(namedDeclaration) {
            val superTypeListEntries =
                it.superTypeListEntries.filterIsInstance<KtSuperTypeCallEntry>().firstOrNull()
                    ?: return@cached null
            superTypeListEntries.valueArgumentList?.arguments?.isEmpty() == true &&
                    it.containingKtFile.isResolvable(
                        JUnitUtil.TEST_CASE_CLASS,
                        superTypeListEntries.calleeExpression.text
                    )
        }
    }

    private fun KtNamedFunction.isSetUpMethod(): Boolean = name == "setUp"
    private fun KtNamedFunction.isTearDownMethod(): Boolean = name == "tearDown"

    override fun isAUnitTestMethod(namedDeclaration: KtNamedFunction): Boolean? {
        if (namedDeclaration.isSetUpMethod() || namedDeclaration.isTearDownMethod()) return null
        return (namedDeclaration.name?.startsWith("test") == true &&
                namedDeclaration.getParentOfType<KtClassOrObject>(true)?.let {
                    isAUnitTestClass(it)
                } ?: false).takeIf { it }

    }
}