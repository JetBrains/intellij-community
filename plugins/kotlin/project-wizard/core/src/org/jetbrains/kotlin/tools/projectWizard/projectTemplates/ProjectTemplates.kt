// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.projectTemplates

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.registry.Registry
import icons.KotlinBaseResourcesIcons
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.buildList
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectTemplates.ProjectTemplatesProvider
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.templates.*
import org.jetbrains.kotlin.tools.projectWizard.templates.mpp.MobileMppTemplate
import javax.swing.Icon

abstract class ProjectTemplate : DisplayableSettingItem {
    abstract val title: String
    override val text: String get() = title
    abstract val description: String
    abstract val suggestedProjectName: String
    abstract val projectKind: ProjectKind
    abstract val id: String
    open val icon: Icon? = null

    open fun isVisible() = true

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

    protected inline fun <reified C : ModuleConfigurator> Module.withConfiguratorSettings(
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
            FullStackWebApplicationProjectTemplate,
            MultiplatformLibraryProjectTemplate,
            NativeApplicationProjectTemplate,
            WasmApplicationProjectTemplate,
            FrontendApplicationProjectTemplate,
            ReactApplicationProjectTemplate,
            NodeJsApplicationProjectTemplate,
            ConsoleApplicationProjectTemplateWithSample
        ) + extensionTemplates

        private val extensionTemplates: List<ProjectTemplate>
            get() = mutableListOf<ProjectTemplate>().also { list ->
                ProjectTemplatesProvider.EP_NAME.extensionsIfPointIsRegistered.forEach { list.addAll(it.getTemplates()) }
            }

        fun byId(id: String): ProjectTemplate? = ALL.firstOrNull {
            it.id.equals(id, ignoreCase = true)
        }
    }
}

private infix fun <V : Any, T : SettingType<V>> PluginSettingReference<V, T>.withValue(value: V): SettingWithValue<V, T> =
    SettingWithValue(this, value)

@Suppress("unused")
private inline infix fun <V : Any, reified T : SettingType<V>> PluginSetting<V, T>.withValue(value: V): SettingWithValue<V, T> =
    SettingWithValue(reference, value)

fun createDefaultSourceSets() =
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

object ConsoleApplicationProjectTemplateWithSample : ConsoleApplicationProjectTemplate(addSampleCode = true) {
    override val icon: Icon
        get() = KotlinIcons.Wizard.CONSOLE
}

object MultiplatformLibraryProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.mpp.lib.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.mpp.lib.description")
    override val id = "multiplatformLibrary"
    override val icon: Icon
        get() = KotlinIcons.Wizard.MULTIPLATFORM_LIBRARY

    @NonNls
    override val suggestedProjectName = "myMultiplatformLibrary"
    override val projectKind = ProjectKind.Multiplatform

    override val setsPluginSettings: List<SettingWithValue<*, *>>
        get() = listOf(
            KotlinPlugin.modules.reference withValue listOf(
                MultiplatformModule(
                    "library",
                    permittedTemplateIds = emptySet(),
                    targets = buildList {
                        +ModuleType.common.createDefaultTarget()
                        +ModuleType.jvm.createDefaultTarget(permittedTemplateIds = emptySet())

                        if (AdvancedSettings.getBoolean("kotlin.mpp.experimental")) {
                            +MultiplatformTargetModule(
                                "js",
                                MppLibJsBrowserTargetConfigurator,
                                createDefaultSourceSets(),
                                permittedTemplateIds = emptySet()
                            ).withConfiguratorSettings<MppLibJsBrowserTargetConfigurator> {
                                JSConfigurator.kind withValue JsTargetKind.LIBRARY
                            }
                        }

                        +ModuleType.native.createDefaultTarget(permittedTemplateIds = emptySet())
                    }
                )
            )
        )
}

object FullStackWebApplicationProjectTemplate : ProjectTemplate() {
    override val title: String = KotlinNewProjectWizardBundle.message("project.template.full.stack.title")
    override val description: String = KotlinNewProjectWizardBundle.message("project.template.full.stack.description")
    override val id = "fullStackWebApplication"
    override val icon: Icon
        get() = KotlinIcons.Wizard.WEB

    override fun isVisible(): Boolean =
        AdvancedSettings.getBoolean("kotlin.mpp.experimental")

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
    override val icon: Icon
        get() = KotlinIcons.Wizard.NATIVE

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

object WasmApplicationProjectTemplate : ProjectTemplate() {
    override val title: String = KotlinNewProjectWizardBundle.message("project.template.wasm.browser.title")
    override val description: String = KotlinNewProjectWizardBundle.message("project.template.wasm.browser.description")
    override val id = "simpleWasmApplication"
    override val icon: Icon
        get() = KotlinIcons.Wizard.WEB

    override fun isVisible(): Boolean {
        return Registry.`is`("kotlin.wasm.wizard")
    }

    @NonNls
    override val suggestedProjectName: String = "myWasmApplication"
    override val projectKind: ProjectKind = ProjectKind.Multiplatform
    override val setsPluginSettings: List<SettingWithValue<*, *>> = listOf(
        KotlinPlugin.modules.reference withValue listOf(
            MultiplatformModule(
                "application",
                permittedTemplateIds = emptySet(),
                targets = listOf(
                    ModuleType.common.createDefaultTarget(),
                    ModuleType.wasm.createDefaultTarget(permittedTemplateIds = setOf(SimpleWasmClientTemplate.id)).apply {
                        withTemplate(SimpleWasmClientTemplate)
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
    override val icon: Icon
        get() = KotlinIcons.Wizard.JS

    override fun isVisible(): Boolean =
        AdvancedSettings.getBoolean("kotlin.mpp.experimental")

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
    override val icon: Icon
        get() = KotlinIcons.Wizard.REACT_JS

    override fun isVisible(): Boolean =
        AdvancedSettings.getBoolean("kotlin.mpp.experimental")

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
    override val icon: Icon
        get() = KotlinBaseResourcesIcons.Wizard.MultiplatformMobile

    @NonNls
    override val suggestedProjectName = "myIOSApplication"
    override val projectKind = ProjectKind.Multiplatform

    override val setsModules: List<Module> = buildList {
        val iosModule = Module(
            "ios",
            sharedIosConfigurator,
            null,
            permittedTemplateIds = emptySet(),
            sourceSets = createDefaultSourceSets(),
            subModules = emptyList(),
            canBeRemoved = false
        )
        val shared = MultiplatformModule(
            "shared",
            canBeRemoved = false,
            template = MobileMppTemplate(),
            targets = listOf(
                ModuleType.common.createDefaultTarget(),
                Module(
                    "android",
                    AndroidTargetConfigurator,
                    null,
                    sourceSets = createDefaultSourceSets(),
                    subModules = emptyList(),
                    canBeRemoved = false
                ).withConfiguratorSettings<AndroidTargetConfigurator> {
                    configurator.androidPlugin withValue AndroidGradlePlugin.LIBRARY
                },
                iosModule,
                Module(
                    "iosSimulatorArm64",
                    RealNativeTargetConfigurator.configuratorsByModuleType.getValue(ModuleSubType.iosSimulatorArm64),
                    null,
                    permittedTemplateIds = emptySet(),
                    sourceSets = SourcesetType.values().map {
                        Sourceset(
                            sourcesetType = it,
                            dependencies = emptyList(),
                            createDirectory = false,
                            dependsOnModules = listOf(iosModule),
                        )
                    },
                    subModules = emptyList(),
                    canBeRemoved = false
                ),
            )
        )
        +iosAppModule(shared)
        +androidAppModule(shared)
        +shared // shared module must be the last so dependent modules could create actual files
    }

    protected abstract fun iosAppModule(shared: Module): Module
    protected abstract fun androidAppModule(shared: Module): Module

    open val sharedIosConfigurator get() = RealNativeTargetConfigurator.configuratorsByModuleType.getValue(ModuleSubType.ios)
}

object NodeJsApplicationProjectTemplate : ProjectTemplate() {
    override val title = KotlinNewProjectWizardBundle.message("project.template.nodejs.title")
    override val description = KotlinNewProjectWizardBundle.message("project.template.nodejs.description")
    override val id = "nodejsApplication"
    override val icon: Icon
        get() = KotlinIcons.Wizard.NODE_JS

    override fun isVisible(): Boolean =
        AdvancedSettings.getBoolean("kotlin.mpp.experimental")

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
