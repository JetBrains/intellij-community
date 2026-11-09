// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import com.intellij.codeInspection.dataFlow.StandardDataFlowRunner
import com.intellij.codeInspection.dataFlow.interpreter.RunnerResult
import com.intellij.codeInspection.dataFlow.java.JavaDfaListener
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState
import com.intellij.codeInspection.dataFlow.value.DfaValue
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.j2k.J2KTypeInference
import org.jetbrains.kotlin.j2k.J2K_LOMBOK_PROJECT_DESCRIPTOR
import org.jetbrains.kotlin.j2k.Nullability

class J2KTypeInferenceLombokTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_LOMBOK_PROJECT_DESCRIPTOR

    fun testGeneratedMembersHaveBodiesInConversionCopy() {
        val file = myFixture.configureByText(
            "Pojo.java", """
            import lombok.AllArgsConstructor;
            import lombok.Getter;
            import lombok.Setter;

            @AllArgsConstructor
            @Getter
            @Setter
            public class Pojo {
                private String name;
            }
            """.trimIndent()
        )
        val pojo = (file.copy() as PsiClassOwner).classes.single()
        val name = checkNotNull(pojo.findFieldByName("name", false))

        assertAssignsParameterToField(pojo.constructors.single(), name)
        assertAssignsParameterToField(pojo.findMethodsByName("setName", false).single(), name)

        val getter = pojo.findMethodsByName("getName", false).single()
        val returned = PsiUtil.findReturnStatements(getter).single().returnValue as PsiReferenceExpression
        assertEquals(name, returned.resolve())
        assertTrue(returned in pushedByDfa(getter))
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    fun testGeneratedConstructorCallsReachFields() {
        val file = myFixture.configureByText(
            "Pojo.java", """
            import lombok.AccessLevel;
            import lombok.AllArgsConstructor;

            @AllArgsConstructor(access = AccessLevel.PRIVATE)
            public class Pojo {
                private final String name;
                private final String nickname;

                public static Pojo create() {
                    return new Pojo("x", null);
                }
            }
            """.trimIndent()
        )
        val decisions = allowAnalysisOnEdt { J2KTypeInference(file.copy() as PsiFile).decisions }
            .entries.filter { it.key is PsiField }.associate { (it.key as PsiField).name to it.value }

        assertEquals(Nullability.NotNull, decisions["name"])
        assertEquals(Nullability.Nullable, decisions["nickname"])
    }

    private fun assertAssignsParameterToField(method: PsiMethod, field: PsiField) {
        val assignment = checkNotNull(PsiTreeUtil.findChildOfType(method.body, PsiAssignmentExpression::class.java))
        assertEquals(field, (assignment.lExpression as PsiReferenceExpression).resolve())
        val value = assignment.rExpression as PsiReferenceExpression
        assertEquals(method.parameterList.parameters.single(), value.resolve())
        assertTrue(value in pushedByDfa(method))
    }

    private fun pushedByDfa(method: PsiMethod): List<PsiExpression> {
        val pushed = ArrayList<PsiExpression>()
        val listener = object : JavaDfaListener {
            override fun beforeExpressionPush(value: DfaValue, expression: PsiExpression, state: DfaMemoryState) {
                pushed += expression
            }
        }
        val result = StandardDataFlowRunner(project, ThreeState.UNSURE).analyzeMethodRecursively(checkNotNull(method.body), listener)
        assertEquals(RunnerResult.OK, result)
        return pushed
    }
}
