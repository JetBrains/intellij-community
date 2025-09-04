// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.engine.JavaDebuggerCodeFragmentFactory
import com.intellij.debugger.engine.evaluation.TextWithImports
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.OldJavaToKotlinConverter
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtExpressionCodeFragment

abstract class KotlinCodeFragmentFactoryBase : JavaDebuggerCodeFragmentFactory() {

    abstract fun KtBlockCodeFragment.registerCodeFragmentExtensions(contextElement: PsiElement?)

    override fun createPresentationPsiCodeFragmentImpl(item: TextWithImports, context: PsiElement?, project: Project): JavaCodeFragment? {
        val kotlinCodeFragment = createPsiCodeFragment(item, context, project) ?: return null
        if (!PsiTreeUtil.hasErrorElements(kotlinCodeFragment) || kotlinCodeFragment !is KtCodeFragment) return kotlinCodeFragment
        val javaExpression = try {
            PsiElementFactory.getInstance(project).createExpressionFromText(item.text, context)
        } catch (e: IncorrectOperationException) {
            null
        }
        val importList = try {
            kotlinCodeFragment.importsAsImportList()?.let {
                (PsiFileFactory.getInstance(project).createFileFromText(
                    "dummy.java", JavaFileType.INSTANCE, it.text
                ) as? PsiJavaFile)?.importList
            }
        } catch (_: IncorrectOperationException) {
            null
        }
        val convertedFragment: KtExpressionCodeFragment? = runReadAction {
            try {
                val converter = OldJavaToKotlinConverter(project, ConverterSettings.defaultSettings)
                val res = converter.elementsToKotlin(listOfNotNull(javaExpression))
                val newText = res.results.singleOrNull()?.text
                val newImports = importList?.text
                newText?.let {
                    KtExpressionCodeFragment(
                        project,
                        kotlinCodeFragment.name,
                        it,
                        newImports,
                        kotlinCodeFragment.context
                    )
                }
            } catch (e: Exception) {
                // ignored because text can be invalid
                LOG.error("Couldn't convert expression:\n`${javaExpression?.text}`", e)
                null
            }
        }
        return convertedFragment ?: kotlinCodeFragment
    }

    override fun getFileType(): KotlinFileType {
        return KotlinFileType.INSTANCE
    }

    override fun getEvaluatorBuilder(): KotlinEvaluatorBuilder {
        return KotlinEvaluatorBuilder
    }

    protected fun initImports(imports: String?): String? {
        if (!imports.isNullOrEmpty()) {
            return imports.split(KtCodeFragment.IMPORT_SEPARATOR)
                .mapNotNull { fixImportIfNeeded(it) }
                .joinToString(KtCodeFragment.IMPORT_SEPARATOR)
        }
        return null
    }

    private fun fixImportIfNeeded(import: String): String? {
        // skip arrays
        if (import.endsWith("[]")) {
            return fixImportIfNeeded(import.removeSuffix("[]").trim())
        }

        // skip primitive types
        if (PsiTypesUtil.boxIfPossible(import) != import) {
            return null
        }
        return import
    }
}