// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.configuration.*
import org.jetbrains.kotlin.idea.framework.JavaRuntimeDetectionUtil
import org.jetbrains.kotlin.idea.framework.isExternalLibrary
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

fun updateLibraries(project: Project, libraries: Collection<Library>) {
    if (project.allModules().any { module -> module.getBuildSystemType() != BuildSystemType.JPS }) {
        Messages.showMessageDialog(
            project,
            KotlinJvmBundle.message("automatic.library.version.update.for.maven.and.gradle.projects.is.currently.unsupported.please.update.your.build.scripts.manually"),
            KotlinJvmBundle.message("update.kotlin.runtime.library"),
            Messages.getErrorIcon()
        )
        return
    }

    val kJvmConfigurator =
        getConfiguratorByName(KotlinJavaModuleConfigurator.NAME) as KotlinJavaModuleConfigurator?
            ?: error("Configurator with given name doesn't exists: " + KotlinJavaModuleConfigurator.NAME)

    val kJsConfigurator =
        getConfiguratorByName(KotlinJsModuleConfigurator.NAME) as KotlinJsModuleConfigurator?
            ?: error("Configurator with given name doesn't exists: " + KotlinJsModuleConfigurator.NAME)

    val collector = createConfigureKotlinNotificationCollector(project)
    val sdk = ProjectRootManager.getInstance(project).projectSdk
    // TODO use module SDK

    for (library in libraries) {
        val libraryJarDescriptors = if (JavaRuntimeDetectionUtil.getJavaRuntimeVersion(library) != null)
            kJvmConfigurator.getLibraryJarDescriptors(sdk)
        else
            kJsConfigurator.getLibraryJarDescriptors(sdk)

        for (libraryJarDescriptor in libraryJarDescriptors) {
            updateJar(project, library, libraryJarDescriptor)
        }
    }

    collector.showNotification()
}

private fun updateJar(
    project: Project,
    library: Library,
    libraryJarDescriptor: LibraryJarDescriptor
) {
    val fileToReplace = libraryJarDescriptor.findExistingJar(library)
    if (fileToReplace == null) {
        if (libraryJarDescriptor.shouldExist) {
            error(
                "Update for library was requested, but file for replacement isn't present: \n" +
                        "name = ${library.name}\n" +
                        "isExternal = `${isExternalLibrary(library)}`\n" +
                        "entries = ${library.getUrls(libraryJarDescriptor.orderRootType)}\n" +
                        "buildSystems = ${project.allModules().map { module -> module.getBuildSystemType() }.distinct()}"
            )
        }

        return
    }

    val oldUrl = fileToReplace.url
    val jarPath: Path = libraryJarDescriptor.getPathInPlugin()
    if (jarPath.notExists()) {
        showRuntimeJarNotFoundDialog(project, libraryJarDescriptor.jarName)
        return
    }

    val jarFileToReplace = VfsUtil.getLocalFile(fileToReplace)
    val newVFile = try {
        replaceFile(jarPath, jarFileToReplace)
    } catch (e: IOException) {
        Messages.showErrorDialog(
            project,
            KotlinJvmBundle.message("failed.to.update.0.1", jarPath, e.message.toString()),
            KotlinJvmBundle.message("library.update.failed")
        )
        return
    }

    if (newVFile != null) {
        val model = library.modifiableModel
        runWriteAction {
            try {
                model.removeRoot(oldUrl, libraryJarDescriptor.orderRootType)

                val newRoot = JarFileSystem.getInstance().getJarRootForLocalFile(newVFile) ?: run {
                    Messages.showErrorDialog(
                        project,
                        KotlinJvmBundle.message("failed.to.find.root.for.file.0", newVFile.canonicalPath.toString()),
                        KotlinJvmBundle.message("library.update.failed1")
                    )
                    return@runWriteAction
                }

                model.addRoot(newRoot, libraryJarDescriptor.orderRootType)
            } finally {
                model.commit()
            }
        }
    }
}

internal fun replaceFile(updatedFile: Path, jarFileToReplace: VirtualFile): VirtualFile? {
    val jarNioFileToReplace = VfsUtil.getLocalFile(jarFileToReplace).toNioPath()
    if (updatedFile == jarNioFileToReplace) {
        return null
    }

    updatedFile.copyTo(jarNioFileToReplace, overwrite = true)
    if (jarNioFileToReplace.name != updatedFile.name) {
        val newFile = jarNioFileToReplace.resolveSibling(updatedFile.name)
        if (newFile.notExists()) {
            try {
                jarNioFileToReplace.moveTo(newFile, overwrite = true)
            } catch (e: Throwable) {
                if (e is ControlFlowException) throw e
                LOG.info("Failed to rename ${jarNioFileToReplace.pathString} to ${newFile.pathString}")
                return null
            }

            val newVFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newFile)
            if (newVFile == null) {
                LOG.info("Failed to find ${newFile.pathString} in VFS")
                return null
            }

            newVFile.refresh(false, true)
            return newVFile
        }
    }

    jarFileToReplace.refresh(false, true)
    return null
}