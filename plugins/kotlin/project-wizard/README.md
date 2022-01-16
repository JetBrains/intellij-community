# Kotlin New Project Wizard

Kotlin New Project Wizard (Kotlin NPW) - is a subsystem allowing to generate different IDEA projects
with Kotlin support.

Users face Kotlin-NPW as a part of `New Project` dialog available via `File -> New -> Project` menu.
On the left side of the dialog window there is `Kotlin`. Everything happening afterwards on the right is Kotlin-NPW.

Other than that Kotlin-NPW is embedded into the recent (redesigned) platform wizard. To activate it use registry key `new.project.wizard`
and follow `File -> New -> Project -> New Project -> Language: Kotlin`. 

## Basic Ideas

Kotlin-NPW was designed as a very flexible and extensible subsystem consisting of a core and outer-world adaptors.<br> 
IDEA-adaptor embeds NPW-core into IDEA project creation infrastructure, CLI-adaptor allows generating project bases on YAML
file-descriptors. The core in its turn encapsulates general knowledge of projects construction.

The key ideas of the NPW-core to understand are its _modularity_ and the _dynamics_ behind.

Any little concept is a `Plugin` comprising settings and actions (tasks) on them. Despite optional nature of the term, plugins are often mandatory - 
though the NPW-core is tolerant to their absence the final product (project) is hardly possible. Some plugins are aware of others - 
dependencies are legal. Clear example of a plugin is `StructurePlugin` capable of project tree generation. Generation is an action,
settings are path, name and POM triple.

Dynamics can be explained as an infrastructure (or kernel/reactor) juggling plugins to generate a project.<br>
What it takes into account:
- There is a registry containing all active plugins. See `Plugins.allPlugins`. To participate in the project-generation plugin needs
  to be registered.  
- Project-generation-process is split into sequential `GenerationPhase`-s. Plugins' tasks are strung on them like threads.
  Within a single phase tasks are ordered according to their dependencies.


## Module Structure

The Wizard consists of several submodules:

| Module      | Description |
| ----------- | ----------- |
| core        | Core as its name states reflects the concept of the common functionality representing the Wizard. Plugins, phases, settings, configurations, computations, templates, build scripts and source code generation. |
| idea        | Adaptor aimed for using Wizard core in the context of IDEA. Integration, UI, i18n, IDEA-specific services. |
| cli         | Adaptor aimed for using Wizard core in the context of a command line. We still support this adaptor though the idea did not spread. YAML DSL for configuration files. |
| gradle & maven   | Modules contain build systems specific logic. Separate modules are needed for proper functioning with/without Gradle/Maven IDEA plugins. |
| tests   |         |


## Deep Dive

### Where Kotlin and Platform wizards touch

Thanks to its IDEA adaptor Kotlin-NPW can be though as a supplementary to the Platform-NPW.
Platform one is all about common UI for supported languages, frameworks, build systems, etc. Kotlin-NPW plugs into this infrastructure.

Kotlin plugin descriptor (`community/plugins/kotlin/plugin/resources/META-INF/core.xml`) has the following important declarations:
```xml
<!-- Project Wizard -->
<extensions defaultExtensionNs="com.intellij">
  <jbProtocolCommand implementation="org.jetbrains.kotlin.tools.projectWizard.wizard.OpenNewProjectWizardProtocolCommand"/>
  <newProjectWizard.language implementation="org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard"/>
  <moduleBuilder builderClass="org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder"/>
</extensions>
```

Actually there are two wizard-related hierarchies reflecting the fact that IDEA is about to introduce its redesigned wizard. <br>
`KotlinNewProjectWizard` is about that same `new.project.wizard`.<br>
`NewProjectWizardModuleBuilder` represents Kotlin dedicated wizard supporting project- and module/code-templates.

--------------------------------------------------------------------------------------------------------------------------------------------

### Project Templates

Wizard guids users in two steps. First one is about project template selection.
_Console Application_, _Library_, _Mobile Application_ are examples of project templates.
Project template is all about its structure. Template lists project modules (including submodules) with a rich variety of settings 
(types, sourcesets, names, restrictions, code sample templates, etc.) 

****Code references****

`ProjectTemplates.kt` is a core module file containing all template declarations.<br>
`ProjectTemplate` is a base class, entry point to see all existing templates.

**How Wizard infrastructure becomes aware of project templates?**

As you might already know, infrastructure (Wizards' kernel) uses plugins as an entry point to the inner world. And there is one
 responsible for project templates - `ProjectTemplatesPlugin`. It exposes so-called drop-down setting with the value of
`ProjectTemplate.ALL`list. The latter contains all available project templates.

--------------------------------------------------------------------------------------------------------------------------------------------

### Sample Code Templates

In addition to a project structure and build scripts wizard supplies its final product with source code samples.
Wizard's entity modelling this concept is called `Template`. `Template`'s subclasses is what fills projects with souls and make them alive.
Nowadays templates act mostly behind the scenes - end users do not control template-to-module-application. Project templates are
the entities defining modules' configuration... including sample code (in fact).

**Code references**

`org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module` is a class representing a module to be created.
Its `template` and `permittedTemplateIds` properties are the key points for understanding module-code_template relation.

`Template.isPermittedForModule` and `ModuleSettingsComponentKt.availableTemplatesFor` shed light on a more sophisticated interaction.
In theory (and that is how it worked some time ago) the number of code templates applicable to a single module is greater than one. When
it is the case, user has a choice which template to apply.

--------------------------------------------------------------------------------------------------------------------------------------------

### Modules/Targets and their Configurators

`org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module` (don't confuse with `com.intellij.openapi.module.Module`) is a module
descriptor, a matrix the future real IDE module is generated from.<br>
There are two important points to know about modules:
1. `Module` is for describing both modules and targets. The latter is a term from multi-platform world.<br>
   Targets can be seen as sub-nodes of so-called _common_ module. Targets are for example Android, iOS, Jvm, Native.
2. `Module` would become too complex to cover all types and flavours of modules/targets.<br>
   To keep it simple the real world complexity is moved to the hierarchy of `ModuleConfigurator`.

--------------------------------------------------------------------------------------------------------------------------------------------

### Settings: where they come from

Setting can be thought as something affecting behaviour of wizard entities.
There are at least 2 worth mentioning things about settings: 
1. Looking at UI one hardly say where the setting in question comes from.<br>  
   Plugins, project templates, code templates, module configurators - all of them expose their settings to UI being themselves invisible.
   Mapping process might be not so obvious. It's good to know the list of places to search.
2. Entities exposing settings (plugins, templates, configurators) form hierarchies where members must answer the question
   "What are your settings?". So far so good. But for module configurators context plays a big role, hence the cases where child classes
   answer "I'm like my parent, but without this setting and this setting and these are my additional ones".

**Understanding settings API**
<br>
TBD (read/write, access contexts)

**Settings are persistent**


**Code references**

* `Plugin.settings` + `PluginSetting`
* Properties of `ProjectTemplate` + `SettingWithValue`
* `Template.settings` + `TemplateSetting`
* `ModuleConfiguratorWithSettings.getConfiguratorSettings` - module configurators' _"What are your settings?"_ 
 
--------------------------------------------------------------------------------------------------------------------------------------------

### Services

Wizard core interacts with the outer world by means of `WizardSerivce`-s. It's an abstraction hiding implementation details for
IDEA and CLS adaptors. Interaction with file system, getting kotlin or jvm-target version are wrapped into services.

--------------------------------------------------------------------------------------------------------------------------------------------

### Build Scripts Generation

To start the journey smoothly it's practical to look at the process from different angles:
1. Generation starts during `GenerationPhase.PROJECT_GENERATION`at `KotlinPlugin`'s task:<br> 
   ``` kotlin
   ...
   val createModules by pipelineTask(GenerationPhase.PROJECT_GENERATION) !!!
   ...
   ```
   This task forms so-called IR - `org.jetbrains.kotlin.tools.projectWizard.ir.IR` - intermediate representation.
2. Hierarchy of possible IRs is impressive. Placing a breakpoint inside one of its members can help a lot.
3. `BuildFilePrinter` and `BuildSystemIR.render` are the key actors where exact string constants reside.

**What affects scripts generation**

IRs mentioned above are collected and affected from:
* Plugin tasks. `PipelineTask.Builder.withAction` + ext. function calls on provided `Writer` can work miracles.
* Code `Template`-s.
* `ModuleConfigurator`-s