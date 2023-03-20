// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.findApplicableConfigurator
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.createIntentionForFirstParentOfType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

class AddReflectionQuickFix(element: KtElement) : AddKotlinLibQuickFix(element, LibraryJarDescriptor.REFLECT_JAR, DependencyScope.COMPILE) {
    override fun getText() = KotlinJvmBundle.message("classpath.add.reflection")
    override fun getFamilyName() = text

    override fun getLibraryDescriptor(module: Module) = MavenExternalLibraryDescriptor.create(
        "org.jetbrains.kotlin",
        "kotlin-reflect",
        getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    )

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic) = diagnostic.createIntentionForFirstParentOfType(::AddReflectionQuickFix)
    }
}

class AddScriptRuntimeQuickFix(element: KtElement) : AddKotlinLibQuickFix(
    element,
    LibraryJarDescriptor.SCRIPT_RUNTIME_JAR,
    DependencyScope.COMPILE
) {
    override fun getText() = KotlinJvmBundle.message("classpath.add.script.runtime")
    override fun getFamilyName() = text

    override fun getLibraryDescriptor(module: Module) = MavenExternalLibraryDescriptor.create(
        "org.jetbrains.kotlin",
        "kotlin-script-runtime",
        getRuntimeLibraryVersion(module) ?: KotlinPluginLayout.standaloneCompilerVersion
    )

    companion object : KotlinSingleIntentionActionFactory() {

        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtElement>? =
            diagnostic.createIntentionForFirstParentOfType(::AddScriptRuntimeQuickFix)
    }
}

class AddTestLibQuickFix(element: KtElement) : AddKotlinLibQuickFix(element, LibraryJarDescriptor.TEST_JAR, DependencyScope.TEST) {
    override fun getText() = KotlinJvmBundle.message("classpath.add.kotlin.test")
    override fun getFamilyName() = text

    override fun getLibraryDescriptor(module: Module) = MavenExternalLibraryDescriptor.create(
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
                    return diagnostic.createIntentionForFirstParentOfType(::AddTestLibQuickFix)
                }

            }

            return null
        }
    }
}

abstract class AddKotlinLibQuickFix(
    element: KtElement,
    private val libraryJarDescriptor: LibraryJarDescriptor,
    private val scope: DependencyScope
) : KotlinQuickFixAction<KtElement>(element) {
    protected abstract fun getLibraryDescriptor(module: Module): MavenExternalLibraryDescriptor

    class MavenExternalLibraryDescriptor private constructor(
        groupId: String,
        artifactId: String,
        version: String
    ): ExternalLibraryDescriptor(groupId, artifactId, version, version) {
        companion object {
            fun create(groupId: String, artifactId: String, version: IdeKotlinVersion): MavenExternalLibraryDescriptor {
                val artifactVersion = version.artifactVersion
                return MavenExternalLibraryDescriptor(groupId, artifactId, artifactVersion)
            }
        }

        override fun getLibraryClassesRoots(): List<String> = emptyList()
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(element.containingFile.virtualFile) ?: return

        val configurator = findApplicableConfigurator(module)
        configurator.addLibraryDependency(module, element, getLibraryDescriptor(module), libraryJarDescriptor, scope)
    }

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = null
}