// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress
import org.jetbrains.kotlin.idea.configuration.withScope
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.createIntentionForModCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

@K1Deprecation
class AddReflectionQuickFix(element: KtElement) : AddKotlinLibQuickFix(element, DependencyScope.COMPILE) {
    override val quickFixText: String
        get() = KotlinJvmBundle.message("classpath.add.reflection")

    override fun getLibraryDescriptor(module: Module): MavenExternalLibraryDescriptor = MavenExternalLibraryDescriptor.create(
        "org.jetbrains.kotlin",
        "kotlin-reflect",
        getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    )

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
            diagnostic.createIntentionForModCommand(::AddReflectionQuickFix)
    }
}

@K1Deprecation
class AddScriptRuntimeQuickFix(element: KtElement) : AddKotlinLibQuickFix(
    element,
    DependencyScope.COMPILE
) {
    override val quickFixText: String
        get() = KotlinJvmBundle.message("classpath.add.script.runtime")

    override fun getLibraryDescriptor(module: Module): MavenExternalLibraryDescriptor = MavenExternalLibraryDescriptor.create(
        "org.jetbrains.kotlin",
        "kotlin-script-runtime",
        getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    )

    companion object : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): IntentionAction? =
            diagnostic.createIntentionForModCommand(::AddScriptRuntimeQuickFix)
    }
}

@K1Deprecation
class AddTestLibQuickFix(element: KtElement) : AddKotlinLibQuickFix(element, DependencyScope.TEST) {
    override val quickFixText: String
        get() = KotlinJvmBundle.message("classpath.add.kotlin.test")

    override fun getLibraryDescriptor(module: Module): MavenExternalLibraryDescriptor = MavenExternalLibraryDescriptor.create(
        "org.jetbrains.kotlin",
        "kotlin-test",
        getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    )

    companion object : KotlinSingleIntentionActionFactory() {
        private val KOTLIN_TEST_UNRESOLVED = setOf(
            "Asserter", "assertFailsWith", "currentStackTrace", "failsWith", "todo", "assertEquals",
            "assertFails", "assertNot", "assertNotEquals", "assertNotNull", "assertNull", "assertTrue", "expect", "fail", "fails"
        )

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val unresolvedReference = Errors.UNRESOLVED_REFERENCE.cast(diagnostic)

            if (PsiTreeUtil.getParentOfType(diagnostic.psiElement, KtImportDirective::class.java) != null) return null

            val unresolvedText = unresolvedReference.a.text
            if (unresolvedText in KOTLIN_TEST_UNRESOLVED) {
                val ktFile = (diagnostic.psiElement.containingFile as? KtFile) ?: return null

                val exactImportFqName = FqName("kotlin.test.$unresolvedText")
                val kotlinTestAllUnder = FqName("kotlin.test")

                var hasExactImport = false
                var hasKotlinTestAllUnder = false

                for (importDirective in ktFile.importDirectives.filter { it.text.contains("kotlin.test.") }) {
                    if (importDirective.importedFqName == exactImportFqName) {
                        hasExactImport = true
                        break
                    }

                    if (importDirective.importedFqName == kotlinTestAllUnder && importDirective.isAllUnder) {
                        hasKotlinTestAllUnder = true
                        break
                    }
                }

                if (hasExactImport || hasKotlinTestAllUnder) {
                    return diagnostic.createIntentionForModCommand(::AddTestLibQuickFix)
                }

            }

            return null
        }
    }
}

@K1Deprecation
abstract class AddKotlinLibQuickFix(
    protected val element: KtElement,
    private val scope: DependencyScope
) : ModCommandAction {
    protected abstract val quickFixText: String

    override fun getFamilyName(): @IntentionFamilyName String = quickFixText

    override fun getPresentation(context: ActionContext): Presentation? {
        val file = context.file
        val module = file.module ?: return null
        val dependencyManager = KotlinBuildSystemDependencyManager.findApplicableConfigurator(module) ?: return null
        if (dependencyManager.isProjectSyncPendingOrInProgress()) return null
        return quickFixText
            .takeIf { dependencyManager.isApplicable(module) && !dependencyManager.isProjectSyncPendingOrInProgress() }
            ?.let(Presentation::of)
    }

    protected abstract fun getLibraryDescriptor(module: Module): MavenExternalLibraryDescriptor

    class MavenExternalLibraryDescriptor private constructor(
        groupId: String,
        artifactId: String,
        version: String
    ) : com.intellij.openapi.roots.ExternalLibraryDescriptor(groupId, artifactId, version, version) {
        companion object {
            fun create(groupId: String, artifactId: String, version: IdeKotlinVersion): MavenExternalLibraryDescriptor {
                val artifactVersion = version.artifactVersion
                return MavenExternalLibraryDescriptor(groupId, artifactId, artifactVersion)
            }
        }

        override fun getLibraryClassesRoots(): List<String> = emptyList()
    }

    override fun perform(context: ActionContext): ModCommand {
        val file = context.file
        val element = file.takeIf { it.module != null } ?: return ModCommand.nop()

        val module =
            ProjectRootManager.getInstance(element.project).fileIndex
                .getModuleForFile(element.containingFile.virtualFile) ?: return ModCommand.nop()
        val configurator =
            KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)
                ?: return ModCommand.nop()

        val libraryDescriptor = getLibraryDescriptor(module).withScope(scope)

        return configurator.addDependencyModCommand(file, module, libraryDescriptor)
    }
}