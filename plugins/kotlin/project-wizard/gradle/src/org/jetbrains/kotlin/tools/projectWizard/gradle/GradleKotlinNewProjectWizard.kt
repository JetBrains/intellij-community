// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsChangedEvent
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.AssetsJavaNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.io.createDirectories
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootMap
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.getGradleKotlinVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.GradleBuildScriptSupport
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.*
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard.Companion.DEFAULT_KOTLIN_VERSION
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinGradleCompatibilityStore
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinWizardVersionStore
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.wizard.AssetsKotlinNewProjectWizardStep
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaKotlinVersionProviderService
import org.jetbrains.plugins.gradle.service.project.wizard.AbstractGradleModuleBuilder
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep
import java.nio.file.Path

private class GradleKotlinModuleBuilder : AbstractGradleModuleBuilder()

internal class GradleKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = GRADLE

    override val ordinal = 200

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent)
            .nextStep(::AssetsStep)

    class Step(parent: KotlinNewProjectWizard.Step) :
        GradleNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent),
        GradleKotlinNewProjectWizardData,
        BuildSystemKotlinNewProjectWizardData by parent {

        init {
            data.putUserData(GradleKotlinNewProjectWizardData.KEY, this)
        }

        override val addSampleCodeProperty = propertyGraph.property(true)
            .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

        override var addSampleCode by addSampleCodeProperty

        override val generateOnboardingTipsProperty = propertyGraph.property(AssetsJavaNewProjectWizardStep.proposeToGenerateOnboardingTipsByDefault())
            .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

        override var generateOnboardingTips by generateOnboardingTipsProperty

        private fun setupSampleCodeUI(builder: Panel) {
            builder.row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(addSampleCodeProperty)
                    .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
            }
        }

        private fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
            builder.indent {
                row {
                    checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
                        .bindSelected(generateOnboardingTipsProperty)
                        .whenStateChangedFromUi { logAddSampleOnboardingTipsChangedEvent(it) }
                }
            }.enabledIf(addSampleCodeProperty)
        }

        override fun setupSettingsUI(builder: Panel) {
            setupJavaSdkUI(builder)
            setupGradleDslUI(builder)
            setupParentsUI(builder)
            setupSampleCodeUI(builder)
            setupSampleCodeWithOnBoardingTipsUI(builder)
            addMultiPlatformLink(builder)
        }

        override fun validateLanguageCompatibility(gradleVersion: GradleVersion): Boolean {
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(IdeKotlinVersion.get(kotlinVersion), gradleVersion)
        }

        override fun validateLanguageCompatibility(
            builder: ValidationInfoBuilder,
            gradleVersion: GradleVersion,
            withDialog: Boolean
        ): ValidationInfo? {
            if (validateLanguageCompatibility(gradleVersion)) return null
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return builder.validationWithDialog(
                withDialog = withDialog,
                message = KotlinNewProjectWizardBundle.message(
                    "gradle.project.settings.distribution.version.kotlin.unsupported",
                    kotlinVersion,
                    gradleVersion.version
                ),
                dialogTitle = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.kotlin.title",
                    context.isCreatingNewProjectInt
                ),
                dialogMessage = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.kotlin.message",
                    kotlinVersion,
                    gradleVersion.version
                )
            )
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGradleDistributionUI(builder)
            setupGroupIdUI(builder)
            setupArtifactIdUI(builder)
        }

        private var selectedJdkJvmTarget: Int? = null

        private fun findSelectedJvmTarget() {
            // Ordinal here works correctly, starting at Java 1.0 (0)
            selectedJdkJvmTarget = sdk?.let { JavaSdk.getInstance().getVersion(it) }?.ordinal
        }

        private var canUseFoojay = false

        private fun checkCanUseFoojay() {
            val parsedKotlinVersionToUse = IdeKotlinVersion.parse(kotlinVersionToUse).getOrNull()
            val parsedMinKotlinFoojayVersion = IdeKotlinVersion.parse(Versions.GRADLE_PLUGINS.MIN_KOTLIN_FOOJAY_VERSION.text).getOrNull()
            if (parsedMinKotlinFoojayVersion != null && parsedKotlinVersionToUse != null &&
                parsedKotlinVersionToUse < parsedMinKotlinFoojayVersion
            ) {
                canUseFoojay = false
                return
            }

            val minGradleFoojayVersion = GradleVersion.version(Versions.GRADLE_PLUGINS.MIN_GRADLE_FOOJAY_VERSION.text)
            val maxJvmTarget = parsedKotlinVersionToUse?.let {
                KotlinGradleCompatibilityStore.getMaxJvmTarget(it)
            } ?: 11
            selectedJdkJvmTarget?.let {
                if (it > maxJvmTarget) {
                    canUseFoojay = false
                    return
                }
            }
            val currentGradleVersion = GradleVersion.version(gradleVersion)
            canUseFoojay = currentGradleVersion >= minGradleFoojayVersion
        }

        // The default value is actually never used, it is just here to avoid the variable being nullable.
        private var kotlinVersionToUse: String = DEFAULT_KOTLIN_VERSION

        private fun findKotlinVersionToUse(project: Project) {
            val latestKotlinVersion = KotlinWizardVersionStore.getInstance().state?.kotlinPluginVersion ?: DEFAULT_KOTLIN_VERSION
            kotlinVersionToUse = latestKotlinVersion
            if (isCreatingNewRootModule()) {
                return
            }

            val parentModule = project.findParentModule()?.baseModule ?: return
            val parsedGradleVersion = GradleVersion.version(gradleVersion) ?: return
            kotlinVersionToUse =
                KotlinWithGradleConfigurator.findBestKotlinVersion(parentModule, parsedGradleVersion)?.rawVersion ?: latestKotlinVersion
        }

        private fun initializeProjectValues(project: Project) {
            findSelectedJvmTarget()
            findKotlinVersionToUse(project)
            checkCanUseFoojay()
        }

        private fun configureSettingsFile(project: Project, settingsFile: VirtualFile) {
            if (!canUseFoojay) return
            val psiFile = settingsFile.findPsiFile(project) ?: return
            val document = settingsFile.findDocument() ?: return
            val psiDocumentManager = PsiDocumentManager.getInstance(project)
            psiDocumentManager.commitDocument(document)

            CommandProcessor.getInstance().executeCommand(project, {
                ApplicationManager.getApplication().runWriteAction {
                    val buildScriptSupport = GradleBuildScriptSupport.getManipulator(psiFile)
                    buildScriptSupport.addFoojayPlugin(psiFile)
                }
            }, KotlinNewProjectWizardBundle.message("module.configurator.command"), null)
        }

        private fun isCreatingNewRootModule(): Boolean {
            return context.isCreatingNewProject || parentData == null
        }

        private fun Project.findParentModule(): ModuleSourceRootGroup? {
            if (isCreatingNewRootModule()) return null
            val moduleGroups = ModuleSourceRootMap(this)
            return moduleGroups.groupByBaseModules(modules.toList())
                .mapNotNull {
                    val externalPath = ExternalSystemApiUtil.getExternalProjectPath(it.baseModule) ?: return@mapNotNull null
                    it to externalPath
                }
                .filter { (_, externalPath) -> path.startsWith(externalPath) }
                .maxByOrNull { (_, externalPath) ->
                    path.commonPrefixWith(externalPath).length
                }?.first
        }

        private fun getPluginManagementKotlinVersion(project: Project): IdeKotlinVersion? {
            if (isCreatingNewRootModule()) return null
            val parentModule = project.findParentModule()?.baseModule ?: return null

            return KotlinWithGradleConfigurator.getPluginManagementVersion(parentModule)?.parsedVersion
        }

        override fun setupProject(project: Project) {
            initializeProjectValues(project)

            val moduleBuilder = GradleKotlinModuleBuilder()
            moduleBuilder.configurePreImport { _, settingsScriptFile ->
                configureSettingsFile(project, settingsScriptFile)
            }
            moduleBuilder.setCreateEmptyContentRoots(false)

            val parentKotlinVersion = project.findParentModule()?.sourceRootModules?.firstNotNullOfOrNull {
                it.getGradleKotlinVersion()
            }
            val pluginManagementVersion = getPluginManagementKotlinVersion(project)
            linkGradleProject(project, moduleBuilder) {
                withKotlinJvmPlugin(kotlinVersionToUse.takeIf { pluginManagementVersion == null && parentKotlinVersion == null })
                withKotlinTest()

                selectedJdkJvmTarget?.takeIf { canUseFoojay }?.let {
                    withKotlinJvmToolchain(it)
                }
            }
        }
    }

    private class AssetsStep(private val parent: Step) : AssetsKotlinNewProjectWizardStep(parent) {
        private fun createKotlinContentRoots() {
            val directories = listOf(
                "$outputDirectory/$SRC_MAIN_KOTLIN_PATH",
                "$outputDirectory/$SRC_MAIN_RESOURCES_PATH",
                "$outputDirectory/$SRC_TEST_KOTLIN_PATH",
                "$outputDirectory/$SRC_TEST_RESOURCES_PATH",
            )
            directories.forEach {
                Path.of(it).createDirectories()
            }
        }

        private fun shouldAddOnboardingTips(): Boolean = parent.addSampleCode && parent.generateOnboardingTips

        override fun setupAssets(project: Project) {
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
                addTemplateAsset("gradle.properties", "KotlinCodeStyleProperties")
            }
            createKotlinContentRoots()
            if (parent.addSampleCode) {
                withKotlinSampleCode(SRC_MAIN_KOTLIN_PATH, parent.groupId, shouldAddOnboardingTips())
            }
        }

        override fun setupProject(project: Project) {
            if (shouldAddOnboardingTips()) {
                prepareOnboardingTips(project)
            }
            super.setupProject(project)
        }
    }
}