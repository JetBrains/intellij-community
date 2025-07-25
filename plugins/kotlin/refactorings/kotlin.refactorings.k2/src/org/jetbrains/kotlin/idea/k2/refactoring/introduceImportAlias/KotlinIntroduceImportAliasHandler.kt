// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.refactoring.introduceImportAlias

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidatorProvider
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.fileScope
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.extractionEngine.KotlinNameSuggester
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.isImportUsage
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

object KotlinIntroduceImportAliasHandler : RefactoringActionHandler {
    private val REFACTORING_NAME = KotlinBundle.message("name.introduce.import.alias")

    @OptIn(KaImplementationDetail::class)
    fun doRefactoring(project: Project, editor: Editor, element: KtNameReferenceExpression) {
        val file = element.containingKtFile
        val declaration = element.mainReference.resolve() as? KtNamedDeclaration ?: return
        val fqName = ((declaration as? KtConstructor<*>)?.getContainingClassOrObject() ?: declaration).fqName ?: return
        val oldName = element.mainReference.value
        val namedUsages = ActionUtil.underModalProgress(project, KotlinBundle.message("perform.refactoring")) {
            val declarations = analyze(element) {
                val scopes = mutableListOf<KaScope>()
                scopes.add(file.scopeContext(element).compositeScope())
                var receiverExpression = element.getReceiverExpression()
                while (receiverExpression != null) {
                    scopes.addIfNotNull(
                        (((receiverExpression as? KtQualifiedExpression)?.selectorExpression
                            ?: receiverExpression).mainReference?.resolveToSymbol() as? KaNamedClassSymbol)?.combinedDeclaredMemberScope
                    )
                    receiverExpression = (receiverExpression as? KtQualifiedExpression)?.receiverExpression
                }

                scopes.asCompositeScope().declarations.filter { symbol ->
                    fqName.shortName() == (symbol as? KaNamedSymbol)?.name ||
                            (symbol as? KaNamedClassSymbol)?.companionObject?.name == fqName.shortName() && fqName.parent().shortName() == symbol.name
                }
                    .mapNotNull { it.psi as? KtNamedDeclaration }
                    .distinct()
                    .toList()
            }

            val fileSearchScope = file.fileScope()

            val declarationUsages = declarations.flatMap { declaration ->
                val extensionDeclaration = declaration.isExtensionDeclaration()
                ReferencesSearch.search(declaration, fileSearchScope).map { reference ->
                    UsageContext(
                        reference.element.createSmartPointer(), isExtension = extensionDeclaration
                    )
                }
            }

            val validatorProvider = KotlinNameValidatorProvider.getInstance()
            val nameValidators = declarationUsages.mapNotNull { it.pointer.element as? KtElement }
                .flatMap { decl ->
                    KotlinNameSuggestionProvider.ValidatorTarget.entries.map {
                        validatorProvider.createNameValidator(
                            file,
                            it,
                            decl
                        )
                    }
                }

            val shortName = if ((declaration as? KtObjectDeclaration)?.isCompanion() == true) fqName.parent().shortName() else fqName.shortName()
            val suggestNameByName = KotlinNameSuggester.suggestNameByName(shortName.asString()) {
                nameValidators.all { validator -> validator(it) } && it != oldName
            }
            suggestNameByName to declarationUsages
        }

        val elementInImportDirective = element.isInImportDirective()

        val newName = namedUsages.first
        project.executeCommand(KotlinBundle.message("intention.add.import.alias.group.name"), groupId = null) {
            var newDirectivePointer: SmartPsiElementPointer<KtImportDirective>? = null
            ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread(
                KotlinBundle.message("intention.add.import.alias.group.name"),
                project,
                null
            ) {
                val pointer = file.addImport(fqName, false, Name.identifier(newName)).createSmartPointer()
                replaceUsages(namedUsages.second, newName)
                cleanImport(file, fqName)
                newDirectivePointer = pointer
            }

            val restoredNewDirective = newDirectivePointer?.element ?: return@executeCommand
            if (elementInImportDirective) {
                editor.moveCaret(restoredNewDirective.alias?.nameIdentifier?.textOffset ?: restoredNewDirective.endOffset)
            }

            val alias = restoredNewDirective.alias?.createSmartPointer() ?: return@executeCommand
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            val rename = VariableInplaceRenamer(alias.element, editor, project)
            val nameSuggestions = LinkedHashSet<String>()
            nameSuggestions.add(newName)
            rename.performInplaceRefactoring(nameSuggestions)
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElement(editor, file, ElementKind.EXPRESSION) {
            doRefactoring(project, editor, it as KtNameReferenceExpression)
        }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("$REFACTORING_NAME can only be invoked from editor")
    }
}

private data class UsageContext(val pointer: SmartPsiElementPointer<PsiElement>, val isExtension: Boolean)

private fun cleanImport(file: KtFile, fqName: FqName) {
    file.importDirectives.find { it.alias == null && fqName == it.importedFqName }?.delete()
}

private fun replaceUsages(usages: List<UsageContext>, newName: String) {
    for (usage in usages.asReversed()) {
        val reference = (usage.pointer.element as? KtElement)?.mainReference?.takeUnless { it.isImportUsage() } ?: continue
        val newExpression = reference.handleElementRename(newName) as? KtNameReferenceExpression ?: continue
        if (usage.isExtension) {
            newExpression.getQualifiedElementSelector()?.replace(newExpression)
            continue
        }

        val qualifiedElement = newExpression.getQualifiedElement()
        if (qualifiedElement != newExpression) {
            val parent = newExpression.parent
            if (parent is KtCallExpression || parent is KtUserType) {
                newExpression.siblings(forward = false, withItself = false).forEach(PsiElement::delete)
                qualifiedElement.replace(parent)
            } else qualifiedElement.replace(newExpression)
        }
    }
}