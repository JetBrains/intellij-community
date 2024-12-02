// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logGenerateMultipleModulesChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logGenerateMultipleModulesFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.AssetsJava
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.proposeToGenerateOnboardingTipsByDefault
import com.intellij.ide.projectWizard.generators.AssetsOnboardingTips.shouldRenderOnboardingTips
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import com.intellij.ui.layout.ValidationInfoBuilder
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootMap
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.KotlinWithGradleConfigurator
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.getBuildScriptPsiFile
import org.jetbrains.kotlin.idea.gradleJava.kotlinGradlePluginVersion
import org.jetbrains.kotlin.tools.projectWizard.*
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard.Companion.DEFAULT_KOTLIN_VERSION
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinGradleCompatibilityStore
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinLibrariesCompatibilityStore
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinLibrariesCompatibilityStore.Companion.COROUTINES_ARTIFACT_ID
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinLibrariesCompatibilityStore.Companion.DATETIME_ARTIFACT_ID
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinLibrariesCompatibilityStore.Companion.KOTLINX_GROUP
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinLibrariesCompatibilityStore.Companion.SERIALIZATION_JSON_ARTIFACT_ID
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinWizardVersionStore
import org.jetbrains.kotlin.tools.projectWizard.core.KotlinAssetsProvider
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle
import org.jetbrains.kotlin.tools.projectWizard.wizard.prepareKotlinSampleOnboardingTips
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaKotlinVersionProviderService
import org.jetbrains.kotlin.tools.projectWizard.wizard.withKotlinSampleCode
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleAssetsNewProjectWizardStep
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep
import org.jetbrains.plugins.gradle.service.project.wizard.addGradleWrapperAsset
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SCRIPT_NAME
import org.jetbrains.plugins.gradle.util.GradleConstants.KOTLIN_DSL_SETTINGS_FILE_NAME

private const val GENERATE_MULTIPLE_MODULES_PROPERTY_NAME: String = "NewProjectWizard.generateMultipleModules"

private const val KOTLIN_GRADLE_PLUGIN_ID = "org.jetbrains.kotlin:kotlin-gradle-plugin"

private val MIN_GRADLE_VERSION_BUILD_SRC = GradleVersion.version("8.2")

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

        override val generateOnboardingTipsProperty = propertyGraph.property(proposeToGenerateOnboardingTipsByDefault())
            .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

        override var generateOnboardingTips by generateOnboardingTipsProperty

        private val generateMultipleModulesProperty = propertyGraph.property(false)
            .bindBooleanStorage(GENERATE_MULTIPLE_MODULES_PROPERTY_NAME)

        override var generateMultipleModules by generateMultipleModulesProperty

        internal val shouldGenerateMultipleModules
            get() = generateMultipleModules &&
                    gradleDsl == GradleDsl.KOTLIN &&
                    context.isCreatingNewProject &&
                    gradleVersionToUse >= MIN_GRADLE_VERSION_BUILD_SRC

        private fun setupSampleCodeUI(builder: Panel) {
            builder.row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(addSampleCodeProperty)
                    .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
                    .onApply { logAddSampleCodeFinished(addSampleCode) }
            }
        }

        private fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
            builder.indent {
                row {
                    checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
                        .bindSelected(generateOnboardingTipsProperty)
                        .whenStateChangedFromUi { logAddSampleOnboardingTipsChanged(it) }
                        .onApply { logAddSampleOnboardingTipsFinished(generateOnboardingTips) }
                }
            }.enabledIf(addSampleCodeProperty)
        }

        private fun setupMultipleModulesUI(builder: Panel) {
            builder.row {
                checkBox(KotlinNewProjectWizardUIBundle.message("label.project.wizard.new.project.generate.multiple.modules"))
                    .bindSelected(generateMultipleModulesProperty)
                    .enabledIf(gradleDslProperty.equalsTo(GradleDsl.KOTLIN))
                    .whenStateChangedFromUi { logGenerateMultipleModulesChanged(it) }
                    .onApply { logGenerateMultipleModulesFinished(generateMultipleModules) }

                contextHelp(KotlinNewProjectWizardUIBundle.message("tooltip.project.wizard.new.project.generate.multiple.modules"))
            }.visibleIf(gradleDslProperty.equalsTo(GradleDsl.KOTLIN))
        }

        override fun setupSettingsUI(builder: Panel) {
            setupJavaSdkUI(builder)
            setupGradleDslUI(builder)
            setupParentsUI(builder)
            setupSampleCodeUI(builder)
            setupSampleCodeWithOnBoardingTipsUI(builder)
            if (context.isCreatingNewProject) {
                setupMultipleModulesUI(builder)
                addMultiPlatformLink(builder)
            }
        }

        override fun validateGradleVersion(gradleVersion: GradleVersion): Boolean {
            return validateLanguageCompatibility(gradleVersion) && validateMultiModuleSupport(gradleVersion)
        }

        override fun validateGradleVersion(
            builder: ValidationInfoBuilder,
            gradleVersion: GradleVersion,
            withDialog: Boolean
        ): ValidationInfo? {
            return builder.validateLanguageCompatibility(gradleVersion, withDialog)
                ?: builder.validateMultiModuleSupport(gradleVersion, withDialog)
        }

        private fun validateLanguageCompatibility(gradleVersion: GradleVersion): Boolean {
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(IdeKotlinVersion.get(kotlinVersion), gradleVersion)
        }

        private fun ValidationInfoBuilder.validateLanguageCompatibility(
            gradleVersion: GradleVersion,
            withDialog: Boolean
        ): ValidationInfo? {
            if (validateLanguageCompatibility(gradleVersion)) return null
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return validationWithDialog(
                withDialog = withDialog,
                message = KotlinNewProjectWizardBundle.message(
                    "gradle.project.settings.distribution.version.kotlin.unsupported",
                    kotlinVersion,
                    gradleVersion.version
                ),
                dialogTitle = GradleBundle.message(
                    "gradle.settings.wizard.gradle.unsupported.title"
                ),
                dialogMessage = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.kotlin.message",
                    kotlinVersion,
                    gradleVersion.version
                )
            )
        }

        private fun validateMultiModuleSupport(gradleVersion: GradleVersion): Boolean {
            return !generateMultipleModules || gradleVersion >= MIN_GRADLE_VERSION_BUILD_SRC
        }

        private fun ValidationInfoBuilder.validateMultiModuleSupport(
            gradleVersion: GradleVersion,
            withDialog: Boolean
        ): ValidationInfo? {
            if (validateMultiModuleSupport(gradleVersion)) return null
            return errorWithDialog(
                withDialog = withDialog,
                message = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.multi.module.message",
                    gradleVersion.version,
                ),
                dialogTitle = GradleBundle.message(
                    "gradle.settings.wizard.gradle.unsupported.title"
                ),
                dialogMessage = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.multi.module.message",
                    gradleVersion.version,
                )
            )
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGradleDistributionUI(builder)
            setupGroupIdUI(builder)
            setupArtifactIdUI(builder)
        }

        var selectedJdkJvmTarget: Int? = null
            private set

        private fun resolveSelectedJvmTarget(): Int? {
            // Ordinal here works correctly, starting at Java 1.0 (0)
            return sdk?.let { JavaSdk.getInstance().getVersion(it) }?.ordinal
        }

        override fun resolveIsFoojayPluginSupported(): Boolean {
            if (!super.resolveIsFoojayPluginSupported()) {
                return false
            }

            val parsedKotlinVersionToUse = IdeKotlinVersion.parse(kotlinVersionToUse).getOrNull()
            val parsedMinKotlinFoojayVersion = IdeKotlinVersion.parse(Versions.GRADLE_PLUGINS.MIN_KOTLIN_FOOJAY_VERSION.text).getOrNull()
            if (parsedMinKotlinFoojayVersion != null && parsedKotlinVersionToUse != null &&
                parsedKotlinVersionToUse < parsedMinKotlinFoojayVersion
            ) {
                return false
            }

            val maxJvmTarget = parsedKotlinVersionToUse?.let {
                KotlinGradleCompatibilityStore.getMaxJvmTarget(it)
            } ?: 11
            selectedJdkJvmTarget?.let {
                if (it > maxJvmTarget) {
                    return false
                }
            }

            return true
        }

        lateinit var kotlinVersionToUse: String
            private set

        private fun resolveKotlinVersionToUse(project: Project): String {
            val kotlinPluginVersion = KotlinWizardVersionStore.getInstance().state?.kotlinPluginVersion ?: DEFAULT_KOTLIN_VERSION

            if (isCreatingNewLinkedProject) {
                return kotlinPluginVersion
            }

            val parentModule = project.findParentModule()?.baseModule ?: return kotlinPluginVersion
            val bestKotlinVersion = KotlinWithGradleConfigurator.findBestKotlinVersion(parentModule, gradleVersionToUse)
            return bestKotlinVersion?.rawVersion ?: kotlinPluginVersion
        }

        private fun Project.findParentModule(): ModuleSourceRootGroup? {
            if (isCreatingNewLinkedProject) return null
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

        fun usesParentKotlinVersion(project: Project): Boolean {
            val parentModule = project.findParentModule() ?: return false
            return parentModule.sourceRootModules.any { it.kotlinGradlePluginVersion != null }
        }

        fun usesPluginManagementKotlinVersion(project: Project): Boolean {
            if (isCreatingNewLinkedProject) return false
            val parentModule = project.findParentModule()?.baseModule ?: return false
            val version = KotlinWithGradleConfigurator.getPluginManagementVersion(parentModule) ?: return false
            return version.parsedVersion != null
        }

        /**
         * Returns if there is a Kotlin Gradle Plugin version defined in the Gradle version catalog
         * and that version is used in the build script of the buildSrc folder.
         */
        fun usesVersionCatalogVersionInBuildSrc(project: Project): Boolean {
            if (isCreatingNewLinkedProject) return false
            val buildSrcModule = project.modules.firstOrNull { it.name.endsWith(".buildSrc") } ?: return false
            val buildSrcBuildFile = buildSrcModule.getBuildScriptPsiFile() ?: return false

            val gradleFolder = project.guessProjectDir()?.findChild("gradle") ?: return false
            val gradlePluginKey = runCatching {
                val tomlFile = gradleFolder.findChild("libs.versions.toml")?.toNioPath()?.toFile() ?: return false
                val tomlTree = TomlMapper().readTree(tomlFile)
                // Find the library entries from the libraries table in the TOML file
                val libraryEntries = tomlTree.get("libraries") ?: return false
                // Find the name of the library entry that contains the Kotlin Gradle Plugin
                libraryEntries.fields().asSequence().firstOrNull { (_, node) ->
                    node.get("module")?.asText()?.contains(KOTLIN_GRADLE_PLUGIN_ID) == true
                }?.key
            }.getOrNull() ?: return false

            // Make sure that the library entry that we found is actually referenced in the build script
            return buildSrcBuildFile.text.contains(gradlePluginKey)
        }

        override fun setupProject(project: Project) {
            selectedJdkJvmTarget = resolveSelectedJvmTarget()
            kotlinVersionToUse = resolveKotlinVersionToUse(project)
        }
    }

    private class AssetsStep(parent: Step) : GradleAssetsNewProjectWizardStep<Step>(parent) {

        override fun setupAssets(project: Project) {
            if (parent.shouldGenerateMultipleModules) {
                setupMultiModuleProjectAssets(project)
            } else {
                setupSingleModuleProjectAssets(project)
            }
        }

        private fun setupSingleModuleProjectAssets(project: Project) {
            if (context.isCreatingNewProject) {
                addAssets(KotlinAssetsProvider.getKotlinGradleIgnoreAssets())
                addTemplateAsset("gradle.properties", "KotlinCodeStyleProperties")
                addGradleWrapperAsset(parent.gradleVersionToUse)
            }

            addEmptyDirectoryAsset(SRC_MAIN_KOTLIN_PATH)
            addEmptyDirectoryAsset(SRC_MAIN_RESOURCES_PATH)
            addEmptyDirectoryAsset(SRC_TEST_KOTLIN_PATH)
            addEmptyDirectoryAsset(SRC_TEST_RESOURCES_PATH)

            if (parent.addSampleCode) {
                if (parent.generateOnboardingTips) {
                    prepareKotlinSampleOnboardingTips(project)
                }
                withKotlinSampleCode(SRC_MAIN_KOTLIN_PATH, parent.groupId, parent.generateOnboardingTips)
            }

            addOrConfigureSettingsScript {
                if (parent.isFoojayPluginSupported || parent.isCreatingDaemonToolchain) {
                    withFoojayPlugin()
                }
            }
            addBuildScript {

                addGroup(parent.groupId)
                addVersion(parent.version)

                val kotlinVersion = parent.kotlinVersionToUse.takeUnless {
                    parent.usesPluginManagementKotlinVersion(project) ||
                            parent.usesParentKotlinVersion(project) ||
                            parent.usesVersionCatalogVersionInBuildSrc(project)
                }
                withKotlinJvmPlugin(kotlinVersion)
                withKotlinTest()

                parent.selectedJdkJvmTarget?.let {
                    if (parent.isFoojayPluginSupported) {
                        withKotlinJvmToolchain(it)
                    }
                }
            }
        }

        // This is currently only supported for generating new projects!
        private fun setupMultiModuleProjectAssets(project: Project) {
            assert(context.isCreatingNewProject)
            val librariesVersionStore = KotlinLibrariesCompatibilityStore.getInstance()
            val datetimeVersion = librariesVersionStore.getLatestVersion(KOTLINX_GROUP, DATETIME_ARTIFACT_ID) ?: ""
            val coroutinesVersion = librariesVersionStore.getLatestVersion(KOTLINX_GROUP, COROUTINES_ARTIFACT_ID) ?: ""
            val serializationJsonVersion = librariesVersionStore.getLatestVersion(KOTLINX_GROUP, SERIALIZATION_JSON_ARTIFACT_ID) ?: ""

            val templateParameters = mapOf(
                "PROJECT_NAME" to parent.name,
                "PACKAGE_NAME" to parent.groupId,
                "KOTLIN_VERSION" to Versions.KOTLIN,
                "FOOJAY_VERSION" to Versions.GRADLE_PLUGINS.FOOJAY_VERSION,
                "JVM_VERSION" to (parent.selectedJdkJvmTarget?.toString() ?: "21"),
                "KOTLINX_DATETIME_VERSION" to datetimeVersion,
                "KOTLINX_SERIALIZATION_JSON_VERSION" to serializationJsonVersion,
                "KOTLINX_COROUTINES_VERSION" to coroutinesVersion,
            )

            addAssets(KotlinAssetsProvider.getKotlinGradleIgnoreAssets())

            addTemplateAsset("gradle.properties", "KotlinSampleProperties", templateParameters)
            addTemplateAsset("gradle/libs.versions.toml", "KotlinSampleGradleToml", templateParameters)
            addTemplateAsset(KOTLIN_DSL_SETTINGS_FILE_NAME, "KotlinSampleSettings", templateParameters)
            addTemplateAsset("README.md", "KotlinSampleReadme", templateParameters)

            addTemplateAsset("buildSrc/$KOTLIN_DSL_SCRIPT_NAME", "KotlinSampleBuildSrcBuildGradle", templateParameters)
            addTemplateAsset("buildSrc/$KOTLIN_DSL_SETTINGS_FILE_NAME", "KotlinSampleBuildSrcSettings", templateParameters)
            addTemplateAsset("buildSrc/$SRC_MAIN_KOTLIN_PATH/kotlin-jvm.gradle.kts", "KotlinSampleConventionPlugin", templateParameters)

            addEmptyDirectoryAsset("app/$SRC_MAIN_KOTLIN_PATH")
            addEmptyDirectoryAsset("app/$SRC_MAIN_RESOURCES_PATH")
            addEmptyDirectoryAsset("app/$SRC_TEST_KOTLIN_PATH")
            addEmptyDirectoryAsset("app/$SRC_TEST_RESOURCES_PATH")
            addTemplateAsset("app/$KOTLIN_DSL_SCRIPT_NAME", "KotlinSampleAppBuildGradle", templateParameters)

            if (parent.addSampleCode) {
                if (parent.generateOnboardingTips) {
                    prepareKotlinSampleOnboardingTips(project, "App.kt")
                }
                val templateName = when {
                    !parent.generateOnboardingTips -> "KotlinSampleApp"
                    shouldRenderOnboardingTips() -> "KotlinSampleAppWithRenderedOnboardingTips"
                    else -> "KotlinSampleAppWithOnboardingTips"
                }
                val sourcePath = AssetsJava.getJavaSampleSourcePath(SRC_MAIN_KOTLIN_PATH, null, "App.kt")
                withKotlinSampleCode("app/$sourcePath", templateName, parent.groupId, parent.generateOnboardingTips)
            }

            addEmptyDirectoryAsset("utils/$SRC_MAIN_KOTLIN_PATH")
            addEmptyDirectoryAsset("utils/$SRC_MAIN_RESOURCES_PATH")
            addEmptyDirectoryAsset("utils/$SRC_TEST_KOTLIN_PATH")
            addEmptyDirectoryAsset("utils/$SRC_TEST_RESOURCES_PATH")
            addTemplateAsset("utils/$KOTLIN_DSL_SCRIPT_NAME", "KotlinSampleUtilsBuildGradle", templateParameters)

            if (parent.addSampleCode) {
                val utilitiesPath = AssetsJava.getJavaSampleSourcePath(SRC_MAIN_KOTLIN_PATH, null, "Utilities.kt")
                val utilitiesTestPath = AssetsJava.getJavaSampleSourcePath(SRC_TEST_KOTLIN_PATH, null, "UtilitiesTest.kt")
                addTemplateAsset("utils/$utilitiesPath", "KotlinSampleUtilsUtilities", templateParameters)
                addTemplateAsset("utils/$utilitiesTestPath", "KotlinSampleUtilsUtilitiesTest", templateParameters)
            }
        }
    }
}