// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

val J2K_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk21()
    }

internal val J2K_FULL_JDK_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

// TODO: adapted from `org.jetbrains.kotlin.idea.test.TestUtilsKt.dumpTextWithErrors`
@OptIn(KaAllowAnalysisOnEdt::class)
internal fun getK2FileTextWithErrors(file: KtFile): String {
    val errors: List<String> = allowAnalysisOnEdt {
        analyze(file) {
            val diagnostics = file.collectDiagnostics(filter = ONLY_COMMON_CHECKERS).asSequence()
            diagnostics
                // TODO KTIJ-29640: For some reason, there is a "redeclaration" error on every declaration for K2 tests
                .filter { it.factoryName != "CLASSIFIER_REDECLARATION" && it.factoryName != "PACKAGE_CONFLICTS_WITH_CLASSIFIER" }
                .filter { it.severity == KaSeverity.ERROR }
                .map { it.defaultMessage.replace(oldChar = '\n', newChar = ' ') }
                .toList()
        }
    }

    if (errors.isEmpty()) return file.text
    val header = errors.joinToString(separator = "\n", postfix = "\n") { "// ERROR: $it" }
    return header + file.text
}

internal object J2kTestPreprocessorExtension : J2kPreprocessorExtension {
    override suspend fun processFiles(
        project: Project,
        files: List<PsiJavaFile>,
    ) {
        for (file in files) {
            val method = readAction {
                file.classes.firstOrNull()?.findDescendantOfType<PsiMethod> {
                    it.name != "main" && !it.isConstructor && !it.name.startsWith("get") && !it.name.startsWith("set")
                }
            } ?: continue

            writeAction {
                PsiImplUtil.setName(checkNotNull(method.nameIdentifier), "prebar")
            }
        }
    }
}

internal object J2kTestPostprocessorExtension : J2kPostprocessorExtension {
    override suspend fun processFiles(
        project: Project,
        files: List<KtFile>,
    ) {
        for (file in files) {
            val firstNamedParameter = readAction {
                file.findDescendantOfType<KtParameter> { it.nameIdentifier != null }
            } ?: continue

            val references = ReferencesSearch.search(firstNamedParameter, LocalSearchScope(file)).findAll()
            writeAction {
                PsiImplUtil.setName(checkNotNull(firstNamedParameter.nameIdentifier), "postbar")
            }
            for (reference in references) {
                writeAction {
                    PsiImplUtil.setName(reference.element, "postbar")
                }
            }
        }
    }
}