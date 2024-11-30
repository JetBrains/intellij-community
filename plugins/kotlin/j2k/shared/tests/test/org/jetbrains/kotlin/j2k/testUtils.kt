// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode.K1
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode.K2
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES.IGNORE_K1
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES.IGNORE_K2
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.dumpTextWithErrors
import org.jetbrains.kotlin.idea.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.io.File

val J2K_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    object : KotlinWithJdkAndRuntimeLightProjectDescriptor() {
        override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk21()

        override fun addDefaultLibraries(model: ModifiableRootModel) {
            DefaultLightProjectDescriptor.addJetBrainsAnnotationsWithTypeUse(model)
        }
    }

private val ignoreDirectives: Set<String> = setOf(IGNORE_K1, IGNORE_K2)

internal val J2K_FULL_JDK_PROJECT_DESCRIPTOR: KotlinWithJdkAndRuntimeLightProjectDescriptor =
    KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

internal fun KtFile.getFileTextWithErrors(pluginMode: KotlinPluginMode): String =
    if (pluginMode === K2) getK2FileTextWithErrors(this) else dumpTextWithErrors()

internal fun getDisableTestDirective(pluginMode: KotlinPluginMode): String =
    if (pluginMode === K2) IGNORE_K2 else IGNORE_K1

internal fun File.getFileTextWithoutDirectives(): String =
    readText().getTextWithoutDirectives()

internal fun String.getTextWithoutDirectives(): String =
    split("\n").filterNot { it.trim() in ignoreDirectives }.joinToString(separator = "\n")

// 1. ".k1.kt" testdata is for trivial differences between K1 and K2 (for example, different wording of error messages).
// Such files will be deleted along with the whole K1 plugin.
// In such tests, the K2 testdata with the default ".kt" suffix is considered completely correct.
//
// 2. ".k2.kt" testdata is for tests that are mostly good on K2, but there are differences due to minor missing post-processings
// that we may support later in "idiomatic" mode.
// Still, we don't want to completely ignore such tests in K2.
//
// 3. If the test only has a default version of testdata ".kt", then:
//   - it may have "IGNORE_K2" directive, in this case the test is completely ignored in K2
//   - or, if no IGNORE directives are present, the K1 and K2 results are identical for such a test
internal fun getExpectedFile(testFile: File, isCopyPaste: Boolean, pluginMode: KotlinPluginMode): File {
    val prefix = if (isCopyPaste) ".expected" else ""
    val testFileExtension = ".${testFile.extension}"

    val defaultFile = File(testFile.path.replace(testFileExtension, "$prefix.kt"))
    if (!defaultFile.exists()) {
        throw AssertionError("Expected file doesn't exist: ${defaultFile.path}.")
    }

    val customFileExtension = when (pluginMode) {
        K1 -> "$prefix.k1.kt"
        K2 -> "$prefix.k2.kt"
    }
    val customFile = File(testFile.path.replace(testFileExtension, customFileExtension)).takeIf(File::exists)
    if (customFile == null) return defaultFile

    val defaultText = defaultFile.readText().trimTrailingWhitespacesAndAddNewlineAtEOF()
    val customText = customFile.readText().trimTrailingWhitespacesAndAddNewlineAtEOF()
    if (defaultText != customText) return customFile

    customFile.delete()
    throw AssertionError(
        """
            Custom expected file text is the same as the default one.
            Deleting custom file: ${customFile.path}.
            Please rerun the test now.""".trimIndent()
    )
}

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