// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.ui.Messages
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPathsProvider
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.util.projectStructure.allModules

fun updateLibraries(project: Project, upToMavenVersion: String, libraries: Collection<Library>) {
    if (project.allModules().any { module -> module.getBuildSystemType() != BuildSystemType.JPS }) {
        Messages.showMessageDialog(
            project,
            KotlinJvmBundle.message("automatic.library.version.update.for.maven.and.gradle.projects.is.currently.unsupported.please.update.your.build.scripts.manually"),
            KotlinJvmBundle.message("update.kotlin.runtime.library"),
            Messages.getErrorIcon()
        )
        return
    }

    libraries.asSequence()
        .mapNotNull {
            val lib = it as? LibraryEx ?: return@mapNotNull null
            val prop = lib.properties as? RepositoryLibraryProperties ?: return@mapNotNull null
            lib to prop
        }
        .filter { (_, prop) -> prop.groupId == KotlinPathsProvider.KOTLIN_MAVEN_GROUP_ID }
        .forEach { (lib, prop) ->
            val modifiableModel = lib.modifiableModel
            try {
                modifiableModel.properties = RepositoryLibraryProperties(prop.groupId, prop.mavenId, upToMavenVersion)
                for (orderRootType in listOf(OrderRootType.SOURCES, OrderRootType.CLASSES, OrderRootType.DOCUMENTATION)) {
                    modifiableModel.getUrls(orderRootType).forEach {
                        modifiableModel.removeRoot(it, orderRootType)
                    }
                }

                JarRepositoryManager.loadDependenciesModal(project, prop, true, true, null, null).forEach {
                    modifiableModel.addRoot(it.file, it.type)
                }
            } finally {
                modifiableModel.commit()
            }
        }
}
