// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.application.options.CodeStyle
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.config.apiVersionView
import org.jetbrains.kotlin.config.languageVersionView
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.projectConfiguration.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

/**
 * A module builder for simple Kotlin JPS modules.
 * If [existingKotlinStdLib] is defined, then the given library will be used as the Kotlin stdlib for the new module.
 * Otherwise, a new library is created for the project and used for the new module.
 */
internal class KotlinModuleBuilder(
    private val existingKotlinStdLib: LibraryOrderEntry?,
    private val isCreatingNewProject: Boolean,
    private val useCompactProjectStructure: Boolean
) : JavaModuleBuilder() {

    companion object {
        // We target 1.8 as the default JPS target for now because it is a safe option.
        // In the future we should consider making it more dynamic which target is used.
        private const val DEFAULT_JVM_TARGET = "1.8"

        // We specify the name here to avoid having to depend on its module, which would not be allowed.
        private const val REPLACE_UNTIL_WITH_RANGE_UNTIL_INSPECTION_NAME = "ReplaceUntilWithRangeUntil"
    }

    init {
        // We do not want the default Java src folder to be created, we create the Kotlin ones ourselves in createKotlinSourcePaths
        sourcePaths = emptyList()
    }


    /**
     * A source set at the [path] of the given [type] to be registered with the JPS module system.
     * If [shouldCreate] is true, the path is also created in the project, otherwise the folder will not be created.
     * This is useful for folders that we want IntelliJ to recognize when the user creates them (i,e. test, resources when
     * using the compact layout), but we don't want to create them by default to avoid cluttering up a simple project.
     */
    private data class KotlinSourceEntry(val path: String, val type: JpsModuleSourceRootType<*>, val shouldCreate: Boolean = true)

    private fun resolveRelativePath(relativePath: String): File? {
        val contentEntryPath = getContentEntryPath() ?: return null
        return File(contentEntryPath, relativePath.replace("/", File.separator))
    }

    /**
     * Creates the sourceSets used by Kotlin for the module and creates the corresponding folders
     * in the project, if they should be created by default.
     */
    private fun createKotlinSourcePaths(contentEntry: ContentEntry) {
        val entries = if (useCompactProjectStructure) {
            listOf(
                KotlinSourceEntry("src", type = JavaSourceRootType.SOURCE),
                KotlinSourceEntry("resources", type = JavaResourceRootType.RESOURCE, shouldCreate = false),
                KotlinSourceEntry("test", type = JavaSourceRootType.TEST_SOURCE, shouldCreate = false),
                KotlinSourceEntry("testResources", type = JavaResourceRootType.TEST_RESOURCE, shouldCreate = false)
            )
        } else {
            listOf(
                KotlinSourceEntry("src/main/kotlin", type = JavaSourceRootType.SOURCE),
                KotlinSourceEntry("src/main/resources", type = JavaResourceRootType.RESOURCE),
                KotlinSourceEntry("src/test/kotlin", type = JavaSourceRootType.TEST_SOURCE),
                KotlinSourceEntry("src/test/resources", type = JavaResourceRootType.TEST_RESOURCE)
            )
        }
        for (entry in entries) {
            val path = resolveRelativePath(entry.path) ?: continue
            if (entry.shouldCreate) {
                path.mkdirs()
            }
            contentEntry.addSourceFolder(VfsUtil.pathToUrl(path.path), entry.type)
        }
    }

    override fun doAddContentEntry(modifiableRootModel: ModifiableRootModel): ContentEntry? {
        val contentEntry = super.doAddContentEntry(modifiableRootModel) ?: return null
        createKotlinSourcePaths(contentEntry)
        return contentEntry
    }

    /**
     * Adds a new Kotlin Standard Library dependency to the new module as a project library.
     * The newly created dependency is a maven dependency and might be downloaded during this function call
     * in case the library does not exist on disk already.
     */
    private fun createKotlinJavaRuntime(rootModel: ModifiableRootModel): LibraryOrderEntry? {
        val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(rootModel.project)
        val kotlinJavaRuntime = projectLibraryTable.createLibrary(JavaRuntimeLibraryDescription.LIBRARY_NAME) as? LibraryEx ?: return null
        kotlinJavaRuntime.modifiableModel.apply {
            kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
            val repositoryProperties = RepositoryLibraryProperties(
                /* groupId = */ KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID,
                /* artifactId = */ PathUtil.KOTLIN_JAVA_STDLIB_NAME,
                /* version = */ Versions.KOTLIN.text,
                /* includeTransitiveDependencies = */ true,
                /* excludedDependencies = */ emptyList()
            )
            properties = repositoryProperties
            val dependencies = JarRepositoryManager.loadDependenciesModal(
                /* project = */ rootModel.project,
                /* libraryProps = */ repositoryProperties,
                /* loadSources = */ true,
                /* loadJavadoc = */ true,
                /* copyTo = */ null,
                /* repositories = */ null
            )

            dependencies.forEach {
                addRoot(it.file, it.type)
            }
        }.commit()
        return rootModel.addLibraryEntry(kotlinJavaRuntime)
    }

    /**
     * If there is an [existingKotlinStdLib], adds it to the new module as a library.
     * Otherwise, creates a new Kotlin Standard Library dependency and adds it as a project library. (see [createKotlinJavaRuntime])
     */
    private fun setupKotlinLibrary(rootModel: ModifiableRootModel) {
        val stdLib = existingKotlinStdLib ?: createKotlinJavaRuntime(rootModel)
        stdLib?.library?.let {
            rootModel.addLibraryEntry(it)
        }
    }

    /**
     * Configures the jvmTarget, apiVersion and languageVersion of the project.
     * If the project already has values configured for these fields, then the existing values are left intact.
     * Otherwise, the default values also used when creating a new project are applied to the project.
     */
    private fun applyKotlinCompilerArguments(modifiableRootModel: ModifiableRootModel) {
        val jvmCompilerArgumentsHolder = Kotlin2JvmCompilerArgumentsHolder.getInstance(modifiableRootModel.project)
        if (jvmCompilerArgumentsHolder.settings.jvmTarget == null) {
            jvmCompilerArgumentsHolder.update {
                jvmTarget = DEFAULT_JVM_TARGET
            }
        }

        val commonCompilerArgumentsHolder = KotlinCommonCompilerArgumentsHolder.getInstance(modifiableRootModel.project)
        val commonSettings = commonCompilerArgumentsHolder.settings
        val kotlinVersion = IdeKotlinVersion.parse(Versions.KOTLIN.text).getOrNull()
        if (commonSettings.apiVersion == null && commonSettings.languageVersion == null && kotlinVersion != null) {
            commonCompilerArgumentsHolder.update {
                apiVersionView = VersionView.Specific(kotlinVersion.apiVersion)
                languageVersionView = VersionView.Specific(kotlinVersion.languageVersion)
            }
        }
    }

    /**
     * Changes the project's code style settings to the [KotlinStyleGuideCodeStyle],
     * unless there already is an explicit code style applied to the project.
     */
    private fun applyKotlinCodeStyle(rootModel: ModifiableRootModel) {
        val existingSettings = CodeStyle.getSettings(rootModel.project)
        if (existingSettings.kotlinCodeStyleDefaults() != null) {
            // Some code style settings were already applied, use them rather than the new default
            return
        }
        ProjectCodeStyleImporter.apply(rootModel.project, KotlinStyleGuideCodeStyle.INSTANCE)
    }

    /**
     * Enables inspections in new projects attempting to prevent developers from using deprecated language features.
     */
    private fun enableNewProjectInspections(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            // Inspections do not seem to get added properly in unit test mode, causing Exceptions.
            // They do work properly when actually creating the project.
            return
        }
        val projectProfile = ProjectInspectionProfileManager.getInstance(project).projectProfile
        val key = HighlightDisplayKey.find(REPLACE_UNTIL_WITH_RANGE_UNTIL_INSPECTION_NAME)
        if (projectProfile != null && key != null) {
            InspectionProfileManager.getInstance(project).getProfile(projectProfile)
                .setErrorLevel(key, HighlightDisplayLevel.WEAK_WARNING, project)
        }
    }

    override fun setupRootModel(rootModel: ModifiableRootModel) {
        super.setupRootModel(rootModel)
        setupKotlinLibrary(rootModel)
        applyKotlinCompilerArguments(rootModel)
        applyKotlinCodeStyle(rootModel)
        if (isCreatingNewProject) {
            enableNewProjectInspections(rootModel.project)
        }
    }
}