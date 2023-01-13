// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.uast

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.KtAssert
import org.jetbrains.uast.test.common.kotlin.UastResolveApiFixtureTestBase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirUastResolveApiFixtureTest : KotlinLightCodeInsightFixtureTestCase(), UastResolveApiFixtureTestBase {
    override val isFirUastPlugin: Boolean = true
    override fun isFirPlugin(): Boolean = true

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
        doCheck("ResolveStringFromUast", ::checkResolveStringFromUast)
    }

    fun testMultiResolve() {
        doCheck("MultiResolve", ::checkMultiResolve)
    }

    fun testMultiResolveJava() {
        doCheck("MultiResolveJava", ::checkMultiResolveJava)
    }

    fun testMultiResolveJavaAmbiguous() {
        doCheck("MultiResolveJavaAmbiguous", ::checkMultiResolveJavaAmbiguous)
    }

    fun testResolveFromBaseJava() {
        doCheck("ResolveFromBaseJava", ::checkResolveFromBaseJava)
    }

    fun testMultiResolveInClass() {
        doCheck("MultiResolveInClass", ::checkMultiResolveInClass)
    }

    fun testResolveToFacade() {
        doCheck("ResolveToFacade", ::checkResolveToFacade)
    }

    fun testMultiConstructorResolve() {
        doCheck("MultiConstructorResolve", ::checkMultiConstructorResolve)
    }

    fun testMultiInvokableObjectResolve() {
        doCheck("MultiInvokableObjectResolve", ::checkMultiInvokableObjectResolve)
    }

    fun testMultiResolveJvmOverloads() {
        doCheck("MultiResolveJvmOverloads", ::checkMultiResolveJvmOverloads)
    }

    fun testLocalResolve() {
        doCheck("LocalResolve", ::checkLocalResolve)
    }

    fun testResolveLocalDefaultConstructor() {
        doCheck("ResolveLocalDefaultConstructor", ::checkResolveLocalDefaultConstructor)
    }

    fun testResolveJavaClassAsAnonymousObjectSuperType() {
        doCheck("ResolveJavaClassAsAnonymousObjectSuperType", ::checkResolveJavaClassAsAnonymousObjectSuperType)
    }

    fun testResolveCompiledAnnotation() {
        doCheck("ResolveCompiledAnnotation", ::checkResolveCompiledAnnotation)
    }

    fun testResolveExplicitLambdaParameter() {
        doCheck("ResolveExplicitLambdaParameter", ::checkResolveExplicitLambdaParameter)
    }

    fun testResolveImplicitLambdaParameter() {
        doCheck("ResolveImplicitLambdaParameter", ::checkResolveImplicitLambdaParameter)
    }

    fun testResolveSyntheticMethod() {
        doCheck("ResolveSyntheticMethod", ::checkResolveSyntheticMethod)
    }

    fun testMapFunctions() {
        doCheck("MapFunctions", ::checkMapFunctions)
    }

    fun testListIterator() {
        doCheck("ListIterator", ::checkListIterator)
    }

    fun testStringJVM() {
        doCheck("StringJVM", ::checkStringJVM)
    }

    fun testArgumentMappingDefaultValue() {
        doCheck("ArgumentMappingDefaultValue", ::checkArgumentMappingDefaultValue)
    }

    fun testArgumentMappingExtensionFunction() {
        doCheck("ArgumentMappingExtensionFunction", ::checkArgumentMappingExtensionFunction)
    }

    fun testArgumentMappingVararg() {
        doCheck("ArgumentMappingVararg", ::checkArgumentMappingVararg)
    }

    fun testArgumentMappingOOBE() {
        doCheck("ArgumentMappingOOBE", ::checkArgumentMappingOOBE)
    }

    fun testSyntheticEnumMethods() {
        doCheck("SyntheticEnumMethods", ::checkSyntheticEnumMethods)
    }

    fun testArrayAccessOverloads() {
        doCheck("ArrayAccessOverloads", ::checkArrayAccessOverloads)
    }

    fun testOperatorOverloads() {
        doCheck("OperatorOverloads", ::checkOperatorOverloads)
    }

    fun testOperatorMultiResolvable() {
        doCheck("OperatorMultiResolvable", ::checkOperatorMultiResolvable)
    }

    fun testResolveSyntheticJavaPropertyAccessor() {
        doCheck("ResolveSyntheticJavaPropertyAccessor", ::checkResolveSyntheticJavaPropertyAccessor)
    }

    fun testResolveKotlinPropertyAccessor() {
        doCheck("ResolveKotlinPropertyAccessor", ::checkResolveKotlinPropertyAccessor)
    }

    fun testResolveStaticImportFromObject() {
        doCheck("ResolveStaticImportFromObject", ::checkResolveStaticImportFromObject)
    }

    fun testResolveToSubstituteOverride() {
        doCheck("ResolveToSubstituteOverride", ::checkResolveToSubstituteOverride)
    }

    fun testResolveEnumEntrySuperType() {
        doCheck("TypeReferenceFromEnumEntry", ::checkResolveEnumEntrySuperType)
    }

    fun testResolveLambdaInvoke() {
        doCheck("LambdaInvoke", ::checkLambdaInvoke)
    }

}
