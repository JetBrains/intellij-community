// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.move.ui

import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants

@ApiStatus.Internal
class K2MoveToClassDialog(
    private val project: Project,
    private val declaration: KtCallableDeclaration,
    private val candidates: List<TargetClassCandidateParameter>,
) : DialogWrapper(project, true) {

    companion object {
        private const val PREFERRED_DIALOG_WIDTH_UNSCALED: Int = 450
    }

    private enum class Mode {
        /**
         * Move to class with conversion: turn a value parameter, a context parameter or the extension receiver into the dispatch receiver
         */
        CONVERT_PARAMETER_TO_DISPATCH_RECEIVER,

        /**
         * Move without conversion: don't try to find an instance for the new dispatch receiver
         */
        MOVE_TO_CLAS_UNCHANGED
    }

    private val propertyGraph = PropertyGraph()
    private val modeProperty = propertyGraph.property(
        initial = if (candidates.isEmpty()) Mode.MOVE_TO_CLAS_UNCHANGED else Mode.CONVERT_PARAMETER_TO_DISPATCH_RECEIVER
    )
    private var mode: Mode by modeProperty

    /**
     * Class chooser is disabled and automatically filled for [Mode.CONVERT_PARAMETER_TO_DISPATCH_RECEIVER].
     */
    private val classChooserPredicate = AtomicBooleanProperty(mode == Mode.MOVE_TO_CLAS_UNCHANGED)

    private val candidateList: JBList<TargetClassCandidateParameter> = object : JBList<TargetClassCandidateParameter>(candidates) {
        // A workaround for the empty list that doesn't react to `visibleRowCont` and shows the default 8 rows regardless
        override fun getPreferredScrollableViewportSize(): Dimension? {
            val result = super.getPreferredScrollableViewportSize()
            val rowCount = visibleRowCount
            val size = model.size
            if (rowCount < size) {
                return result
            }
            result.height = preferredSize.height
            return result
        }
    }.apply {
        border = null
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = ReceiverCandidateRenderer()
        emptyText.text = KotlinBundle.message("no.suitable.parameters")
        if (candidates.isNotEmpty()) selectedIndex = 0
        addListSelectionListener { selectionEvent ->
            if (!selectionEvent.valueIsAdjusting) {
                classChooser.text = candidateList.selectedValue?.targetClassFqName?.asString().orEmpty()
            }
        }
    }

    private val classChooser: TextFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
        addActionListener { browseTargetClass() }
        text = candidateList.selectedValue?.targetClassFqName?.asString().orEmpty()
    }

    init {
        title = KotlinBundle.message("refactoring.move.to.class.dialog.title")
        setOKButtonText(RefactoringBundle.message("refactor.button"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        buttonsGroup(title = KotlinBundle.message("destination.class"), indent = false) {
            row {
                layout(RowLayout.LABEL_ALIGNED)
                radioButton(
                    text = KotlinBundle.message("label.text.parameter"),
                    value = Mode.CONVERT_PARAMETER_TO_DISPATCH_RECEIVER,
                ).align(AlignY.TOP).applyToComponent {
                    border = JBUI.Borders.emptyTop(UIUtil.DEFAULT_VGAP)
                    addItemListener {
                        if (it.stateChange == ItemEvent.SELECTED) {
                            classChooserPredicate.set(false)
                            if (candidateList.isSelectionEmpty && !candidateList.isEmpty) {
                                candidateList.selectedIndex = 0
                            }
                        }
                    }
                }.enabled(!candidateList.isEmpty)
                cell(JBScrollPane(candidateList).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                }).enabledIf(classChooserPredicate.not()).align(AlignX.FILL).align(AlignY.TOP).resizableColumn()
            }
            row {
                layout(RowLayout.LABEL_ALIGNED)
                radioButton(
                    text = KotlinBundle.message("label.text.qualified.name"),
                    value = Mode.MOVE_TO_CLAS_UNCHANGED,
                ).align(AlignY.TOP).applyToComponent {
                    border = JBUI.Borders.emptyTop(UIUtil.DEFAULT_VGAP)
                    addItemListener {
                        classChooserPredicate.set(it.stateChange == ItemEvent.SELECTED)
                        candidateList.removeSelectionInterval(candidateList.selectedIndex, candidateList.selectedIndex)
                    }
                }
                cell(classChooser)
                    .enabledIf(classChooserPredicate)
                    .align(AlignX.FILL)
                    .resizableColumn()
            }

        }.bind(::mode.toMutableProperty(), Mode::class.java)
    }.withPreferredWidth(PREFERRED_DIALOG_WIDTH_UNSCALED)

    override fun getPreferredFocusedComponent(): JComponent = classChooser

    override fun doOKAction() {
        super.doOKAction()
        performMove()
    }

    private fun performMove() {
        val targetClass = currentTargetClass() ?: return
        val functionToMove = declaration as? KtNamedFunction ?: return // support only functions in the first version
        val editor = FileEditorManager.getInstance(project)
            .getSelectedEditor(functionToMove.containingFile.virtualFile) ?: return

        when(mode) {
            Mode.CONVERT_PARAMETER_TO_DISPATCH_RECEIVER -> {
                candidateList.selectedValue?.let { targetClassCandidate ->
                    runMoveToClassWithConversionCommand(project, targetClassCandidate, functionToMove, targetClass, editor)
                }
            }

            Mode.MOVE_TO_CLAS_UNCHANGED -> moveToClass(functionToMove, targetClass)
        }
    }

    private fun currentTargetClass(): KtClassOrObject? {
        val text = classChooser.text.trim()
        if (text.isEmpty()) return null
        val psiClass: PsiClass = JavaPsiFacade.getInstance(project).findClass(text, project.projectScope()) ?: return null
        return psiClass.unwrapped as? KtClassOrObject
    }

    private fun browseTargetClass() {
        val chooser = TreeJavaClassChooserDialog(
            RefactoringBundle.message("choose.destination.class"),
            project,
            project.projectScope().restrictToKotlinSources(),
            { clazz -> (clazz as? KtLightClass)?.kotlinOrigin != null },
            null,
            null,
            false,
        )
        chooser.showDialog()
        val chosen = chooser.selected?.unwrapped as? KtClassOrObject ?: return
        classChooser.text = chosen.fqName?.asString().orEmpty()
    }

    private class ReceiverCandidateRenderer : ColoredListCellRenderer<TargetClassCandidateParameter>() {
        override fun customizeCellRenderer(
            list: JList<out TargetClassCandidateParameter>,
            value: TargetClassCandidateParameter?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            if (value == null) return
            val nameAttributes = if (list.isEnabled) SimpleTextAttributes.REGULAR_ATTRIBUTES else SimpleTextAttributes.GRAYED_ATTRIBUTES
            append(value.displayName, nameAttributes)
            if (value.typeText.isNotEmpty()) {
                append(": ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                append(value.typeText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
}
