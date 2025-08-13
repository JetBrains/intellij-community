// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.*
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.util.xml.GenericDomValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.dom.MavenDomElement
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.model.*
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenArtifactScope
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.buildArgumentString
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.RepositoryDescription
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.utils.SmartList

class PomFile private constructor(private val xmlFile: XmlFile, val domModel: MavenDomProjectModel) {
    constructor(xmlFile: XmlFile) : this(
        xmlFile,
        MavenDomUtil.getMavenDomProjectModel(xmlFile.project, xmlFile.virtualFile)
            ?: throw IllegalStateException("No DOM model found for pom ${xmlFile.name}")
    )

    private val nodesByName = HashMap<String, XmlTag>()
    private val projectElement: XmlTag

    init {
        var projectElement: XmlTag? = null

        xmlFile.document?.accept(object : PsiElementVisitor(), PsiRecursiveVisitor {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                when (element) {
                    is XmlTag if element.localName in recommendedElementsOrder && element.parent === projectElement -> {
                        nodesByName[element.localName] = element
                    }

                    is XmlTag if element.localName == "project" -> {
                        projectElement = element
                        element.acceptChildren(this)
                    }

                    else -> {
                        element.acceptChildren(this)
                    }
                }
            }
        })

        require(projectElement != null) { "pom file should have project element" }
        this.projectElement = projectElement!!
    }

    fun addProperty(name: String, value: String) {
        val properties = ensureElement(projectElement, "properties")
        val existing = properties.children.filterIsInstance<XmlTag>().filter { it.localName == name }

        if (existing.isNotEmpty()) {
            for (tag in existing) {
                val textNode = tag.children.filterIsInstance<XmlText>().firstOrNull()
                if (textNode != null) {
                    textNode.value = value
                } else {
                    tag.replace(projectElement.createChildTag(name, value))
                }
            }
        } else {
            properties.add(projectElement.createChildTag(name, value))
        }
    }

    fun findProperty(name: String): XmlTag? {
        val propertiesNode = nodesByName["properties"] ?: return null
        return propertiesNode.findFirstSubTag(name)
    }

    fun addDependency(
        artifact: MavenId,
        scope: MavenArtifactScope? = null,
        classifier: String? = null,
        optional: Boolean = false,
        systemPath: String? = null
    ): MavenDomDependency {
        require(systemPath == null || scope == MavenArtifactScope.SYSTEM) { "systemPath is only applicable for system scope dependency" }
        require(artifact.groupId != null) { "groupId shouldn't be null" }
        require(artifact.artifactId != null) { "artifactId shouldn't be null" }

        ensureDependencies()
        val versionless = artifact.withNoVersion().withoutJDKSpecificSuffix()
        val dependency = domModel.dependencies.dependencies.firstOrNull { it.matches(versionless) } ?: domModel.dependencies.addDependency()
        dependency.groupId.stringValue = artifact.groupId
        dependency.artifactId.stringValue = artifact.artifactId
        dependency.version.stringValue = artifact.version
        dependency.classifier.stringValue = classifier

        if (scope != null && scope != MavenArtifactScope.COMPILE) {
            dependency.scope.stringValue = scope.name.toLowerCaseAsciiOnly()
        }

        if (optional) {
            dependency.optional.value = true
        }

        dependency.systemPath.stringValue = systemPath
        dependency.ensureTagExists()

        return dependency
    }

    fun addKotlinPlugin(version: String?): MavenDomPlugin = addPlugin(kotlinPluginId(version))

    fun addPlugin(artifact: MavenId): MavenDomPlugin {
        ensureBuild()

        val groupArtifact = artifact.withNoVersion()
        val plugin = findPlugin(groupArtifact) ?: domModel.build.plugins.addPlugin()
        plugin.groupId.stringValue = artifact.groupId
        plugin.artifactId.stringValue = artifact.artifactId
        if (artifact.version != null) {
            plugin.version.stringValue = artifact.version
        }
        plugin.ensureTagExists()

        return plugin
    }

    fun findPlugin(groupArtifact: MavenId): MavenDomPlugin? = domModel.build.plugins.plugins.firstOrNull { it.matches(groupArtifact) }

    fun isPluginAfter(plugin: MavenDomPlugin, referencePlugin: MavenDomPlugin): Boolean {
        require(plugin.parent === referencePlugin.parent) { "Plugins should be siblings" }
        require(plugin !== referencePlugin)

        val referenceElement = referencePlugin.xmlElement!!
        var e: PsiElement = plugin.xmlElement!!

        while (e !== referenceElement) {
            val prev = e.prevSibling ?: return false
            e = prev
        }

        return true
    }

    private fun ensurePluginAfter(plugin: MavenDomPlugin, referencePlugin: MavenDomPlugin): MavenDomPlugin {
        if (!isPluginAfter(plugin, referencePlugin)) {
            // rearrange
            val referenceElement = referencePlugin.xmlElement!!
            val newElement = referenceElement.parent.addAfter(plugin.xmlElement!!, referenceElement)
            plugin.xmlTag?.delete()

            return domModel.build.plugins.plugins.single { it.xmlElement == newElement }
        }

        return plugin
    }

    fun findKotlinPlugins(): List<MavenDomPlugin> = domModel.build.plugins.plugins.filter { it.isKotlinMavenPlugin() }
    fun findKotlinExecutions(vararg goals: String): List<MavenDomPluginExecution> = findKotlinExecutions().filter { it.goals.goals.any { it.rawText in goals } }
    fun findKotlinExecutions(): List<MavenDomPluginExecution> = findKotlinPlugins().flatMap { it.executions.executions }

    private fun findExecutions(plugin: MavenDomPlugin) = plugin.executions.executions
    fun findExecutions(plugin: MavenDomPlugin, vararg goals: String): List<MavenDomPluginExecution> =
        findExecutions(plugin).filter { it.goals.goals.any { it.rawText in goals } }

    fun addExecution(plugin: MavenDomPlugin, executionId: String, phase: String, goals: List<String>): MavenDomPluginExecution {
        require(executionId.isNotEmpty()) { "executionId shouldn't be empty" }
        require(phase.isNotEmpty()) { "phase shouldn't be empty" }

        val execution = plugin.executions.executions.firstOrNull { it.id.stringValue == executionId } ?: plugin.executions.addExecution()
        execution.id.stringValue = executionId
        execution.phase.stringValue = phase

        val existingGoals = execution.goals.goals.mapNotNull { it.rawText }
        for (goal in goals.filter { it !in existingGoals }) {
            val goalTag = execution.goals.ensureTagExists().createChildTag("goal", goal)
            execution.goals.xmlTag?.add(goalTag)
        }

        return execution
    }

    fun addKotlinExecution(
        module: Module,
        plugin: MavenDomPlugin,
        executionId: String,
        phase: String,
        isTest: Boolean,
        goals: List<String>
    ) {
        val execution = addExecution(plugin, executionId, phase, goals)

        val sourceDirs = ModuleRootManager.getInstance(module)
            .contentEntries
            .flatMap { it.sourceFolders.filter { it.isRelatedSourceRoot(isTest) } }
            .mapNotNull { it.file }
            .mapNotNull { VfsUtilCore.getRelativePath(it, xmlFile.virtualFile.parent, '/') }

        executionSourceDirs(execution, sourceDirs)
    }

    fun isPluginExecutionMissing(plugin: MavenPlugin?, excludedExecutionId: String, goal: String): Boolean =
        plugin == null || plugin.executions.none { it.executionId != excludedExecutionId && goal in it.goals }

    fun hasJavacPlugin(): Boolean {
        return findPlugin(
            MavenId(
                "org.apache.maven.plugins",
                "maven-compiler-plugin",
                null
            )
        ) != null
    }

    fun addJavacExecutions(module: Module, kotlinPlugin: MavenDomPlugin) {
        val javacPlugin = ensurePluginAfter(addPlugin(MavenId("org.apache.maven.plugins", "maven-compiler-plugin", null)), kotlinPlugin)

        //We are doing this here rather than below, because unit tests cannot resolve the maven project
        val defaultCompileExecution = findExecution(javacPlugin, "default-compile")
        if (defaultCompileExecution == null) {
            addExecution(javacPlugin, "default-compile", "none", emptyList())
        }

        val defaultTestCompileExecution = findExecution(javacPlugin, "default-testCompile")
        if (defaultTestCompileExecution == null) {
            addExecution(javacPlugin, "default-testCompile", "none", emptyList())
        }

        //Show warning that we could not automatically disable the default-* phases
        if ((defaultCompileExecution != null && defaultCompileExecution.phase.stringValue != "none") ||
            (defaultTestCompileExecution != null && defaultTestCompileExecution.phase.stringValue != "none")
        ) {
            @Suppress("DialogTitleCapitalization") //It is properly capitalized
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Configure Kotlin")
                .createNotification(
                    title = KotlinProjectConfigurationBundle.message("configure.kotlin"),
                    content = KotlinProjectConfigurationBundle.message("warning.failed.disable.default.compile.message"),
                    type = NotificationType.WARNING,
                )
                .addAction(NotificationAction.createSimple(KotlinProjectConfigurationBundle.message("warning.failed.disable.default.compile.action")) {
                    BrowserUtil.browse(KotlinProjectConfigurationBundle.message("warning.failed.disable.default.compile.link"))
                }).notify(module.project)
        }

        val project: MavenProject =
            MavenProjectsManager.getInstance(module.project).findProject(module) ?: run {
                if (isUnitTestMode()) {
                    LOG.warn("WARNING: Bad project configuration in tests. Javac execution configuration was skipped.")
                    return
                }
                error("Can't find maven project for $module")
            }

        val plugin = project.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin")

        if (isPluginExecutionMissing(plugin, "default-compile", "compile")) {
            addExecution(javacPlugin, "compile", DefaultPhases.Compile, listOf("compile"))
        }

        if (isPluginExecutionMissing(plugin, "default-testCompile", "testCompile")) {
            addExecution(javacPlugin, "testCompile", DefaultPhases.TestCompile, listOf("testCompile"))
        }
    }

    private fun findExecution(plugin: MavenDomPlugin, executionId: String): MavenDomPluginExecution? {
        return plugin.executions.executions.firstOrNull { it.id.stringValue == executionId }
    }

    fun findExecution(plugin: MavenPlugin?, executionId: String): MavenPlugin.Execution? {
        return plugin?.executions?.firstOrNull { it.executionId == executionId }
    }

    fun executionSourceDirs(execution: MavenDomPluginExecution, sourceDirs: List<String>, forceSingleSource: Boolean = false) {
        ensureBuild()

        val isTest = execution.goals.goals.any { it.stringValue == KotlinGoals.TestCompile || it.stringValue == KotlinGoals.TestJs }
        val defaultDir = if (isTest) "test" else "main"
        val singleDirectoryElement = if (isTest) {
            domModel.build.testSourceDirectory
        } else {
            domModel.build.sourceDirectory
        }

        if (sourceDirs.isEmpty() || sourceDirs.singleOrNull() == "src/$defaultDir/java") {
            execution.configuration.xmlTag?.findSubTags("sourceDirs")?.forEach { it.deleteCascade() }
            singleDirectoryElement.undefine()
        } else if (sourceDirs.size == 1 && !forceSingleSource) {
            singleDirectoryElement.stringValue = sourceDirs.single()
            execution.configuration.xmlTag?.findSubTags("sourceDirs")?.forEach { it.deleteCascade() }
        } else {
            val sourceDirsTag = executionConfiguration(execution, "sourceDirs")
            execution.configuration.createChildTag("sourceDirs")?.let { newSourceDirsTag ->
                for (dir in sourceDirs) {
                    newSourceDirsTag.add(newSourceDirsTag.createChildTag("source", dir))
                }
                sourceDirsTag.replace(newSourceDirsTag)
            }
        }
    }

    fun executionSourceDirs(execution: MavenDomPluginExecution): List<String> {
        return execution.configuration.xmlTag
            ?.getChildrenOfType<XmlTag>()
            ?.firstOrNull { it.localName == "sourceDirs" }
            ?.getChildrenOfType<XmlTag>()
            ?.map { it.getChildrenOfType<XmlText>().joinToString("") { it.text } }
            ?: emptyList()
    }

    private fun executionConfiguration(execution: MavenDomPluginExecution, name: String): XmlTag {
        val configurationTag = execution.configuration.ensureTagExists()!!
        return configurationTag.findSubTagOrCreate(name)
    }

    fun addPluginConfiguration(plugin: MavenDomPlugin, optionName: String, optionValue: String): XmlTag {
        val configurationTag = plugin.configuration.ensureTagExists()
        val existingTag = configurationTag.findFirstSubTag(optionName)
        if (existingTag != null) {
            existingTag.value.text = optionValue
        } else {
            configurationTag.add(configurationTag.createChildTag(optionName, optionValue))
        }
        return configurationTag
    }

    private fun addPluginRepository(
        id: String,
        name: String,
        url: String,
        snapshots: Boolean = false,
        releases: Boolean = true
    ): MavenDomRepository {
        ensurePluginRepositories()

        return addRepository(
            id,
            name,
            url,
            snapshots,
            releases,
            { domModel.pluginRepositories.pluginRepositories },
            { domModel.pluginRepositories.addPluginRepository() })
    }

    fun addPluginRepository(description: RepositoryDescription) {
        addPluginRepository(description.id, description.name, description.url, description.isSnapshot, true)
    }

    private fun addLibraryRepository(
        id: String,
        name: String,
        url: String,
        snapshots: Boolean = false,
        releases: Boolean = true
    ): MavenDomRepository {
        ensureRepositories()

        return addRepository(
            id,
            name,
            url,
            snapshots,
            releases,
            { domModel.repositories.repositories },
            { domModel.repositories.addRepository() })
    }

    fun addLibraryRepository(description: RepositoryDescription) {
        addLibraryRepository(description.id, description.name, description.url, description.isSnapshot, true)
    }

    private fun addRepository(
        id: String,
        name: String,
        url: String,
        snapshots: Boolean,
        releases: Boolean,
        existing: () -> List<MavenDomRepository>,
        create: () -> MavenDomRepository
    ): MavenDomRepository {

        val repository =
            existing().firstOrNull { it.id.stringValue == id } ?: existing().firstOrNull { it.url.stringValue == url } ?: create()

        if (repository.id.isEmpty()) {
            repository.id.stringValue = id
        }
        if (repository.name.isEmpty()) {
            repository.name.stringValue = name
        }
        if (repository.url.isEmpty()) {
            repository.url.stringValue = url
        }
        repository.releases.enabled.value = repository.releases.enabled.value?.let { it || releases } ?: releases
        repository.snapshots.enabled.value = repository.snapshots.enabled.value?.let { it || snapshots } ?: snapshots

        repository.ensureTagExists()

        return repository
    }

    fun findDependencies(artifact: MavenId, scope: MavenArtifactScope? = null): List<MavenDomDependency> =
        domModel.dependencies.findDependencies(artifact, scope)

    fun findDependencies(artifacts: List<MavenId>, scope: MavenArtifactScope? = null): List<MavenDomDependency> {
        return domModel.dependencies.findDependencies(artifacts, scope)
    }

    private fun ensureBuild(): XmlTag = ensureElement(projectElement, "build")

    private fun ensureDependencies(): XmlTag = ensureElement(projectElement, "dependencies")
    private fun ensurePluginRepositories(): XmlTag = ensureElement(projectElement, "pluginRepositories")
    private fun ensureRepositories(): XmlTag = ensureElement(projectElement, "repositories")

    private fun MavenDomPlugin.isKotlinMavenPlugin() = groupId.stringValue == KotlinMavenConfigurator.GROUP_ID
            && artifactId.stringValue == KotlinMavenConfigurator.MAVEN_PLUGIN_ID

    private fun MavenId.withNoVersion() = MavenId(groupId, artifactId, null)
    private fun MavenId.withoutJDKSpecificSuffix() = MavenId(
        groupId,
        artifactId?.substringBeforeLast("-jre")?.substringBeforeLast("-jdk"),
        null
    )

    private fun ensureElement(projectElement: XmlTag, localName: String): XmlTag {
        require(localName in recommendedElementsOrder) { "You can only ensure presence or the elements from the recommendation list" }

        return nodesByName.getOrPut(localName) {
            val tag = projectElement.createChildTag(localName, projectElement.namespace, null, false)!!
            val newTag = insertTagImpl(projectElement, tag)

            insertEmptyLines(newTag)

            newTag
        }
    }

    private fun insertTagImpl(projectElement: XmlTag, tag: XmlTag): XmlTag {
        val middle = recommendedOrderAsList.indexOf(tag.localName)
        require(middle != -1) { "You can only insert element from the recommendation list" }

        for (idx in middle - 1 downTo 0) {
            val reference = nodesByName[recommendedOrderAsList[idx]]
            if (reference != null) {
                return projectElement.addAfter(tag, reference) as XmlTag
            }
        }

        for (idx in middle + 1..recommendedOrderAsList.lastIndex) {
            val reference = nodesByName[recommendedOrderAsList[idx]]
            if (reference != null) {
                return projectElement.addBefore(tag, reference) as XmlTag
            }
        }

        return projectElement.add(tag) as XmlTag
    }

    private fun insertEmptyLines(node: XmlTag) {
        node.prevSibling?.let { before ->
            if (!(before.hasEmptyLine() || before.lastChild?.hasEmptyLine() == true)) {
                node.parent.addBefore(createEmptyLine(), node)
            }
        }
        node.nextSibling?.let { after ->
            if (!(after.hasEmptyLine() || after.firstChild?.hasEmptyLine() == true)) {
                node.parent.addAfter(createEmptyLine(), node)
            }
        }
    }

    private fun PsiElement.hasEmptyLine() = this is PsiWhiteSpace && text.count { it == '\n' } > 1

    private fun createEmptyLine(): XmlText {
        return XmlElementFactory.getInstance(xmlFile.project).createTagFromText("<s>\n\n</s>").children.first { it is XmlText } as XmlText
    }

    private fun GenericDomValue<String>.isEmpty() = !exists() || stringValue.isNullOrEmpty()

    private fun SourceFolder.isRelatedSourceRoot(isTest: Boolean): Boolean {
        return if (isTest) {
            rootType == JavaSourceRootType.TEST_SOURCE || rootType == TestSourceKotlinRootType
        } else {
            rootType == JavaSourceRootType.SOURCE || rootType == SourceKotlinRootType
        }
    }

    @Suppress("Unused")
    object DefaultPhases {
        const val None: String = "none"
        const val Validate: String = "validate"
        const val Initialize: String = "initialize"
        const val GenerateSources: String = "generate-sources"
        const val ProcessSources: String = "process-sources"
        const val GenerateResources: String = "generate-resources"
        const val ProcessResources: String = "process-resources"
        const val Compile: String = "compile"
        const val ProcessClasses: String = "process-classes"
        const val GenerateTestSources: String = "generate-test-sources"
        const val ProcessTestSources: String = "process-test-sources"
        const val GenerateTestResources: String = "generate-test-resources"
        const val ProcessTestResources: String = "process-test-resources"
        const val TestCompile: String = "test-compile"
        const val ProcessTestClasses: String = "process-test-classes"
        const val Test: String = "test"
        const val PreparePackage: String = "prepare-package"
        const val Package: String = "package"
        const val PreIntegrationTest: String = "pre-integration-test"
        const val IntegrationTest: String = "integration-test"
        const val PostIntegrationTest: String = "post-integration-test"
        const val Verify: String = "verify"
        const val Install: String = "install"
        const val Deploy: String = "deploy"
    }

    object KotlinGoals {
        const val Compile: String = "compile"
        const val TestCompile: String = "test-compile"

        const val Js: String = "js"
        const val TestJs: String = "test-js"
        const val MetaData: String = "metadata"

        val JvmGoals: List<String> = listOf(Compile, TestCompile)
        val CompileGoals: List<String> = listOf(Compile, TestCompile, Js, TestJs, MetaData)
    }

    companion object {
        private val LOG = Logger.getInstance(PomFile::class.java)

        fun forFileOrNull(xmlFile: XmlFile): PomFile? =
            MavenDomUtil.getMavenDomProjectModel(xmlFile.project, xmlFile.virtualFile)?.let { PomFile(xmlFile, it) }

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("We shouldn't use phase but additional compiler configuration in most cases")
        fun getPhase(hasJavaFiles: Boolean, isTest: Boolean): String = when {
            hasJavaFiles -> when {
                isTest -> DefaultPhases.ProcessTestSources
                else -> DefaultPhases.ProcessSources
            }
            else -> when {
                isTest -> DefaultPhases.TestCompile
                else -> DefaultPhases.Compile
            }
        }

        // from maven code convention: https://maven.apache.org/developers/conventions/code.html
        val recommendedElementsOrder: LinkedHashSet<String> = """
          <modelVersion/>
          <parent/>

          <groupId/>
          <artifactId/>
          <version/>
          <packaging/>

          <name/>
          <description/>
          <url/>
          <inceptionYear/>
          <organization/>
          <licenses/>

          <developers/>
          <contributors/>

          <mailingLists/>

          <prerequisites/>

          <modules/>

          <scm/>
          <issueManagement/>
          <ciManagement/>
          <distributionManagement/>

          <properties/>

          <dependencyManagement/>
          <dependencies/>

          <repositories/>
          <pluginRepositories/>

          <build/>

          <reporting/>

          <profiles/>
        """.lines()
            .map { it.trim().removePrefix("<").removeSuffix("/>").trim() }
            .filter(String::isNotEmpty)
            .toCollection(LinkedHashSet())

        val recommendedOrderAsList: List<String> = recommendedElementsOrder.toList()
    }
}

@ApiStatus.Internal
fun PomFile.changeLanguageVersion(languageVersion: String?, apiVersion: String?): PsiElement? {
    val kotlinPlugin = findPlugin(kotlinPluginId(null)) ?: return null
    val languageElement = languageVersion?.let {
        changeConfigurationOrProperty(kotlinPlugin, "languageVersion", "kotlin.compiler.languageVersion", it)
    }
    val apiElement = apiVersion?.let {
        changeConfigurationOrProperty(kotlinPlugin, "apiVersion", "kotlin.compiler.apiVersion", it)
    }
    return languageElement ?: apiElement
}

@ApiStatus.Internal
fun PomFile.addKotlinCompilerPlugins(name: String) {
    val kotlinPlugin = findPlugin(kotlinPluginId(null)) ?: return
    val configurationTag = kotlinPlugin.configuration.ensureTagExists()
    val compilerPluginsTag = configurationTag.findSubTagOrCreate("compilerPlugins")
    compilerPluginsTag.findSubTags("plugin").firstOrNull { it.value.trimmedText == name } ?: run {
        val pluginTag = compilerPluginsTag.createChildTag("plugin", name)
        compilerPluginsTag.add(pluginTag)
    }
}

internal fun MavenDomDependencies.findDependencies(artifact: MavenId, scope: MavenArtifactScope? = null) =
    findDependencies(SmartList(artifact), scope)

internal fun MavenDomDependencies.findDependencies(artifacts: List<MavenId>, scope: MavenArtifactScope? = null): List<MavenDomDependency> {
    return dependencies.filter { dependency ->
        artifacts.any { artifact ->
            dependency.matches(artifact, scope)
        }
    }
}

private fun MavenDomDependency.matches(artifact: MavenId, scope: MavenArtifactScope?) =
    this.matches(artifact) &&
            (this.scope.stringValue == scope?.name?.toLowerCaseAsciiOnly() || scope == null && this.scope.stringValue == "compile")

private fun MavenDomArtifactCoordinates.matches(artifact: MavenId) =
    (artifact.groupId == null || groupId.stringValue == artifact.groupId)
            && (artifact.artifactId == null || artifactId.stringValue == artifact.artifactId)
            && (artifact.version == null || version.stringValue == artifact.version)

private fun PomFile.changeConfigurationOrProperty(
    kotlinPlugin: MavenDomPlugin,
    configurationTagName: String,
    propertyName: String, value: String
): XmlTag {
    val configuration = kotlinPlugin.configuration
    if (configuration.exists()) {
        val subTag = configuration.xmlTag?.findFirstSubTag(configurationTagName)
        if (subTag != null) {
            subTag.value.text = value
            return subTag
        }
    }

    val propertyTag = findProperty(propertyName)
    if (propertyTag != null) {
        val textNode = propertyTag.children.filterIsInstance<XmlText>().firstOrNull()
        if (textNode != null) {
            textNode.value = value
            return propertyTag
        }
    }

    return addPluginConfiguration(kotlinPlugin, configurationTagName, value)
}

fun PomFile.changeCoroutineConfiguration(value: String): PsiElement? {
    val kotlinPlugin = findPlugin(kotlinPluginId(null)) ?: return null
    return changeConfigurationOrProperty(kotlinPlugin, "experimentalCoroutines", "kotlin.compiler.experimental.coroutines", value)
}

fun PomFile.changeFeatureConfiguration(
    feature: LanguageFeature,
    state: LanguageFeature.State
): PsiElement? {
    val kotlinPlugin = findPlugin(kotlinPluginId(null)) ?: return null
    val configurationTag = kotlinPlugin.configuration.ensureTagExists()
    val argsSubTag = configurationTag.findSubTagOrCreate("args")
    argsSubTag.findSubTags("arg").filter { feature.name in it.value.text }.forEach { it.deleteCascade() }
    val kotlinVersion = kotlinPlugin.version.stringValue?.let(IdeKotlinVersion::opt)
    val featureArgumentString = feature.buildArgumentString(state, kotlinVersion)
    val childTag = argsSubTag.createChildTag("arg", featureArgumentString)
    return argsSubTag.add(childTag)
}

private fun MavenDomElement.createChildTag(name: String, value: String? = null): XmlTag? =
    xmlTag?.createChildTag(name, value)
private fun XmlTag.createChildTag(name: String, value: String? = null): XmlTag =
    createChildTag(name, namespace, value, false)!!
private fun XmlTag.findSubTagOrCreate(name: String): XmlTag =
    findSubTags(name).firstOrNull() ?: run {
        val childTag = createChildTag(name)
        add(childTag) as XmlTag
    }

private tailrec fun XmlTag.deleteCascade() {
    val oldParent = this.parentTag
    delete()

    if (oldParent != null && oldParent.subTags.isEmpty()) {
        oldParent.deleteCascade()
    }
}

