// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.module.Module
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.idea.maven.dom.model.MavenDomDependency
import org.jetbrains.idea.maven.dom.model.MavenDomPlugin
import org.jetbrains.idea.maven.dom.model.MavenDomPluginExecution
import org.jetbrains.idea.maven.dom.model.MavenDomShortArtifactCoordinates
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.AbstractKotlinCompilerProjectPostConfigurator
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.ConfigurationResultBuilder
import org.jetbrains.kotlin.idea.configuration.KotlinCompilerPluginProjectConfigurator
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.maven.KotlinMavenBundle
import org.jetbrains.kotlin.idea.maven.PomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.KOTLIN_VERSION_PROPERTY
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findModulePomFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.findPomXmlByFile
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator.Companion.kotlinPluginId
import org.jetbrains.kotlin.idea.maven.createChildTag
import org.jetbrains.kotlin.idea.maven.findSubTagOrCreate
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

private const val KAPT_PLUGIN_ID = "kapt"
private const val LOMBOK_FQN = "lombok.Lombok"
private val MAVEN_COMPILER_PLUGIN_ID = MavenId("org.apache.maven.plugins", "maven-compiler-plugin", null)

class KaptMavenKotlinCompilerPluginProjectConfigurator : KotlinCompilerPluginProjectConfigurator {
    override val kotlinCompilerPluginId: String = KAPT_PLUGIN_ID

    override fun isApplicable(module: Module): Boolean =
        module.findModulePomFileWithLocalOrInheritedKotlinPlugin() != null

    override fun configureModule(module: Module, configurationResultBuilder: ConfigurationResultBuilder) {
        val xmlFile = module.findModulePomFileWithLocalOrInheritedKotlinPlugin() ?: return
        val pom = PomFile.forFileOrNull(xmlFile) ?: return
        configurationResultBuilder.changedFile(xmlFile)
        val project = module.project
        val mavenProject = MavenProjectsManager.getInstance(project).findProject(module)
        val processors = pom.findMavenProcessorPaths(mavenProject).ifEmpty { return }
        project.executeWriteCommand(KotlinMavenBundle.message("command.name.configure.0", xmlFile.name), null) {
            val kotlinPlugin = pom.findPlugin(kotlinPluginId) ?: pom.addPlugin(kotlinPluginId)
            if (pom.configureKapt(module, xmlFile, kotlinPlugin, processors)) {
                configurationResultBuilder.configuredModule(module)
            }
        }
    }

    override fun configureModuleModCommand(module: Module): ModCommand {
        val xmlFile = module.findModulePomFileWithLocalOrInheritedKotlinPlugin() ?: return ModCommand.nop()
        val mavenProject = MavenProjectsManager.getInstance(module.project).findProject(module)
        val actionContext = ActionContext.from(null, xmlFile)
        return ModCommand.psiUpdate(actionContext) { updater ->
            val writablePomFile = updater.getWritable(xmlFile)
            val pom = PomFile.forFileOrNull(writablePomFile) ?: return@psiUpdate
            val processors = pom.findMavenProcessorPaths(mavenProject)
            if (processors.isEmpty()) return@psiUpdate

            val kotlinPlugin = pom.findPlugin(kotlinPluginId) ?: pom.addPlugin(kotlinPluginId)
            pom.configureKapt(module, writablePomFile, kotlinPlugin, processors)
        }.andThen(KotlinDependencyProvider.syncModCommand(xmlFile))
    }
}

class KaptMavenProjectPostConfigurator : AbstractKotlinCompilerProjectPostConfigurator(KAPT_PLUGIN_ID) {
    override fun isApplicable(module: Module): Boolean =
        module.buildSystemType == BuildSystemType.Maven &&
                compilerPluginProjectConfigurators(module).isNotEmpty() &&
                !module.hasMavenKaptConfigured() &&
                module.hasNonLombokMavenAnnotationProcessor()
}

private fun Module.hasMavenKaptConfigured(): Boolean {
    val xmlFile = findModulePomFileWithLocalOrInheritedKotlinPlugin() ?: return false
    val pom = PomFile.forFileOrNull(xmlFile) ?: return false
    val kotlinPlugin = pom.findPlugin(kotlinPluginId) ?: return false
    return kotlinPlugin.findKaptExecution() != null
}

private fun Module.hasNonLombokMavenAnnotationProcessor(): Boolean {
    val mavenProject = MavenProjectsManager.getInstance(project).findProject(this)
    val xmlFile = findModulePomFileWithLocalOrInheritedKotlinPlugin() ?: return false
    val pom = PomFile.forFileOrNull(xmlFile) ?: return false
    return pom.findMavenProcessorPaths(mavenProject).isNotEmpty() ||
            KNOWN_NON_LOMBOK_PROCESSOR_CLASSES.any { JavaLibraryUtil.hasLibraryClass(this, it) }
}

private fun Module.findModulePomFileWithLocalOrInheritedKotlinPlugin(): XmlFile? {
    val pomFile = findModulePomFile(this) ?: return null
    if (PomFile.forFileOrNull(pomFile)?.findPlugin(kotlinPluginId) != null) return pomFile

    val mavenProjectsManager = MavenProjectsManager.getInstance(project)
    var parentId = mavenProjectsManager.findProject(this)?.parentId
    while (parentId != null) {
        val parentProject = mavenProjectsManager.findProject(parentId) ?: break
        val parentPomFile = findPomXmlByFile(parentProject.file) ?: break
        if (PomFile.forFileOrNull(parentPomFile)?.findPlugin(kotlinPluginId) != null) return pomFile
        parentId = parentProject.parentId
    }

    return null
}

private fun PomFile.findMavenProcessorPaths(mavenProject: MavenProject?): List<MavenProcessorPath> {
    val declaredAnnotationProcessors = findDeclaredAnnotationProcessorPaths(mavenProject)
    val externalProcessors = mavenProject?.externalAnnotationProcessors
        ?.mapNotNull { it.toProcessorPath() }
        ?: emptyList()
    val dependencyProcessors = domModel.dependencies.dependencies.mapNotNull { it.toKnownProcessorPath(mavenProject) }
    return (declaredAnnotationProcessors + externalProcessors + dependencyProcessors).distinctBy { it.groupId to it.artifactId }
}

private fun PomFile.findDeclaredAnnotationProcessorPaths(mavenProject: MavenProject?): List<MavenProcessorPath> {
    val javacPlugin = findPlugin(MAVEN_COMPILER_PLUGIN_ID) ?: return emptyList()
    val configurationTag = javacPlugin.configuration.xmlTag ?: return emptyList()
    val annotationProcessorPaths = configurationTag.findFirstSubTag("annotationProcessorPaths") ?: return emptyList()
    return (annotationProcessorPaths.findSubTags("path") +
            annotationProcessorPaths.findSubTags("dependency") +
            annotationProcessorPaths.findSubTags("annotationProcessorPath"))
        .mapNotNull { it.toProcessorPath(mavenProject) }
}

private fun PomFile.configureKapt(
    module: Module,
    xmlFile: XmlFile,
    kotlinPlugin: MavenDomPlugin,
    processors: List<MavenProcessorPath>
): Boolean {
    val oldText = xmlFile.text
    val kaptExecution = kotlinPlugin.findKaptExecution() ?: kotlinPlugin.createKaptExecution()
    kaptExecution.configureSourceDirs(xmlFile)
    kaptExecution.configureAnnotationProcessorPaths(processors)

    if (processors.any { it.copy(version = null) in KNOWN_PROCESSORS_TO_WRAP }) {
        kaptExecution.configureAnnotationProcessorPaths(
            listOf(MavenProcessorPath("org.jetbrains.kotlin", "kotlin-metadata-jvm", $$"${$$KOTLIN_VERSION_PROPERTY}"))
        )
    }

    if (!module.hasLombokDependency()) {
        disableJavacAnnotationProcessing()
    }
    addJavacExecutions(module, kotlinPlugin)

    return oldText != xmlFile.text
}

private fun PomFile.disableJavacAnnotationProcessing() {
    val javacPlugin = addPlugin(MAVEN_COMPILER_PLUGIN_ID)
    val configurationTag = javacPlugin.configuration.ensureTagExists()
    configurationTag.findSubTagOrCreate("proc").value.text = "none"
}

private fun MavenDomPlugin.findKaptExecution(): MavenDomPluginExecution? =
    executions.executions.firstOrNull { execution ->
        execution.goals.goals.any { it.stringValue == KAPT_PLUGIN_ID }
    }

private fun MavenDomPlugin.createKaptExecution(): MavenDomPluginExecution {
    val execution = executions.addExecution()
    execution.id.stringValue = KAPT_PLUGIN_ID
    val goalsTag = execution.goals.ensureTagExists()
    goalsTag.add(goalsTag.createChildTag("goal", KAPT_PLUGIN_ID))
    return execution
}

private fun MavenDomPluginExecution.configureSourceDirs(xmlFile: XmlFile) {
    val projectRoot = (xmlFile.takeIf { it.isPhysical } ?: xmlFile.originalFile).virtualFile?.parent ?: return
    val sourceDirs = listOf("src/main/kotlin", "src/main/java")
        .filter { projectRoot.findFileByRelativePath(it) != null }
        .ifEmpty { listOf("src/main/kotlin", "src/main/java") }

    val sourceDirsTag = configuration.ensureTagExists().findSubTagOrCreate("sourceDirs")
    val existingSourceDirs = sourceDirsTag.findSubTags("sourceDir").map { it.value.text }.toSet()
    for (sourceDir in sourceDirs) {
        if (sourceDir !in existingSourceDirs) {
            sourceDirsTag.add(sourceDirsTag.createChildTag("sourceDir", sourceDir))
        }
    }
}

private fun MavenDomPluginExecution.configureAnnotationProcessorPaths(processors: List<MavenProcessorPath>) {
    val annotationProcessorPaths = configuration.ensureTagExists().findSubTagOrCreate("annotationProcessorPaths")
    val existingPaths = annotationProcessorPaths.findSubTags("annotationProcessorPath")
        .map { it.findFirstSubTag("groupId")?.value?.text to it.findFirstSubTag("artifactId")?.value?.text }
        .toSet()

    for ((groupId, artifactId, version) in processors) {
        if (groupId to artifactId in existingPaths) continue

        val processorTag = annotationProcessorPaths.createChildTag("annotationProcessorPath")
        processorTag.add(processorTag.createChildTag("groupId", groupId))
        processorTag.add(processorTag.createChildTag("artifactId", artifactId))
        version?.let { processorTag.add(processorTag.createChildTag("version", it)) }
        annotationProcessorPaths.add(processorTag)
    }
}

private fun MavenDomDependency.toKnownProcessorPath(mavenProject: MavenProject?): MavenProcessorPath? {
    val processorPath = MavenProcessorPath.of(this) ?: return null
    if (processorPath !in KNOWN_PROCESSOR_ARTIFACTS) return null
    val version = version.stringValue?.takeIf { it.isNotBlank() }
        ?: mavenProject?.findManagedDependencyVersion(processorPath.groupId, processorPath.artifactId)
    return processorPath.copy(version = version)
}

private fun MavenArtifact.toProcessorPath(): MavenProcessorPath? {
    val groupId = groupId.takeIf { it.isNotBlank() } ?: return null
    val artifactId = artifactId.takeIf { it.isNotBlank() && it != "lombok" } ?: return null
    val version = version.takeIf { it.isNotBlank() }
    return MavenProcessorPath(groupId, artifactId, version)
}

private fun XmlTag.toProcessorPath(mavenProject: MavenProject?): MavenProcessorPath? {
    val groupId = findFirstSubTag("groupId")?.value?.text?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val artifactId = findFirstSubTag("artifactId")?.value?.text?.trim()?.takeIf { it.isNotBlank() && it != "lombok" } ?: return null
    val version = findFirstSubTag("version")?.value?.text?.trim()?.takeIf { it.isNotBlank() }
        ?: mavenProject?.findManagedDependencyVersion(groupId, artifactId)
    return MavenProcessorPath(groupId, artifactId, version)
}

private fun Module.hasLombokDependency(): Boolean =
    JavaLibraryUtil.hasLibraryClass(this, LOMBOK_FQN)

private data class MavenProcessorPath(val groupId: String, val artifactId: String, val version: String?) {
    constructor(groupId: String, artifactId: String) : this(groupId, artifactId, null)

    companion object {
        fun of(coordinates: MavenDomShortArtifactCoordinates): MavenProcessorPath? {
            val groupId = coordinates.groupId?.stringValue ?: return null
            val artifactId = coordinates.artifactId.stringValue ?: return null
            return MavenProcessorPath(groupId, artifactId, null)
        }
    }
}

private val KNOWN_PROCESSOR_ARTIFACTS = setOf(
    MavenProcessorPath("org.mapstruct", "mapstruct-processor"),
    MavenProcessorPath("com.google.dagger", "dagger-compiler"),
    MavenProcessorPath("com.google.dagger", "hilt-compiler"),
    MavenProcessorPath("androidx.room", "room-compiler"),
    MavenProcessorPath("org.hibernate.orm", "hibernate-jpamodelgen"),
    MavenProcessorPath("org.hibernate", "hibernate-jpamodelgen"),
    MavenProcessorPath("io.micronaut", "micronaut-inject-java"),
    MavenProcessorPath("com.google.auto.service", "auto-service"),
    MavenProcessorPath("com.querydsl", "querydsl-apt"),
)

private val KNOWN_PROCESSORS_TO_WRAP = setOf(
    MavenProcessorPath("org.mapstruct", "mapstruct-processor"),
)

private val KNOWN_NON_LOMBOK_PROCESSOR_CLASSES = listOf(
    "org.mapstruct.ap.MappingProcessor",
    "dagger.internal.codegen.ComponentProcessor",
    "com.google.dagger.hilt.processor.internal.root.RootProcessor",
    "androidx.room.RoomProcessor",
    "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor",
    "io.micronaut.annotation.processing.TypeElementVisitorProcessor",
    "com.google.auto.service.processor.AutoServiceProcessor",
    "com.querydsl.apt.jpa.JPAAnnotationProcessor",
)
