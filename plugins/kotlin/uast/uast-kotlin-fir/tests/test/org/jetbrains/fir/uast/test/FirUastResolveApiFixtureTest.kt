// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.fir.uast.test

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.KtAssert
import org.jetbrains.uast.test.common.kotlin.UastResolveApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastResolveApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastResolveApiFixtureTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    private val whitelist : Set<String> = setOf(
        // TODO: return type for ambiguous call
        "MultiResolveJavaAmbiguous",
        // TODO: return type for ambiguous call
        "MultiConstructorResolve",
        // TODO: multiResolve
        "MultiInvokableObjectResolve",
        // TODO: resolve annotation param to annotation ctor ??
        "ResolveCompiledAnnotation",
    )

    private fun isExpectedToFail(key: String): Boolean {
        return key in whitelist
    }

    private fun doCheckRunner(key: String, checkCallback: () -> Unit) {
        try {
            checkCallback()
        } catch (e: AssertionError) {
            if (isExpectedToFail(key))
                return
            else
                throw e
        }
        if (isExpectedToFail(key)) {
            KtAssert.fail("This test seems not fail anymore. Drop this from the white-list and re-run the test.")
        }
    }

    private fun doCheck(key: String, checkCallback: (JavaCodeInsightTestFixture, Project) -> Unit) {
        doCheckRunner(key) {
            checkCallback(myFixture, project)
        }
    }

    private fun doCheck(key: String, checkCallback: (JavaCodeInsightTestFixture) -> Unit) {
        doCheckRunner(key) {
            checkCallback(myFixture)
        }
    }

    fun testResolveStringFromUast() {
        checkResolveStringFromUast(myFixture, project)
    }

    fun testMultiResolve() {
        checkMultiResolve(myFixture)
    }

    fun testMultiResolveJava() {
        checkMultiResolveJava(myFixture)
    }

    fun testMultiResolveJavaAmbiguous() {
        doCheck("MultiResolveJavaAmbiguous", ::checkMultiResolveJavaAmbiguous)
    }

    fun testResolveFromBaseJava() {
        checkResolveFromBaseJava(myFixture)
    }

    fun testMultiResolveInClass() {
        checkMultiResolveInClass(myFixture)
    }

    fun testResolveToFacade() {
        checkResolveToFacade(myFixture)
    }

    fun testMultiConstructorResolve() {
        doCheck("MultiConstructorResolve", ::checkMultiConstructorResolve)
    }

    fun testMultiInvokableObjectResolve() {
        doCheck("MultiInvokableObjectResolve", ::checkMultiInvokableObjectResolve)
    }

    fun testMultiResolveJvmOverloads() {
        checkMultiResolveJvmOverloads(myFixture)
    }

    fun testLocalResolve_class() {
        checkLocalResolve_class(myFixture)
    }

    fun testLocalResolve_function() {
        checkLocalResolve_function(myFixture)
    }

    fun testGetJavaClass() {
        checkGetJavaClass(myFixture)
    }

    fun testResolveLocalDefaultConstructor() {
        checkResolveLocalDefaultConstructor(myFixture)
    }

    fun testResolveJavaDefaultConstructor() {
        checkResolveJavaDefaultConstructor(myFixture)
    }

    fun testResolveKotlinDefaultConstructor() {
        checkResolveKotlinDefaultConstructor(myFixture)
    }

    fun testResolveJavaClassAsAnonymousObjectSuperType() {
        checkResolveJavaClassAsAnonymousObjectSuperType(myFixture)
    }

    fun testResolveCompiledAnnotation() {
        doCheck("ResolveCompiledAnnotation", ::checkResolveCompiledAnnotation)
    }

    fun testResolveExplicitLambdaParameter() {
        checkResolveExplicitLambdaParameter(myFixture)
    }

    fun testResolveImplicitLambdaParameter() {
        checkResolveImplicitLambdaParameter(myFixture)
    }

    fun testResolveImplicitLambdaParameter_binary() {
        checkResolveImplicitLambdaParameter_binary(myFixture)
    }

    fun testNullityOfResolvedLambdaParameter() {
        checkNullityOfResolvedLambdaParameter(myFixture)
    }

    fun testResolveSyntheticMethod() {
        checkResolveSyntheticMethod(myFixture)
    }

    fun testMapFunctions() {
        checkMapFunctions(myFixture)
    }

    fun testListIterator() {
        checkListIterator(myFixture)
    }

    fun testStringJVM() {
        checkStringJVM(myFixture)
    }

    fun testArgumentMappingDefaultValue() {
        checkArgumentMappingDefaultValue(myFixture)
    }

    fun testArgumentMappingExtensionFunction() {
        checkArgumentMappingExtensionFunction(myFixture)
    }

    fun testArgumentMappingVararg() {
        checkArgumentMappingVararg(myFixture)
    }

    fun testArgumentMappingOOBE() {
        checkArgumentMappingOOBE(myFixture)
    }

    fun testArgumentMappingSAM() {
        checkArgumentMappingSAM(myFixture)
    }

    fun testArgumentMappingSAM_methodReference() {
        checkArgumentMappingSAM_methodReference(myFixture)
    }

    fun testSyntheticEnumMethods() {
        checkSyntheticEnumMethods(myFixture)
    }

    fun testArrayAccessOverloads() {
        checkArrayAccessOverloads(myFixture)
    }

    fun testPrimitiveOperator() {
        checkPrimitiveOperator(myFixture)
    }

    fun testOperatorOverloads() {
        checkOperatorOverloads(myFixture)
    }

    fun testOperatorMultiResolvable() {
        checkOperatorMultiResolvable(myFixture)
    }

    fun testResolveSyntheticJavaPropertyCompoundAccess() {
        checkResolveSyntheticJavaPropertyCompoundAccess(myFixture)
    }

    fun testResolveSyntheticJavaPropertyAccessor_setter() {
        checkResolveSyntheticJavaPropertyAccessor_setter(myFixture)
    }

    fun testResolveSyntheticJavaPropertyAccessor_getter() {
        checkResolveSyntheticJavaPropertyAccessor_getter(myFixture)
    }

    fun testResolveKotlinPropertyAccessor() {
        checkResolveKotlinPropertyAccessor(myFixture)
    }

    fun testResolveBackingField() {
        checkResolveBackingField(myFixture)
    }

    fun testResolveBackingFieldInCompanionObject() {
        checkResolveBackingFieldInCompanionObject(myFixture)
    }

    fun testResolveStaticImportFromObject() {
        checkResolveStaticImportFromObject(myFixture)
    }

    fun testResolveToSubstituteOverride() {
        checkResolveToSubstituteOverride(myFixture)
    }

    fun testResolveEnumEntrySuperType() {
        checkResolveEnumEntrySuperType(myFixture)
    }

    fun testResolveFunInterfaceSamWithValueClassInSignature() {
        checkResolveFunInterfaceSamWithValueClassInSignature(myFixture, isK2 = true)
    }

    fun testResolveLambdaInvoke() {
        checkLambdaInvoke(myFixture)
    }

    fun testIsMethodCallCanBeOneOfInvoke() {
        checkIsMethodCallCanBeOneOfInvoke(myFixture)
    }

    fun testIsMethodCallCanBeOneOfRegularMethod() {
        checkIsMethodCallCanBeOneOfRegularMethod(myFixture)
    }

    fun testCheckIsMethodCallCanBeOneOfConstructor() {
        checkIsMethodCallCanBeOneOfConstructor(myFixture)
    }

    fun testIsMethodCallCanBeOneOfImportAliased() {
        checkIsMethodCallCanBeOneOfImportAliased(myFixture)
    }

    fun testParentOfParameterOfCatchClause() {
        checkParentOfParameterOfCatchClause(myFixture)
    }

    fun testCompanionConstantAsVarargAnnotationValue() {
        checkCompanionConstantAsVarargAnnotationValue(myFixture)
    }

    fun testResolveThisExpression() {
        checkResolveThisExpression(myFixture)
    }

    fun testResolveThisExpressionAsLambdaReceiver() {
        checkResolveThisExpressionAsLambdaReceiver(myFixture)
    }

    fun testResolvePropertiesInCompanionObjectFromBinaryDependency() {
        checkResolvePropertiesInCompanionObjectFromBinaryDependency(myFixture)
    }

    fun testResolvePropertiesInInnerClassFromBinaryDependency() {
        checkResolvePropertiesInInnerClassFromBinaryDependency(myFixture)
    }

    fun testResolveConstructorCallFromLibrary() {
        checkResolveConstructorCallFromLibrary(myFixture)
    }

    fun testResolveTopLevelInlineReifiedFromLibrary() {
        checkResolveTopLevelInlineReifiedFromLibrary(myFixture, withJvmName = false)
    }

    fun testResolveTopLevelInlineReifiedFromLibraryWithJvmName() {
        checkResolveTopLevelInlineReifiedFromLibrary(myFixture, withJvmName = true)
    }

    fun testResolveTopLevelInlineInFacadeFromLibrary() {
        checkResolveTopLevelInlineInFacadeFromLibrary(myFixture, isK2 = true)
    }

    fun testResolveInnerInlineFromLibrary() {
        checkResolveInnerInlineFromLibrary(myFixture)
    }

    fun testResolveJvmNameOnFunctionFromLibrary() {
        checkResolveJvmNameOnFunctionFromLibrary(myFixture)
    }

    fun testResolveJvmNameOnGetterFromLibrary() {
        checkResolveJvmNameOnGetterFromLibrary(myFixture)
    }

    fun testResolveJvmNameOnSetterFromLibrary() {
        checkResolveJvmNameOnSetterFromLibrary(myFixture)
    }
}
