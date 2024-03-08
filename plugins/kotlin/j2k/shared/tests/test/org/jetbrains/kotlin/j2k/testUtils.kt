// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

fun descriptorByFileDirective(testDataFile: File, languageLevel: LanguageLevel = LanguageLevel.JDK_1_8): LightProjectDescriptor {
    val fileText = FileUtil.loadFile(testDataFile, true)
    val descriptor = when {
        InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK") ->
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

        InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_STDLIB_JDK8") ->
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk8()

        else -> KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    return object : KotlinWithJdkAndRuntimeLightProjectDescriptor(descriptor.libraryFiles, descriptor.librarySourceFiles) {
        override fun getSdk(): Sdk? {
            val sdk = descriptor.sdk ?: return null
            runWriteAction {
                val modificator: SdkModificator = sdk.clone().sdkModificator
                JavaSdkImpl.attachJdkAnnotations(modificator)
                modificator.commitChanges()
            }
            return sdk
        }

        override fun configureModule(module: Module, model: ModifiableRootModel) {
            super.configureModule(module, model)
            model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = languageLevel
        }
    }
}

// TODO: adapted from `org.jetbrains.kotlin.idea.test.TestUtilsKt.dumpTextWithErrors`
@OptIn(KtAllowAnalysisOnEdt::class)
internal fun getK2FileTextWithErrors(file: KtFile): String {
    val errors: List<String> = allowAnalysisOnEdt {
        analyze(file) {
            val diagnostics = file.collectDiagnosticsForFile(filter = ONLY_COMMON_CHECKERS).asSequence()
            diagnostics
                // TODO: For some reason, there is a "redeclaration" error on every declaration for K2 tests
                .filter { it.factoryName != "PACKAGE_OR_CLASSIFIER_REDECLARATION" }
                .filter { it.severity == Severity.ERROR }
                .map { it.defaultMessage.replace(oldChar = '\n', newChar = ' ') }
                .toList()
        }
    }

    if (errors.isEmpty()) return file.text
    val header = errors.joinToString(separator = "\n", postfix = "\n") { "// ERROR: $it" }
    return header + file.text
}