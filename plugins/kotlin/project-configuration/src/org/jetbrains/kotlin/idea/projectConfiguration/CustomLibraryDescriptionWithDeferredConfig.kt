// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectConfiguration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryKind
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.config.apiVersionView
import org.jetbrains.kotlin.config.languageVersionView
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.KotlinWithLibraryConfigurator
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.getConfiguratorByName
import javax.swing.JComponent

/**
 * @param project null when project doesn't exist yet (called from project wizard)
 */
abstract class CustomLibraryDescriptionWithDeferredConfig(
    val project: Project?,
    private val configuratorName: String,
    private val libraryName: String,
    private val libraryKind: LibraryKind,
    private val suitableLibraryKinds: Set<LibraryKind>
) : CustomLibraryDescription() {

    override fun getSuitableLibraryKinds(): Set<LibraryKind> {
        return suitableLibraryKinds
    }

    fun finishLibConfiguration(
        module: Module,
        rootModel: ModifiableRootModel,
        isNewProject: Boolean = true
    ) {
        configureKotlinSettings(module.project, rootModel.sdk)

        if (isNewProject) {
            KotlinCommonCompilerArgumentsHolder.getInstance(module.project).update {
                val bundledLanguageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion
                languageVersionView = VersionView.Specific(bundledLanguageVersion)
                apiVersionView = VersionView.Specific(bundledLanguageVersion)
            }

            val library = rootModel.orderEntries().findLibrary { library ->
                val libraryPresentationManager = LibraryPresentationManager.getInstance()
                val classFiles = library.getFiles(OrderRootType.CLASSES).toList()

                libraryPresentationManager.isLibraryOfKind(classFiles, libraryKind)
            } as? LibraryEx ?: return

            val model = library.modifiableModel
            try {
                val collector = NotificationMessageCollector.create(module.project)

                // Now that we know the SDK which is going to be set for the module, we can add jre 7/8 if required
                val descriptorWithSdk = configurator.libraryJarDescriptor
                if (descriptorWithSdk.findExistingJar(library) != null) {
                    configurator.configureLibraryJar(module.project, model, descriptorWithSdk, collector) // Updates KotlinJavaRuntime.xml
                }

                collector.showNotification()
            } finally {
                model.commit()
            }
        }
    }

    protected open fun configureKotlinSettings(project: Project, sdk: Sdk?) {
    }

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?): NewLibraryConfiguration? {
        return createConfigurationFromPluginPaths()
    }

    private val configurator: KotlinWithLibraryConfigurator<*>
        get() = getConfiguratorByName(configuratorName) as KotlinWithLibraryConfigurator<*>?
            ?: error("Configurator with name $configuratorName should exists")

    override fun createNewLibraryWithDefaultSettings(contextDirectory: VirtualFile?): NewLibraryConfiguration? {
        return createConfigurationFromPluginPaths()
    }

    protected fun createConfigurationFromPluginPaths(): NewLibraryConfiguration {
        return MyLibraryConfiguration(project ?: ProjectManager.getInstance().defaultProject, libraryName, configurator)
    }

    private class MyLibraryConfiguration<P : LibraryProperties<*>>(
        val project: Project,
        libraryName: String,
        private val configurator: KotlinWithLibraryConfigurator<P>
    ) : NewLibraryConfiguration(libraryName, configurator.libraryType, configurator.libraryProperties) {
        override fun addRoots(editor: LibraryEditor) {
            val repositoryLibraryProperties = configurator.libraryJarDescriptor.repositoryLibraryProperties
            JarRepositoryManager.loadDependenciesModal(project, repositoryLibraryProperties, true, true, null, null).forEach {
                editor.addRoot(it.file, it.type)
            }
        }
    }
}
