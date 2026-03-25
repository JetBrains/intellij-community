// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.actions

import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.actions.CreateTemplateInPackageAction
import com.intellij.ide.actions.ElementCreator
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil
import com.intellij.ide.ui.newItemPopup.NewItemWithTemplatesPopupPanel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KOTLIN_AWARE_SOURCE_ROOT_TYPES
import org.jetbrains.kotlin.idea.core.script.v1.kotlinScriptTemplateInfo
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.Icon
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.ide

internal class NewKotlinScriptAction : AbstractNewKotlinFileAction(), DumbAware {
    override fun isAvailable(dataContext: DataContext): Boolean {
        if (!super.isAvailable(dataContext)) return false

        val ideView = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return false
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex

        if (ideView.directories.any {
                projectFileIndex.isUnderSourceRootOfType(it.virtualFile, KOTLIN_AWARE_SOURCE_ROOT_TYPES) ||
                        CreateTemplateInPackageAction.isInContentRoot(it.virtualFile, projectFileIndex)
            }) return false

        val firstDirectory = ideView.directories.firstOrNull() ?: return true
        val definitions = project.service<ScriptDefinitionProvider>().currentDefinitions.toList()
        if (definitions.isEmpty()) return true

        return definitions.any { isScriptLocationAccepted(it, firstDirectory.virtualFile, project) }
    }

    override fun createDialogBuilder(project: Project, dataContext: DataContext): CreateFileFromTemplateDialog.Builder {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return super.createDialogBuilder(project, dataContext)
        }
        val directory = LangDataKeys.IDE_VIEW.getData(dataContext)?.directories?.firstOrNull()
        return NewKotlinScriptDialogBuilder(project, collectScriptTypes(project, directory))
    }

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle(KotlinBundle.message("action.new.script.dialog.title"))

        val definitions = project.service<ScriptDefinitionProvider>().currentDefinitions.toList()
        if (definitions.isNotEmpty()) {
            definitions
                .filter { isScriptLocationAccepted(it, directory.virtualFile, project) }
                .mapNotNull { it.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplateInfo] }
                .distinct()
                .forEach { builder.addKind(it.title, it.icon, it.templateName) }
        } else {
            builder
                .addKind(KotlinScriptFileTemplate.GradleKts)
                .addKind(KotlinScriptFileTemplate.MainKts)
                .addKind(KotlinScriptFileTemplate.Kts)
        }

        builder.setValidator(NewKotlinFileNameValidator)
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String =
        KotlinBundle.message("action.Kotlin.NewScript.text")

    override fun postProcess(
        createdElement: PsiFile,
        templateName: String?,
        customProperties: Map<String, String>?
    ) {
        val project = createdElement.project
        val virtualFile = createdElement.virtualFile ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return
        if (editor.document == document) {
            editor.caretModel.moveToOffset(document.textLength)
        }
    }

    private fun collectScriptTypes(project: Project, directory: PsiDirectory?): List<ScriptTypeItem> {
        val definitions = project.service<ScriptDefinitionProvider>().currentDefinitions.toList()
        if (definitions.isEmpty()) {
            return KotlinScriptFileTemplate.entries.map {
                ScriptTypeItem(it.title, it.icon, it.fileName, it.description)
            }
        }

        return definitions
            .filter { directory == null || isScriptLocationAccepted(it, directory.virtualFile, project) }
            .mapNotNull { it.compilationConfiguration[ScriptCompilationConfiguration.ide.kotlinScriptTemplateInfo] }
            .distinct()
            .map { ScriptTypeItem(it.title, it.icon, it.templateName, it.description) }
    }

}

internal data class ScriptTypeItem(
    @NlsContexts.ListItem val title: String,
    val icon: Icon?,
    val templateName: String,
    val description: @Nls String
)

internal class NewKotlinScriptPopupPanel(scriptTypes: List<ScriptTypeItem>) :
    NewItemWithTemplatesPopupPanel<ScriptTypeItem>(scriptTypes, createScriptTypeListCellRenderer()) {

    private val descriptionLabel = javax.swing.JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.compound(
            JBUI.Borders.customLineTop(JBUI.CurrentTheme.NewClassDialog.bordersColor()),
            JBUI.Borders.empty(6, 8)
        )
        isVisible = false
        putClientProperty(javax.swing.JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    }

    init {
        add(descriptionLabel, BorderLayout.SOUTH)

        if (scriptTypes.isNotEmpty()) {
            myTemplatesList.selectedIndex = 0
            updateDescription(scriptTypes[0].description)
        }

        myTemplatesList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting) {
                myTemplatesList.selectedValue?.let { updateDescription(it.description) }
            }
        }

        setTemplatesListVisible(scriptTypes.size > 1)
    }

    val enteredName: String get() = myTextField.text.trim()
    val selectedTemplateName: String get() = myTemplatesList.selectedValue?.templateName ?: ""

    private fun updateDescription(@Nls description: String) {
        if (description.isNotEmpty()) {
            descriptionLabel.text = "<html>$description</html>"
            descriptionLabel.isVisible = true
        } else {
            descriptionLabel.isVisible = false
        }
    }
}

private fun createScriptTypeListCellRenderer(): javax.swing.ListCellRenderer<ScriptTypeItem> =
    SimpleListCellRenderer.create { label, value, _ ->
        if (value != null) {
            label.text = value.title
            label.icon = value.icon
        }
    }

internal class NewKotlinScriptDialogBuilder(
    private val project: Project,
    private val scriptTypes: List<ScriptTypeItem>
) : CreateFileFromTemplateDialog.Builder {

    @get:NlsContexts.PopupTitle
    private var title: String = ""
    private var validator: InputValidator? = null

    override fun setTitle(title: String): CreateFileFromTemplateDialog.Builder = apply { this.title = title }
    override fun setValidator(validator: InputValidator): CreateFileFromTemplateDialog.Builder = apply { this.validator = validator }
    override fun setDefaultText(text: String): CreateFileFromTemplateDialog.Builder = this
    override fun setDialogOwner(owner: Component?): CreateFileFromTemplateDialog.Builder = this
    override fun getCustomProperties(): Map<String, String>? = null

    override fun addKind(
        kind: String,
        icon: Icon?,
        templateName: String,
        extraValidator: InputValidator?
    ): CreateFileFromTemplateDialog.Builder = this

    override fun <T : PsiElement?> show(
        errorTitle: String,
        selectedItem: String?,
        creator: CreateFileFromTemplateDialog.FileCreator<T>
    ): Nothing = throw UnsupportedOperationException("Modal dialog is not supported by this builder")

    override fun <T : PsiElement?> show(
        errorTitle: String,
        selectedItem: String?,
        fileCreator: CreateFileFromTemplateDialog.FileCreator<T>,
        elementConsumer: Consumer<in T>
    ) {
        val panel = NewKotlinScriptPopupPanel(scriptTypes)
        val popup = NewItemPopupUtil.createNewItemPopup(title, panel, panel.textField)
        val currentValidator = validator

        val elementCreator = object : ElementCreator(project, errorTitle) {
            override fun create(newName: String): Array<PsiElement> {
                val element = fileCreator.createFile(newName, panel.selectedTemplateName)
                return if (element != null) arrayOf(element) else PsiElement.EMPTY_ARRAY
            }

            override fun startInWriteAction(): Boolean = fileCreator.startInWriteAction()

            override fun getActionName(newName: String): String =
                fileCreator.getActionName(newName, panel.selectedTemplateName)
        }

        panel.setApplyAction { event ->
            val name = panel.enteredName
            if (name.isNotBlank()) {
                val isValid = currentValidator?.canClose(name) != false
                if (isValid) {
                    popup.closeOk(event)
                    val elements = elementCreator.tryCreate(name)
                    @Suppress("UNCHECKED_CAST")
                    elementConsumer.consume(elements.firstOrNull() as? T)
                } else {
                    val errorMsg = (currentValidator as? InputValidatorEx)?.getErrorText(name) ?: ""
                    panel.setError(errorMsg)
                }
            }
        }

        popup.showCenteredInCurrentWindow(project)
    }
}

internal enum class KotlinScriptFileTemplate(
    @NlsContexts.ListItem override val title: String,
    override val icon: Icon,
    override val fileName: String,
    val description: @Nls String
) : KotlinTemplate {
    GradleKts(
        ".gradle.kts",
        KotlinIcons.GRADLE_SCRIPT,
        "Kotlin Script Gradle",
        KotlinBundle.message("action.new.script.description.gradle.kts")
    ),
    MainKts(
        ".main.kts",
        KotlinIcons.SCRIPT,
        "Kotlin Script MainKts",
        KotlinBundle.message("action.new.script.description.main.kts")
    ),
    Kts(
        ".kts",
        KotlinIcons.SCRIPT,
        "Kotlin Script",
        KotlinBundle.message("action.new.script.description.kts")
    ),
}

fun isScriptLocationAccepted(
    definition: ScriptDefinition,
    directory: VirtualFile,
    project: Project,
): Boolean {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val scriptAcceptedLocations = definition.compilationConfiguration[ScriptCompilationConfiguration.ide.acceptedLocations]
    if (scriptAcceptedLocations.isNullOrEmpty()) return true

    return scriptAcceptedLocations.any { location ->
        when (location) {
            ScriptAcceptedLocation.Everywhere -> true
            ScriptAcceptedLocation.Project -> fileIndex.isInContent(directory)
            ScriptAcceptedLocation.Sources -> fileIndex.isUnderSourceRootOfType(directory, KOTLIN_AWARE_SOURCE_ROOT_TYPES)
                    && !TestSourcesFilter.isTestSources(directory, project)
            ScriptAcceptedLocation.Tests -> TestSourcesFilter.isTestSources(directory, project)
            ScriptAcceptedLocation.Libraries -> fileIndex.isInLibrary(directory)
        }
    }
}
