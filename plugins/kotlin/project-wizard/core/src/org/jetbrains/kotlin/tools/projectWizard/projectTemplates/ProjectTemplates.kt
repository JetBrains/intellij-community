// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.projectTemplates

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.*
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.StructurePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.gradle.GradlePlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.templates.*
import org.jetbrains.kotlin.tools.projectWizard.templates.compose.*
import org.jetbrains.kotlin.tools.projectWizard.templates.mpp.MobileMppTemplate

abstract class ProjectTemplate : DisplayableSettingItem {
    abstract val title: String
    override val text: String get() = title
    abstract val description: String
    abstract val suggestedProjectName: String
    abstract val projectKind: ProjectKind
    abstract val id: String

    private val setsDefaultValues: List<SettingWithValue<*, *>>
        get() = listOf(KotlinPlugin.projectKind.reference withValue projectKind)

    protected open val setsPluginSettings: List<SettingWithValue<*, *>> = emptyList()
    protected open val setsModules: List<Module> = emptyList()
    val setsAdditionalSettingValues = mutableListOf<SettingWithValue<*, *>>()

    val setsValues: List<SettingWithValue<*, *>>
        get() = buildList {
            setsModules.takeIf { it.isNotEmpty() }?.let { modules ->
                +(KotlinPlugin.modules.reference withValue modules)
            }
            +setsDefaultValues
            +setsPluginSettings
            +setsAdditionalSettingValues
        }


    protected fun <T : Template> Module.withTemplate(
        template: T,
        createSettings: TemplateSettingsBuilder<T>.() -> Unit = {}
    ) = apply {
        this.template = template
        with(TemplateSettingsBuilder(this, template)) {
            createSettings()
            setsAdditionalSettingValues += setsSettings
        }
    }

    protected inline fun <reified C: ModuleConfigurator> Module.withConfiguratorSettings(
        createSettings: ConfiguratorSettingsBuilder<C>.() -> Unit = {}
    ) = apply {
        check(configurator is C)
        with(ConfiguratorSettingsBuilder(this, configurator)) {
            createSettings()
            setsAdditionalSettingValues += setsSettings
        }
    }


    companion object {
        val ALL = listOf(
            ConsoleApplicationProjectTemplateWithSample,
            MultiplatformLibraryProjectTemplate,
            NativeApplicationProjectTemplate,
            FrontendApplicationProjectTemplate,
            ReactApplicationProjectTemplate,
            FullStackWebApplicationProjectTemplate,
            NodeJsApplicationProjectTemplate,
            ComposeDesktopApplicationProjectTemplate,
            ComposeMultiplatformApplicationProjectTemplate,
            ComposeWebApplicationProjectTemplate
        )

        fun byId(id: String): ProjectTemplate? = ALL.firstOrNull {
            it.id.equals(id, ignoreCase = true)
        }
    }
}

private infix fun <V : Any, T : SettingType<V>> PluginSettingReference<V, T>.withValue(value: V): SettingWithValue<V, T> =
    SettingWithValue(this, value)

private inline infix fun <V : Any, reified T : SettingType<V>> PluginSetting<V, T>.withValue(value: V): SettingWithValue<V, T> =
    SettingWithValue(reference, value)

private fun createDefaultSourceSets() =
    SourcesetType.values().map { sourceSetType ->
        Sourceset(
            sourceSetType,
            dependencies = emptyList()
        )
    }

private fun ModuleType.createDefaultTarget(name: String = this.name, permittedTemplateIds: Set<String>? = null) =
    MultiplatformTargetModule(name, defaultTarget, createDefaultSourceSets(), permittedTemplateIds)


open class ConsoleApplicationProjectTemplate(private val addSampleCode: Boolean) : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.empty.jvm.console.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.empty.jvm.console.description")
    override val id = "consoleApplication"

    @NonNls
    override val suggestedProjectName = "myConsoleApplication"
    override val projectKind = ProjectKind.Singleplatform

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                SinglePlatformModule(
                    "consoleApp",
                    createDefaultSourceSets(),
                    permittedTemplateIds = setOf(ConsoleJvmApplicationTemplate.id)
                ).apply {
                    if (addSampleCode)
                        withTemplate(ConsoleJvmApplicationTemplate)
                }
            )
        )
}

object ConsoleApplicationProjectTemplateWithSample : ConsoleApplicationProjectTemplate(addSampleCode = true)

object MultiplatformLibraryProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.mpp.lib.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.mpp.lib.description")
    override val id = "multiplatformLibrary"

    @NonNls
    override val suggestedProjectName = "myMultiplatformLibrary"
    override val projectKind = ProjectKind.Multiplatform

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                MultiplatformModule(
                    "library",
                    permittedTemplateIds = emptySet(),
                    targets = listOf(
                        ModuleType.common.createDefaultTarget(),
                        ModuleType.jvm.createDefaultTarget(permittedTemplateIds = emptySet()),
                        MultiplatformTargetModule(
                            "js",
                            MppLibJsBrowserTargetConfigurator,
                            createDefaultSourceSets(),
                            permittedTemplateIds = emptySet()
                        )
                            .withConfiguratorSettings<MppLibJsBrowserTargetConfigurator> {
                                JSConfigurator.kind withValue JsTargetKind.LIBRARY
                            },
                        ModuleType.native.createDefaultTarget(permittedTemplateIds = emptySet())
                    )
                )
            )
        )
}

object FullStackWebApplicationProjectTemplate : ProjectTemplate() {
    override val title: String = KotlinNewProjectWizardBundle.message("project.template.full.stack.title")
    override val description: String = KotlinNewProjectWizardBundle.message("project.template.full.stack.description")
    override val id = "fullStackWebApplication"

    @NonNls
    override val suggestedProjectName: String = "myFullStackApplication"
    override val projectKind: ProjectKind = ProjectKind.Multiplatform
    override val setsPluginSettings: List<SettingWithValue<*, *>> = listOf(
        KotlinPlugin.modules.reference withValue listOf(
            MultiplatformModule(
                "application",
                permittedTemplateIds = emptySet(),
                targets = listOf(
                    ModuleType.common.createDefaultTarget(),
                    ModuleType.jvm.createDefaultTarget(permittedTemplateIds = setOf(KtorServerTemplate.id)).apply {
                        withTemplate(KtorServerTemplate)
                    },
                    ModuleType.js.createDefaultTarget(permittedTemplateIds = setOf(ReactJsClientTemplate.id)).apply {
                        withTemplate(ReactJsClientTemplate)
                    }
                )
            )
        )
    )
}

object NativeApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.native.console.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.native.console.description")
    override val id = "nativeApplication"

    @NonNls
    override val suggestedProjectName = "myNativeConsoleApp"
    override val projectKind = ProjectKind.Multiplatform

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                Module(
                    "app",
                    MppModuleConfigurator,
                    permittedTemplateIds = emptySet(),
                    sourceSets = emptyList(),
                    subModules = listOf(
                        ModuleType.native.createDefaultTarget("native", permittedTemplateIds = setOf(NativeConsoleApplicationTemplate.id))
                            .apply {
                                withTemplate(NativeConsoleApplicationTemplate)
                            }
                    )
                )
            )
        )
}

object FrontendApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.browser.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.browser.description")
    override val id = "frontendApplication"

    @NonNls
    override val suggestedProjectName = "myKotlinJsApplication"
    override val projectKind = ProjectKind.Js

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                Module(
                    "browser",
                    BrowserJsSinglePlatformModuleConfigurator,
                    template = SimpleJsClientTemplate,
                    permittedTemplateIds = setOf(SimpleJsClientTemplate.id),
                    sourceSets = SourcesetType.ALL.map { type ->
                        Sourceset(type, dependencies = emptyList())
                    },
                    subModules = emptyList()
                )
            )
        )
}

object ReactApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.react.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.react.description")
    override val id = "reactApplication"

    @NonNls
    override val suggestedProjectName = "myKotlinJsApplication"
    override val projectKind = ProjectKind.Js

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                Module(
                    "react",
                    BrowserJsSinglePlatformModuleConfigurator,
                    template = ReactJsClientTemplate,
                    permittedTemplateIds = setOf(ReactJsClientTemplate.id),
                    sourceSets = SourcesetType.ALL.map { type ->
                        Sourceset(type, dependencies = emptyList())
                    },
                    subModules = emptyList()
                )
            )
        )
}

abstract class MultiplatformMobileApplicationProjectTemplateBase : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.mpp.mobile.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.mpp.mobile.description")

    @NonNls
    override val suggestedProjectName = "myIOSApplication"
    override val projectKind = ProjectKind.Multiplatform

    override val setsModules: List<Module> = buildList {
        val shared = MultiplatformModule(
            "shared",
            template = MobileMppTemplate(),
            targets = listOf(
                ModuleType.common.createDefaultTarget(),
                Module(
                    "android",
                    AndroidTargetConfigurator,
                    null,
                    sourceSets = createDefaultSourceSets(),
                    subModules = emptyList()
                ).withConfiguratorSettings<AndroidTargetConfigurator> {
                    configurator.androidPlugin withValue AndroidGradlePlugin.LIBRARY
                },
                Module(
                    "ios",
                    RealNativeTargetConfigurator.configuratorsByModuleType.getValue(ModuleSubType.ios),
                    null,
                    permittedTemplateIds = emptySet(),
                    sourceSets = createDefaultSourceSets(),
                    subModules = emptyList()
                )
            )
        )
        +iosAppModule(shared)
        +androidAppModule(shared)
        +shared // shared module must be the last so dependent modules could create actual files
    }

    protected abstract fun iosAppModule(shared: Module): Module
    protected abstract fun androidAppModule(shared: Module): Module
}

object NodeJsApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.nodejs.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.nodejs.description")
    override val id = "nodejsApplication"

    @NonNls
    override val suggestedProjectName = "myKotlinJsApplication"
    override val projectKind = ProjectKind.Js

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                Module(
                    "nodejs",
                    NodeJsSinglePlatformModuleConfigurator,
                    template = SimpleNodeJsTemplate,
                    permittedTemplateIds = setOf(SimpleNodeJsTemplate.id),
                    sourceSets = SourcesetType.ALL.map { type ->
                        Sourceset(type, dependencies = emptyList())
                    },
                    subModules = emptyList()
                )
            )
        )
}

object ComposeDesktopApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.compose.desktop.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.compose.desktop.description")
    override val id = "composeDesktopApplication"

    @NonNls
    override val suggestedProjectName = "myComposeDesktopApplication"
    override val projectKind = ProjectKind.COMPOSE

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            GradlePlugin.gradleVersion withValue Versions.GRADLE_VERSION_FOR_COMPOSE,
            StructurePlugin.version withValue "1.0",
        )

    override val setsModules: List<Module>
        get() = listOf(
            Module(
                "compose",
                JvmSinglePlatformModuleConfigurator,
                template = ComposeJvmDesktopTemplate(),
                sourceSets = SourcesetType.ALL.map { type ->
                    Sourceset(type, dependencies = emptyList())
                },
                subModules = emptyList()
            ).withConfiguratorSettings<JvmSinglePlatformModuleConfigurator> {
                JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
                JvmModuleConfigurator.targetJvmVersion withValue TargetJvmVersion.JVM_11
            }
        )
}

object ComposeMultiplatformApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.compose.multiplatform.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.compose.multiplatform.description")
    override val id = "composeMultiplatformApplication"

    @NonNls
    override val suggestedProjectName = "myComposeMultiplatformApplication"
    override val projectKind = ProjectKind.COMPOSE

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            GradlePlugin.gradleVersion withValue Versions.GRADLE_VERSION_FOR_COMPOSE,
            StructurePlugin.version withValue "1.0",
        )

    override val setsModules: List<Module>
        get() = buildList {
            val common = MultiplatformModule(
                "common",
                template = ComposeMppModuleTemplate(),
                listOf(
                    ModuleType.common.createDefaultTarget().withConfiguratorSettings<CommonTargetConfigurator> {
                        JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
                    },
                    Module(
                        "android",
                        AndroidTargetConfigurator,
                        template = ComposeCommonAndroidTemplate(),
                        sourceSets = createDefaultSourceSets(),
                        subModules = emptyList()
                    ).withConfiguratorSettings<AndroidTargetConfigurator> {
                        configurator.androidPlugin withValue AndroidGradlePlugin.LIBRARY
                        JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
                    },
                    Module(
                        "desktop",
                        JvmTargetConfigurator,
                        template = ComposeCommonDesktopTemplate(),
                        sourceSets = createDefaultSourceSets(),
                        subModules = emptyList()
                    ).withConfiguratorSettings<JvmTargetConfigurator> {
                        JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
                        JvmModuleConfigurator.targetJvmVersion withValue TargetJvmVersion.JVM_11
                    }
                )
            )
            +Module(
                "android",
                AndroidSinglePlatformModuleConfigurator,
                template = ComposeAndroidTemplate(),
                sourceSets = createDefaultSourceSets(),
                subModules = emptyList(),
                dependencies = mutableListOf(ModuleReference.ByModule(common))
            ).withConfiguratorSettings<AndroidSinglePlatformModuleConfigurator> {
                JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
            }
            +Module(
                "desktop",
                MppModuleConfigurator,
                template = ComposeCommonDesktopTemplate(),
                sourceSets = createDefaultSourceSets(),
                subModules = listOf(
                    Module(
                        "jvm",
                        JvmTargetConfigurator,
                        template = ComposeJvmDesktopTemplate(),
                        sourceSets = createDefaultSourceSets(),
                        subModules = emptyList(),
                        dependencies = mutableListOf(ModuleReference.ByModule(common))
                    ).withConfiguratorSettings<JvmTargetConfigurator> {
                        JvmModuleConfigurator.testFramework withValue KotlinTestFramework.NONE
                        JvmModuleConfigurator.targetJvmVersion withValue TargetJvmVersion.JVM_11
                    }
                ),
            )
            +common
        }
}

object ComposeWebApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.compose.web.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.compose.web.description")
    override val id = "composeWebApplication"

    @NonNls
    override val suggestedProjectName = "myComposeWebApplication"
    override val projectKind = ProjectKind.COMPOSE

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            GradlePlugin.gradleVersion withValue Versions.GRADLE_VERSION_FOR_COMPOSE,
            StructurePlugin.version withValue "1.0",
        )

    override val setsModules: List<Module>
        get() = listOf(
            Module(
                "web",
                MppModuleConfigurator,
                template = null,
                sourceSets = createDefaultSourceSets(),
                subModules = listOf(
                    Module(
                        "js",
                        JsComposeMppConfigurator,
                        template = ComposeWebModuleTemplate,
                        permittedTemplateIds = setOf(ComposeWebModuleTemplate.id),
                        sourceSets = createDefaultSourceSets(),
                        subModules = emptyList()
                    )
                )
            )
        )
}