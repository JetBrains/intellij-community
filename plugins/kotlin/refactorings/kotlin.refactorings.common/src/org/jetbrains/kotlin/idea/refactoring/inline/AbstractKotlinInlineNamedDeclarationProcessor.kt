// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.lang.Language
import com.intellij.lang.refactoring.InlineHandler
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.OverrideMethodsProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.inline.GenericInlineHandler
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.searching.usages.ReferencesSearchScopeHelper
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.refactoring.deleteWithCompanion
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.replaceUsages
import org.jetbrains.kotlin.idea.search.KotlinSearchUsagesSupport
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

abstract class AbstractKotlinInlineNamedDeclarationProcessor<TDeclaration : KtNamedDeclaration>(
    declaration: TDeclaration,
    private val reference: PsiReference?,
    private val inlineThisOnly: Boolean,
    private val deleteAfter: Boolean,
    editor: Editor?,
    project: Project,
) : AbstractKotlinDeclarationInlineProcessor<TDeclaration>(declaration, editor, project) {
    private lateinit var inliners: Map<Language, InlineHandler.Inliner>

    abstract fun createReplacementStrategy(): UsageReplacementStrategy?

    abstract fun unwrapSpecialUsage(usage: KtReferenceExpression): KtSimpleNameExpression?

    open fun postAction() = Unit
    open fun postDeleteAction() = Unit

    protected final override fun findUsages(): Array<UsageInfo> {
        if (inlineThisOnly && reference != null) return arrayOf(UsageInfo(reference))
        val usages = hashSetOf<UsageInfo>()
        for (usage in ReferencesSearchScopeHelper.search(declaration, myRefactoringScope)) {
            usages += UsageInfo(usage)
        }

        if (shouldDeleteAfter) {
            declaration.forEachOverridingElement(scope = myRefactoringScope) { _, overridingMember ->
                val superMethods = KotlinSearchUsagesSupport.SearchUtils.findSuperMethodsNoWrapping(overridingMember)
                if (superMethods.singleOrNull()?.unwrapped == declaration) {
                    usages += OverrideUsageInfo(overridingMember)
                    return@forEachOverridingElement true
                }

                true
            }
        }

        return usages.toArray(UsageInfo.EMPTY_ARRAY)
    }

    open fun additionalPreprocessUsages(usages: Array<out UsageInfo>, conflicts: MultiMap<PsiElement, String>) = Unit

    protected final override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usagesInfo = refUsages.get()
        if (inlineThisOnly) {
            val element = usagesInfo.singleOrNull()?.element
            if (element != null && !CommonRefactoringUtil.checkReadOnlyStatus(myProject, element)) return false
        }


        val conflicts =
            ActionUtil.underModalProgress(declaration.project, RefactoringBundle.message("detecting.possible.conflicts")) {
                val conflicts = MultiMap<PsiElement, String>()
                additionalPreprocessUsages(usagesInfo, conflicts)
                for (usage in usagesInfo) {
                    val element = usage.element ?: continue
                    val callableConflict = findCallableConflictForUsage(element) ?: continue
                    conflicts.putValue(element, callableConflict)
                }

                if (shouldDeleteAfter) {
                    for (superDeclaration in KotlinSearchUsagesSupport.SearchUtils.findSuperMethodsNoWrapping(declaration)) {
                        val fqName = superDeclaration.kotlinFqName?.asString() ?: KotlinBundle.message("fix.change.signature.error")
                        val message = KotlinBundle.message("text.inlined.0.overrides.0.1", kind, fqName)
                        conflicts.putValue(superDeclaration, message)
                    }
                }

                inliners = GenericInlineHandler.initInliners(
                    declaration,
                    usagesInfo,
                    InlineHandler.Settings { inlineThisOnly },
                    conflicts,
                    KotlinLanguage.INSTANCE
                )

                conflicts
            }

        return showConflicts(conflicts, usagesInfo)
    }

    private val shouldDeleteAfter: Boolean get() = deleteAfter && isWritable

    private fun postActions() {
        if (shouldDeleteAfter) {
            declaration.deleteWithCompanion()
            postDeleteAction()
        }

        postAction()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        if (usages.isEmpty()) {
            if (!shouldDeleteAfter) {
                val message = KotlinBundle.message("0.1.is.never.used", kind.capitalize(), declaration.name.toString())
                CommonRefactoringUtil.showErrorHint(myProject, editor, message, commandName, null)
            } else {
                postActions()
            }

            return
        }

        val replacementStrategy = createReplacementStrategy() ?: return

        val (kotlinReferenceUsages, nonKotlinReferenceUsages) = usages.partition { it !is OverrideUsageInfo && it.element is KtReferenceExpression }
        for (usage in nonKotlinReferenceUsages) {
            val element = usage.element ?: continue
            when {
                usage is OverrideUsageInfo -> for (processor in OverrideMethodsProcessor.EP_NAME.extensionList) {
                    if (processor.removeOverrideAttribute(element)) break
                }

                element.language == KotlinLanguage.INSTANCE -> LOG.error("Found unexpected Kotlin usage $element")
                else -> GenericInlineHandler.inlineReference(usage, declaration, inliners)
            }
        }

        replacementStrategy.replaceUsages(
            usages = kotlinReferenceUsages.mapNotNull { it.element as? KtReferenceExpression },
            unwrapSpecialUsages = true,
            unwrapSpecialUsageOrNull = ::unwrapSpecialUsage
        )

        postActions()
    }

    private val isWritable: Boolean
        get() = declaration.isWritable

    override fun getElementsToWrite(descriptor: UsageViewDescriptor): Collection<PsiElement?> = when {
        inlineThisOnly -> listOfNotNull(reference?.element)
        isWritable -> listOfNotNull(reference?.element, declaration)
        else -> emptyList()
    }

    companion object {
        private val LOG = Logger.getInstance(AbstractKotlinInlineNamedDeclarationProcessor::class.java)
    }
}

private class OverrideUsageInfo(element: PsiElement) : UsageInfo(element)

@OptIn(KaAllowAnalysisOnEdt::class)
fun findCallableConflictForUsage(usage: PsiElement): @NlsContexts.DialogMessage String? {
    val usageParent = usage.parent as? KtCallableReferenceExpression ?: return null
    if (usageParent.callableReference != usage) return null
    allowAnalysisOnEdt { //todo j2k calls writeAction so it's impossible to wrap in simple progress yet
        analyze(usageParent) {
            val classSymbol = usageParent.expectedType?.expandedSymbol ?: return null
            val fqName = classSymbol.getFqNameIfPackageOrNonLocal() ?: return null
            if (fqName.isRoot || fqName.parent() != StandardNames.KOTLIN_REFLECT_FQ_NAME) return null
        }
    }
    return KotlinBundle.message("text.reference.cannot.be.converted.to.a.lambda")
}
