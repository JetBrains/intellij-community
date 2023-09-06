// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.chooseContainer

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.navigation.TargetPresentationProvider
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.codeInsight.unwrap.RangeSplitter
import com.intellij.codeInsight.unwrap.UnwrapHandler
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiPackageBase
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.collapseSpaces
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import java.util.*
import javax.swing.Icon

fun <T> chooseContainerElementIfNecessary(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: (T) -> PsiElement,
    onSelect: (T) -> Unit
): Unit = chooseContainerElementIfNecessaryImpl(containers, editor, title, highlightSelection, toPsi, onSelect)

fun <T : PsiElement> chooseContainerElementIfNecessary(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    onSelect: (T) -> Unit
): Unit = chooseContainerElementIfNecessaryImpl(containers, editor, title, highlightSelection, null, onSelect)

private fun <T> chooseContainerElementIfNecessaryImpl(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: ((T) -> PsiElement)?,
    onSelect: (T) -> Unit
) {
    when {
        containers.isEmpty() -> return
        containers.size == 1 || isUnitTestMode() -> onSelect(containers.first())
        toPsi != null -> chooseContainerElement(containers, editor, title, highlightSelection, toPsi, onSelect)
        else -> {
            @Suppress("UNCHECKED_CAST")
            chooseContainerElement(containers as List<PsiElement>, editor, title, highlightSelection, onSelect as (PsiElement) -> Unit)
        }
    }
}

private fun <T> chooseContainerElement(
    containers: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    toPsi: (T) -> PsiElement,
    onSelect: (T) -> Unit
) {
    val psiElements = containers.map(toPsi)
    choosePsiContainerElement(
        elements = psiElements,
        editor = editor,
        title = title,
        highlightSelection = highlightSelection,
        psi2Container = { containers[psiElements.indexOf(it)] },
        onSelect = onSelect
    )
}

private fun <T : PsiElement> chooseContainerElement(
    elements: List<T>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    onSelect: (T) -> Unit
): Unit = choosePsiContainerElement(
    elements = elements,
    editor = editor,
    title = title,
    highlightSelection = highlightSelection,
    psi2Container = { it },
    onSelect = onSelect,
)

private fun <T, E : PsiElement> choosePsiContainerElement(
    elements: List<E>,
    editor: Editor,
    @NlsContexts.PopupTitle title: String,
    highlightSelection: Boolean,
    psi2Container: (E) -> T,
    onSelect: (T) -> Unit,
) {
    val popup = getPsiElementPopup(
        editor,
        elements,
        popupPresentationProvider(),
        title,
        highlightSelection,
    ) { psiElement ->
        @Suppress("UNCHECKED_CAST")
        onSelect(psi2Container(psiElement as E))
        true
    }

    invokeLater {
        popup.showInBestPositionFor(editor)
    }
}

private fun <T : PsiElement> getPsiElementPopup(
    editor: Editor,
    elements: List<T>,
    presentationProvider: TargetPresentationProvider<T>,
    @NlsContexts.PopupTitle title: String?,
    highlightSelection: Boolean,
    processor: (T) -> Boolean
): JBPopup {
    val project = elements.firstOrNull()?.project ?: throw IllegalArgumentException("Can't create popup because no elements are provided")
    val highlighter = if (highlightSelection) SelectionAwareScopeHighlighter(editor) else null
    return PsiTargetNavigator(elements)
        .presentationProvider(presentationProvider)
        .builderConsumer { builder ->
            builder
                .setItemSelectedCallback { presentation ->
                    highlighter?.dropHighlight()
                    val psiElement = (presentation?.item as? SmartPsiElementPointer<*>)?.element ?: return@setItemSelectedCallback
                    highlighter?.highlight(psiElement)
                }
                .setItemChosenCallback {
                    @Suppress("UNCHECKED_CAST")
                    val element = (it.item as? SmartPsiElementPointer<T>)?.element ?: return@setItemChosenCallback
                    processor(element)
                }
                .addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) {
                        highlighter?.dropHighlight()
                    }
                })
        }
        .createPopup(project, title)
}

private fun popupPresentationProvider() = object : PsiTargetPresentationRenderer<PsiElement>() {
    @NlsSafe
    private fun PsiElement.renderName(): String = when {
        this is KtPropertyAccessor -> property.renderName() + if (isGetter) ".get" else ".set"
        this is KtObjectDeclaration && isCompanion() -> {
            val name = getStrictParentOfType<KtClassOrObject>()?.renderName() ?: "<anonymous>"
            "Companion object of $name"
        }

        else -> (this as? PsiNamedElement)?.name ?: "<anonymous>"
    }

    @NlsSafe
    private fun PsiElement.renderDeclaration(): String? {
        return null
        //if (this is KtFunctionLiteral || isFunctionalExpression()) return renderText()
        //val descriptor = when (this) {
        //    is KtFile -> name
        //    is KtElement -> analyze()[BindingContext.DECLARATION_TO_DESCRIPTOR, this]
        //    is PsiMember -> getJavaMemberDescriptor()
        //    else -> null
        //} ?: return null
        //
        //val name = renderName()
        //val params = (descriptor as? FunctionDescriptor)?.valueParameters?.joinToString(
        //    ", ",
        //    "(",
        //    ")"
        //) { DescriptorRenderer.SHORT_NAMES_IN_TYPES.renderType(it.type) } ?: ""
        //
        //return "$name$params"
    }

    @NlsSafe
    private fun PsiElement.renderText(): String = when (this) {
        is SeparateFileWrapper -> KotlinBundle.message("refactoring.extract.to.separate.file.text")
        is PsiPackageBase -> qualifiedName
        is PsiFile -> name
        is KtClassOrObject -> {
            val list = mutableListOf<String>()
            modifierList?.let {
                for (child in it.allChildren) {
                    if (child is KtAnnotationEntry || child is KtAnnotation || child is PsiWhiteSpace) continue
                    list.add(child.text)
                }
            }
            getDeclarationKeyword()?.text?.let(list::add)
            name?.let(list::add)
            StringUtil.shortenTextWithEllipsis(list.joinToString(separator = " "), 53, 0)
        }
        is KtNamedFunction -> {
            val list = mutableListOf<String>()
            for (child in allChildren) {
                if (child is PsiComment || child is PsiWhiteSpace) continue
                if (child is KtBlockExpression) break
                list.add(child.text)
            }
            StringUtil.shortenTextWithEllipsis(list.joinToString(separator = " "), 53, 0)
        }
        else -> {
            val text = text ?: "<invalid text>"
            StringUtil.shortenTextWithEllipsis(text.collapseSpaces(), 53, 0)
        }
    }

    private fun PsiElement.getRepresentativeElement(): PsiElement = when (this) {
        is KtBlockExpression -> (parent as? KtDeclarationWithBody) ?: this
        is KtClassBody -> parent as KtClassOrObject
        else -> this
    }

    override fun getElementText(element: PsiElement): String {
        val representativeElement = element.getRepresentativeElement()
        return representativeElement.renderDeclaration() ?: representativeElement.renderText()
    }

    override fun getContainerText(element: PsiElement): String? = null

    override fun getIcon(element: PsiElement): Icon? = super.getIcon(element.getRepresentativeElement())
}

private class SelectionAwareScopeHighlighter(val editor: Editor) {
    private val highlighters = ArrayList<RangeHighlighter>()

    private fun addHighlighter(r: TextRange, attr: TextAttributes) {
        highlighters.add(
            editor.markupModel.addRangeHighlighter(
                r.startOffset,
                r.endOffset,
                UnwrapHandler.HIGHLIGHTER_LEVEL,
                attr,
                HighlighterTargetArea.EXACT_RANGE
            )
        )
    }

    fun highlight(wholeAffected: PsiElement) {
        dropHighlight()

        val affectedRange = wholeAffected.textRange ?: return

        val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)!!
        val selectedRange = with(editor.selectionModel) { TextRange(selectionStart, selectionEnd) }
        val textLength = editor.document.textLength
        for (r in RangeSplitter.split(affectedRange, Collections.singletonList(selectedRange))) {
            if (r.endOffset <= textLength) addHighlighter(r, attributes)
        }
    }

    fun dropHighlight() {
        highlighters.forEach { it.dispose() }
        highlighters.clear()
    }
}

class SeparateFileWrapper(manager: PsiManager) : LightElement(manager, KotlinLanguage.INSTANCE) {
    override fun toString() = ""
}