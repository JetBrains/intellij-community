package org.jetbrains.kotlin.idea.platform.testintegration

import com.intellij.execution.junit.JUnitUtil
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class JUnit4LightTestFramework: AbstractLightTestFramework() {

    override val name: String = "JUnit4"

    override fun detectFramework(namedDeclaration: KtNamedDeclaration): LightTestFrameworkResult {
        val testFramework = framework
        if (testFramework == null ||
            !hasClass(JUnitUtil.TEST_ANNOTATION, namedDeclaration) ||
            !hasClass(JUnitUtil.BEFORE_ANNOTATION_NAME, namedDeclaration) ||
            // presence of junit 5
            hasClass(JUnitUtil.TEST5_ANNOTATION, namedDeclaration)
        ) return UnsureLightTestFrameworkResult

        return internalDetectFramework(namedDeclaration)
    }

    override fun isAUnitTestClass(namedDeclaration: KtClassOrObject): Boolean? {
        return cached(namedDeclaration) { isJUnit4TestClass(namedDeclaration) }
    }

    override fun isAUnitTestMethod(namedDeclaration: KtNamedFunction): Boolean? {
        if (namedDeclaration.annotationEntries.isEmpty()) return null

        return isJUnit4TestMethod(namedDeclaration)
    }

    private fun getTopmostClass(psiClass: KtClassOrObject): KtClassOrObject? {
        var topLevelClass: KtClassOrObject? = psiClass
        while (topLevelClass != null && !topLevelClass.isTopLevel()) {
            topLevelClass = topLevelClass.getParentOfType<KtClassOrObject>(true)
        }
        return topLevelClass
    }

    private fun isJUnit4TestClass(ktClassOrObject: KtClassOrObject): Boolean? {
        getTopmostClass(ktClassOrObject)?.let { topLevelClass ->
            topLevelClass.annotationEntries.find { it.isFqName(JUnitUtil.RUN_WITH) }?.let { return true }
        }
        return ktClassOrObject.declarations.filterIsInstance<KtNamedFunction>().filterNot { isNotACandidateFastCheck(it) }
            .any { isJUnit4TestMethod(it) }.takeIf { it }
    }

    private fun KtAnnotated.isAnnotated(fqName: String): Boolean = annotationEntries.any {
        it.isFqName(fqName)
    }

    private fun isJUnit4TestMethod(method: KtNamedFunction): Boolean {
        return method.isAnnotated(JUnitCommonClassNames.ORG_JUNIT_TEST) || method.isAnnotated(KOTLIN_TEST_TEST)
    }

    private fun KtAnnotationEntry?.isFqName(fqName: String): Boolean {
        val shortName = this?.shortName?.asString() ?: return false

        return containingKtFile.isResolvable(fqName, shortName)
    }

    companion object {
        private const val KOTLIN_TEST_TEST = "kotlin.test.Test"
    }
}