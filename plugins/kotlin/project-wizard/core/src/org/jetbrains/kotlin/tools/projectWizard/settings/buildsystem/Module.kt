// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.GeneratedIdentificator
import org.jetbrains.kotlin.tools.projectWizard.Identificator
import org.jetbrains.kotlin.tools.projectWizard.IdentificatorOwner
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*

import org.jetbrains.kotlin.tools.projectWizard.core.entity.*
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.PluginSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingType
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.reference
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.templates.Template

@Suppress("EnumEntryName")
enum class ModuleKind(val isSinglePlatform: Boolean) : DisplayableSettingItem {
    multiplatform(isSinglePlatform = false),
    target(isSinglePlatform = false),
    singlePlatformJvm(isSinglePlatform = true),
    singlePlatformAndroid(isSinglePlatform = true),
    singlePlatformJsBrowser(isSinglePlatform = true),
    singlePlatformJsNode(isSinglePlatform = true),
    ;

    override val text: String
        @Suppress("HardCodedStringLiteral")
        get() = name
}


/**
 * @param template selected by default if any, should be present in [permittedTemplateIds].
 * @param permittedTemplateIds when specified the set restricts templates available for the module. Final decision (on whether a template
 * is included) is made on a template level, see [Template.isSupportedByModuleType].
 */
class Module(
    @NonNls var name: String,
    val configurator: ModuleConfigurator,
    var template: Template? = null,
    val permittedTemplateIds: Set<String>? = null,
    val sourceSets: List<Sourceset> = createDefaultSourceSets(),
    subModules: List<Module> = emptyList(),
    val dependencies: MutableList<ModuleReference> = mutableListOf(),
    var parent: Module? = null,
    override val identificator: Identificator = GeneratedIdentificator(name)
) : DisplayableSettingItem, Validatable<Module>, IdentificatorOwner {

    val kind: ModuleKind
        get() = configurator.moduleKind

    override val validator = (moduleNameValidator and
            moduleConfiguratorValidator and
            moduleTemplateValidator).withTarget(this) and
            subModulesValidator

    var subModules = subModules
        set(value) {
            field = value
            value.forEach { it.parent = this }
        }

    init {
        subModules.forEach { it.parent = this }
        sourceSets.forEach { it.parent = this }
    }


    @Suppress("HardCodedStringLiteral")
    override val text: String get() = name

    override val greyText: String
        get() = when {
            kind == ModuleKind.target -> configurator.text + " " + KotlinNewProjectWizardBundle.message("module.kind.target")
            configurator == MppModuleConfigurator -> KotlinNewProjectWizardBundle.message("module.kind.mpp.module")
            configurator == AndroidSinglePlatformModuleConfigurator -> KotlinNewProjectWizardBundle.message("module.kind.android.module")
            configurator == IOSSinglePlatformModuleConfigurator -> KotlinNewProjectWizardBundle.message("module.kind.ios.module")
            configurator == BrowserJsSinglePlatformModuleConfigurator -> KotlinNewProjectWizardBundle.message("module.kind.js.browser.module")
            configurator == NodeJsSinglePlatformModuleConfigurator -> KotlinNewProjectWizardBundle.message("module.kind.js.node.module")
            else -> KotlinNewProjectWizardBundle.message("module.kind.module")
        }

    fun SettingsWriter.initDefaultValuesForSettings() {
        configurator.safeAs<ModuleConfiguratorWithSettings>()?.apply { initDefaultValuesFor(this@Module) }
        configurator.safeAs<ModuleConfiguratorWithProperties>()?.apply { initDefaultValuesForProperties(this@Module) }
        template?.apply { initDefaultValuesFor(this@Module) }
    }

    companion object {
        val ALLOWED_SPECIAL_CHARS_IN_MODULE_NAMES = setOf('-', '_')

        val parser: Parser<Module> = mapParser { map, path ->
            val (name) = map.parseValue<String>(path, "name")
            val identifier = GeneratedIdentificator(name)
            val (configurator) = map.parseValue(this, path, "type", ModuleConfigurator.getParser(identifier))

            val template = map["template"]?.let {
                Template.parser(identifier).parse(this, it, "$path.template")
            }.nullableValue()
            val sourceSets = listOf(Sourceset(SourcesetType.main), Sourceset(SourcesetType.test))
            val (submodules) = map.parseValue(this, path, "subModules", listParser(Module.parser)) { emptyList() }
            val (dependencies) = map.parseValue(this, path, "dependencies", listParser(ModuleReference.ByPath.parser)) { emptyList() }

            Module(
                name, configurator, template = template, sourceSets = sourceSets, subModules = submodules,
                dependencies = dependencies.toMutableList(), identificator = identifier
            )
        }

        private val moduleNameValidator = settingValidator<Module> { module ->
            StringValidators.shouldNotBeBlank(KotlinNewProjectWizardBundle.message("module.name")).validate(this, module.name)
        } and settingValidator { module ->
            StringValidators.shouldBeValidIdentifier(
                KotlinNewProjectWizardBundle.message("module.name", module.name),
                ALLOWED_SPECIAL_CHARS_IN_MODULE_NAMES
            ).validate(this, module.name)
        }

        val moduleConfiguratorValidator = settingValidator<Module> { module ->
            inContextOfModuleConfigurator(module) {
                allSettingsOfModuleConfigurator(module.configurator).map { setting ->
                    val reference = when (setting) {
                        is PluginSetting<Any, SettingType<Any>> -> setting.reference
                        is ModuleConfiguratorSetting<Any, SettingType<Any>> -> setting.reference
                        else -> null
                    }
                    val value = reference?.notRequiredSettingValue
                        ?: reference?.savedOrDefaultValue
                        ?: return@map ValidationResult.ValidationError(
                            KotlinNewProjectWizardBundle.message(
                                "validation.should.not.be.blank",
                                setting.title.capitalize()
                            )
                        )
                    setting.validator.validate(this@settingValidator, value)
                }.fold()
            }
        }

        val moduleTemplateValidator = settingValidator<Module> { module ->
            val template = module.template ?: return@settingValidator ValidationResult.OK
            org.jetbrains.kotlin.tools.projectWizard.templates.withSettingsOf(module) {
                template.settings.map { setting ->
                    val value = setting.reference.notRequiredSettingValue
                        ?: setting.reference.savedOrDefaultValue
                        ?: return@map ValidationResult.ValidationError(
                            KotlinNewProjectWizardBundle.message(
                                "validation.should.not.be.blank",
                                setting.title.capitalize()
                            )
                        )
                    (setting.validator as SettingValidator<Any>).validate(this@settingValidator, value)
                }.fold()
            }
        }

        val subModulesValidator = inValidatorContext<Module> { module ->
            validateList(module.subModules)
        }

        private fun createDefaultSourceSets() =
            SourcesetType.values().map { sourceSetType -> Sourceset(sourceSetType, dependencies = emptyList()) }
    }
}

val Module.path
    get() = generateSequence(this, Module::parent)
        .map { it.name }
        .toList()
        .asReversed()
        .let(::ModulePath)


val Sourceset.path
    get() = ModulePath(parent?.path?.parts.orEmpty() + sourcesetType.name)

val Module.isRootModule
    get() = parent == null

@Suppress("FunctionName")
fun MultiplatformTargetModule(
    @NonNls name: String,
    configurator: ModuleConfigurator,
    sourceSets: List<Sourceset>,
    permittedTemplateIds: Set<String>? = null
) =
    Module(name, configurator, sourceSets = sourceSets, permittedTemplateIds = permittedTemplateIds)

@Suppress("FunctionName")
fun MultiplatformModule(
    @NonNls name: String,
    template: Template? = null,
    targets: List<Module> = emptyList(),
    permittedTemplateIds: Set<String>? = null
) =
    Module(name, MppModuleConfigurator, template = template, permittedTemplateIds = permittedTemplateIds, subModules = targets)

@Suppress("FunctionName")
fun SinglePlatformModule(@NonNls name: String, sourceSets: List<Sourceset>, permittedTemplateIds: Set<String>? = null) =
    Module(name, JvmSinglePlatformModuleConfigurator, sourceSets = sourceSets, permittedTemplateIds = permittedTemplateIds)
