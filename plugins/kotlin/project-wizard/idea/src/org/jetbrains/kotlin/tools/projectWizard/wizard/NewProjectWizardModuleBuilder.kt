// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.actions.NewProjectAction
import com.intellij.ide.util.projectWizard.*
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.SystemProperties
import com.intellij.util.ui.EDT
import org.jetbrains.kotlin.idea.statistics.WizardStatsService
import org.jetbrains.kotlin.idea.statistics.WizardStatsService.ProjectCreationStats
import org.jetbrains.kotlin.idea.statistics.WizardStatsService.UiEditorUsageStats
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.div
import org.jetbrains.kotlin.tools.projectWizard.core.entity.StringValidators
import org.jetbrains.kotlin.tools.projectWizard.core.entity.ValidationResult
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.core.isSuccess
import org.jetbrains.kotlin.tools.projectWizard.core.onFailure
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.Plugins
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaJpsWizardService
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaServices
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.asHtml
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.firstStep.FirstWizardStepComponent
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.runWithProgressBar
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.secondStep.SecondStepWizardComponent
import java.io.File
import java.util.*
import java.util.regex.Pattern
import javax.swing.JButton
import javax.swing.JComponent
import com.intellij.openapi.module.Module as IdeaModule

/*
Have to override EmptyModuleBuilder here instead of just ModuleBuilder
As EmptyModuleBuilder has not expert panel option which are redundant
 */
class NewProjectWizardModuleBuilder : EmptyModuleBuilder() {
    val wizard = IdeWizard(Plugins.allPlugins, IdeaServices.PROJECT_INDEPENDENT, isUnitTestMode = false)
    private val uiEditorUsagesStats = UiEditorUsageStats()

    override fun isOpenProjectSettingsAfter(): Boolean = false
    override fun canCreateModule(): Boolean = false
    override fun getPresentableName(): String = moduleType.name
    override fun getDescription(): String = moduleType.description
    override fun getGroupName(): String = moduleType.name
    override fun isTemplateBased(): Boolean = false

    override fun getWeight(): Int {
        return ModuleBuilder.KOTLIN_WEIGHT
    }

    companion object {
        const val MODULE_BUILDER_ID = "kotlin.newProjectWizard.builder"
        private val projectNameValidator = StringValidators.shouldBeValidIdentifier("Project name", setOf('-', '_'))
        private val INVALID_PROJECT_NAME_MESSAGE
            @NlsContexts.DialogTitle
            get() = KotlinNewProjectWizardUIBundle.message("dialog.title.invalid.project.name")
    }

    override fun isAvailable(): Boolean = isCreatingNewProject()

    private lateinit var wizardContext: WizardContext
    private var finishButtonClicked: Boolean = false

    override fun getModuleType(): ModuleType<*> = NewProjectWizardModuleType()
    override fun getParentGroup(): String = "Kotlin Group"

    override fun createWizardSteps(
        wizardContext: WizardContext,
        modulesProvider: ModulesProvider
    ): Array<ModuleWizardStep> {
        this.wizardContext = wizardContext
        val disposable = wizardContext.disposable
        return arrayOf(ModuleNewWizardSecondStep(wizard, uiEditorUsagesStats, wizardContext, disposable))
    }

    override fun commit(
        project: Project,
        model: ModifiableModuleModel?,
        modulesProvider: ModulesProvider?
    ): List<IdeaModule>? {
        runWriteAction {
            wizard.jdk?.let { jdk -> JavaSdkUtil.applyJdkToProject(project, jdk) }
        }
        val modulesModel = model ?: ModuleManager.getInstance(project).getModifiableModel()
        val success = wizard.apply(
            services = buildList {
                +IdeaServices.createScopeDependent(project)
                +IdeaServices.PROJECT_INDEPENDENT
                +IdeaJpsWizardService(project, modulesModel, this@NewProjectWizardModuleBuilder, wizard)
            },
            phases = GenerationPhase.startingFrom(GenerationPhase.FIRST_STEP)
        ).onFailure { errors ->
            val errorMessages = errors.joinToString(separator = "\n") { it.message }
            Messages.showErrorDialog(project, errorMessages, KotlinNewProjectWizardUIBundle.message("error.generation"))
        }.isSuccess
        if (success) {
            logToFUS(project)
            scheduleSampleFilesOpening(project)
        }

        return when {
            !success -> null
            wizard.buildSystemType == BuildSystemType.Jps -> runWriteAction {
                modulesModel.modules.toList().onEach { setupModule(it) }
            }
            else -> emptyList()
        }
    }

    private fun scheduleSampleFilesOpening(project: Project) = StartupManager.getInstance(project).runAfterOpened {
        // From javadoc of StartupManager.runAfterOpened:
        //      ... that is executed on pooled thread after project is opened.
        //      The runnable will be executed in current thread if project is already opened.

        // The latter literally means EDT. New module addition is an example of the case.

        fun openSampleFiles() {
            val pathname = project.basePath ?: return
            val projectPath = File(pathname)

            val wizardModules = wizard.context.read { KotlinPlugin.modules.settingValue }
                .flatMap { module ->
                    buildList<Module> {
                        +module.subModules
                        +module
                    }
                }

            // Might take time. Should be executed in a background thread.
            val filesToOpen = wizardModules
                .flatMap { it.template?.filesToOpenInEditor ?: emptyList() }
                .mapNotNull { expectedFileName ->
                    val file = FileUtil.findFilesByMask(Pattern.compile(Pattern.quote(expectedFileName)), projectPath).firstOrNull()
                    file?.let { VirtualFileManager.getInstance().findFileByNioPath(file.toPath()) }
                }

            ApplicationManager.getApplication().invokeLater {
                filesToOpen.forEach {
                    FileEditorManager.getInstance(project).openFile(it, true)
                }
            }
        }

        if (EDT.isCurrentThreadEdt()) {
            ApplicationManager.getApplication().executeOnPooledThread(::openSampleFiles)
        } else {
            openSampleFiles()
        }
    }

    private fun logToFUS(project: Project?) {
        val modules = wizard.context.read {
            KotlinPlugin.modules.reference.settingValue
        }
        val projectCreationStats = ProjectCreationStats(
            "Kotlin",
            wizard.projectTemplate!!.id,
            wizard.buildSystemType!!.id,
            modules.map { it.configurator.id },
        )
        WizardStatsService.logDataOnProjectGenerated(
            wizard.context.contextComponents.get(),
            project,
            projectCreationStats,
            uiEditorUsagesStats
        )

        val moduleTemplates = modules.map { module ->
            module.template?.id ?: "none"
        }
        WizardStatsService.logUsedModuleTemplatesOnNewWizardProjectCreated(
            wizard.context.contextComponents.get(),
            project,
            wizard.projectTemplate!!.id,
            moduleTemplates,
        )
    }

    private fun clickFinishButton() {
        if (finishButtonClicked) return
        finishButtonClicked = true
        wizardContext.getNextButton()?.doClick()
    }

    override fun modifySettingsStep(settingsStep: SettingsStep): ModuleWizardStep? {
        settingsStep.moduleNameLocationSettings?.apply {
            moduleName = wizard.projectName!!
            moduleContentRoot = wizard.projectPath!!.toString()
        }
        clickFinishButton()
        return null
    }

    override fun validateModuleName(moduleName: String): Boolean {
        when (val validationResult = wizard.context.read {
            projectNameValidator.validate(this, moduleName)
        }) {
            ValidationResult.OK -> return true
            is ValidationResult.ValidationError -> {
                val message = validationResult.messages.firstOrNull() ?: INVALID_PROJECT_NAME_MESSAGE
                throw ConfigurationException(message, INVALID_PROJECT_NAME_MESSAGE)
            }
        }
    }

    internal fun selectProjectTemplate(template: ProjectTemplate) {
        wizard.buildSystemType = BuildSystemType.GradleKotlinDsl
        wizard.projectTemplate = template
    }

    override fun getCustomOptionsStep(context: WizardContext?, parentDisposable: Disposable) =
        ModuleNewWizardFirstStep(wizard, parentDisposable)
}

abstract class WizardStep(protected val wizard: IdeWizard, private val phase: GenerationPhase) : ModuleWizardStep() {
    override fun getHelpId(): String = HELP_ID

    override fun updateDataModel() = Unit // model is updated on every UI action
    override fun validate(): Boolean =
        when (val result = wizard.context.read { with(wizard) { validate(setOf(phase)) } }) {
            ValidationResult.OK -> true
            is ValidationResult.ValidationError -> {
                handleErrors(result)
                false
            }
        }

    protected open fun handleErrors(error: ValidationResult.ValidationError) {
        throw ConfigurationException(error.asHtml(), KotlinNewProjectWizardUIBundle.message("dialog.title.validation.error"))
    }

    companion object {
        private const val HELP_ID = "new_project_wizard_kotlin"
    }
}

class ModuleNewWizardFirstStep(wizard: IdeWizard, disposable: Disposable) : WizardStep(wizard, GenerationPhase.FIRST_STEP) {
    private val component = FirstWizardStepComponent(wizard)
    override fun getComponent(): JComponent = component.component

    init {
        runPreparePhase()
        initDefaultValues()
        component.onInit()
        Disposer.register(disposable, component)
    }

    private fun runPreparePhase() = runWithProgressBar(title = "") {
        wizard.apply(emptyList(), setOf(GenerationPhase.PREPARE)) { task ->
            ProgressManager.getInstance().progressIndicator.text = task.title ?: ""
        }
    }

    override fun handleErrors(error: ValidationResult.ValidationError) {
        component.navigateTo(error)
    }

    private fun initDefaultValues() {
        val suggestedProjectParentLocation = RecentProjectsManager.getInstance().suggestNewProjectLocation()
        val suggestedProjectName = ProjectWizardUtil.findNonExistingFileName(suggestedProjectParentLocation, "untitled", "")
        wizard.context.writeSettings {
            StructurePlugin.name.reference.setValue(suggestedProjectName)
            StructurePlugin.projectPath.reference.setValue(suggestedProjectParentLocation / suggestedProjectName)
            StructurePlugin.artifactId.reference.setValue(suggestedProjectName)

            if (StructurePlugin.groupId.notRequiredSettingValue == null) {
                StructurePlugin.groupId.reference.setValue(suggestGroupId())
            }
        }
    }

    private fun suggestGroupId(): String {
        val username = SystemProperties.getUserName() ?: return DEFAULT_GROUP_ID
        if (!username.matches("[\\w\\s]+".toRegex())) return DEFAULT_GROUP_ID
        val usernameAsGroupId = username.trim().toLowerCase(Locale.US).split("\\s+".toRegex()).joinToString(separator = ".")
        return "me.$usernameAsGroupId"
    }

    companion object {
        private const val DEFAULT_GROUP_ID = "me.user"
    }
}

class ModuleNewWizardSecondStep(
    wizard: IdeWizard,
    uiEditorUsagesStats: UiEditorUsageStats,
    private val wizardContext: WizardContext,
    disposable: Disposable
) : WizardStep(wizard, GenerationPhase.SECOND_STEP) {
    private val component = SecondStepWizardComponent(wizard, uiEditorUsagesStats)
    override fun getComponent(): JComponent = component.component

    init {
        Disposer.register(disposable, component)
    }

    override fun _init() {
        component.onInit()
        WizardStatsService.logDataOnNextClicked(wizard.context.contextComponents.get())
    }

    override fun onStepLeaving() {
        if (isNavigatingBack()) {
            WizardStatsService.logDataOnPrevClicked(wizard.context.contextComponents.get())
        }
        super.onStepLeaving()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        wizardContext.getNextButton()?.text = KotlinNewProjectWizardUIBundle.message("finish.button.text")
        return super.getPreferredFocusedComponent()
    }

    override fun handleErrors(error: ValidationResult.ValidationError) {
        component.navigateTo(error)
    }
}

private fun isCreatingNewProject() = Thread.currentThread().stackTrace.any { element ->
    element.className == NewProjectAction::class.java.name
}

private fun isNavigatingBack() = Thread.currentThread().stackTrace.any { element ->
    element.methodName == "doPreviousAction"
}

private fun WizardContext.getNextButton() = try {
    AbstractWizard::class.java.getDeclaredMethod("getNextButton")
        .also { it.isAccessible = true }
        .invoke(getUserData(AbstractWizard.KEY)) as? JButton
} catch (_: Throwable) {
    null
}
