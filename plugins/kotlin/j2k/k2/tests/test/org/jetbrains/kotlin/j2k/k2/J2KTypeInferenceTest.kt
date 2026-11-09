// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.k2

import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.j2k.J2KTypeInference
import org.jetbrains.kotlin.j2k.J2K_PROJECT_DESCRIPTOR
import org.jetbrains.kotlin.j2k.Nullability

class J2KTypeInferenceTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = J2K_PROJECT_DESCRIPTOR

    fun testFieldAssignedOnOnePathStaysNullable() {
        val decisions = decisionsByName(
            """
            public class C {
                private String assignedOnOnePath;
                private String assignedOnEveryPath;

                public C(boolean c) {
                    if (c) assignedOnOnePath = "x";
                    assignedOnEveryPath = "y";
                }
            }
            """
        )
        assertEquals(Nullability.Nullable, decisions["assignedOnOnePath"])
        assertEquals(Nullability.NotNull, decisions["assignedOnEveryPath"])
    }

    fun testLibraryMethodAnnotationReachesLocal() {
        val decisions = decisionsByName(
            """
            public class C {
                void test() {
                    Integer boxed = Integer.valueOf(100);
                }
            }
            """
        )
        assertEquals(Nullability.NotNull, decisions["boxed"])
    }

    fun testThrowingContractBranchGivesNoValue() {
        val decisions = decisionsByName(
            """
            import java.util.List;

            class C {
                void test(List<String> list) {
                    String first = list.get(0);
                }
            }
            """
        )
        assertEquals(Nullability.Default, decisions["first"])
    }

    fun testWildcardElementsLinkOneWay() {
        val decisions = decisionsByName(
            """
            import java.util.List;

            class C {
                void test(List<String> withNull, List<String> clean, List<String> target) {
                    withNull.add(null);
                    List<? extends String> either = withNull;
                    either = clean;
                    String fromEither = either.get(0);
                    String fromClean = clean.get(0);

                    List<? super String> sink = target;
                    sink.add(null);
                    String fromTarget = target.get(0);
                }
            }
            """
        )
        assertEquals(Nullability.Nullable, decisions["fromEither"])
        assertEquals(Nullability.Default, decisions["fromClean"])
        assertEquals(Nullability.Nullable, decisions["fromTarget"])
    }

    fun testPackagePrivateMembersUseTheWholePackage() {
        myFixture.addFileToProject(
            "p/Other.java", """
            package p;

            class Other {
                void use(C c) {
                    c.packageMethod("y");
                    c.publicMethod("y");
                    c.packageField = null;
                }
            }
            """.trimIndent()
        )
        val decisions = decisionsByName(
            """
            package p;

            public class C {
                String packageField = "x";

                void packageMethod(String packageParameter) {}

                public void publicMethod(String publicParameter) {}

                void call() {
                    packageMethod("x");
                    publicMethod("x");
                }
            }
            """, "p/C.java"
        )
        assertEquals(Nullability.NotNull, decisions["packageParameter"])
        assertEquals(Nullability.Default, decisions["publicParameter"])
        assertEquals(Nullability.Nullable, decisions["packageField"])
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    private fun decisionsByName(javaCode: String, path: String = "C.java"): Map<String?, Nullability> {
        val file = myFixture.addFileToProject(path, javaCode.trimIndent())
        return allowAnalysisOnEdt { J2KTypeInference(file).decisions }.mapKeys { (it.key as? PsiNamedElement)?.name }
    }
}
