// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.util.*

/**
 * Analyze maven modules graph and exclude all children from the [selectedModules] so only
 * topmost modules of [selectedModules] will remain.
 *
 * Example:
 *
 *
 * - root
 *   - module1
 *   - module2
 *     - module2.1
 *     - module2.2
 *   - module3
 *
 *
 * so `excludeMavenChildrenModules(project, listOf(module2, module2.2, module1)` -> `listOf(module1, module2)`
 *
 */
fun excludeMavenChildrenModules(project: Project, selectedModules: List<Module>): List<Module> {
    val mavenManager = MavenProjectsManager.getInstance(project)

    val projectsById = mavenManager.projects.associateBy { it.mavenId }
    val selectedProjects = selectedModules.mapNotNull { mavenManager.findProject(it) }
    val selectedIds = selectedProjects.mapTo(HashSet()) { it.mavenId }

    val excluded = HashSet<MavenId>(selectedProjects.size)
    for (m in selectedProjects) {
        if (m.mavenId !in excluded) {
            var current: MavenProject? = m
            while (current != null) {
                if (current.mavenId in excluded || (current != m && current.mavenId in selectedIds)) {
                    excluded.add(m.mavenId)
                    break
                }
                current = current.parentId?.let { projectsById[it] }
            }
        }
    }

    return selectedProjects.filter { it.mavenId !in excluded }.mapNotNull { mavenManager.findModule(it) }
}
