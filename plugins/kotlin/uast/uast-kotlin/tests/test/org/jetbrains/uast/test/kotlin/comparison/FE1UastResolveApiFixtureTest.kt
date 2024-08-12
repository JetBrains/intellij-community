// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.kotlin.comparison

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.uast.test.common.kotlin.UastResolveApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FE1UastResolveApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastResolveApiFixtureTestBase {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K1

    override fun getProjectDescriptor(): LightProjectDescriptor =
        KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

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
        checkMultiResolveJavaAmbiguous(myFixture)
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
        checkMultiConstructorResolve(myFixture, project)
    }

    fun testMultiInvokableObjectResolve() {
        checkMultiInvokableObjectResolve(myFixture)
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
        checkResolveCompiledAnnotation(myFixture)
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
        checkResolveSyntheticJavaPropertyCompoundAccess(myFixture, isK2 = false)
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
        checkResolveFunInterfaceSamWithValueClassInSignature(myFixture, isK2 = false)
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
        checkResolveTopLevelInlineInFacadeFromLibrary(myFixture, isK2 = false)
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
