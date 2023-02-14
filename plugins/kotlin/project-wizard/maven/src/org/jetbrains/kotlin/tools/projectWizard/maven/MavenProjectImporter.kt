// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import java.nio.file.Path

internal class MavenProjectImporter(private val project: Project) {

    fun importProject(path: Path) {
        if (KotlinPlatformUtils.isAndroidStudio) {
            return // AS does not support Maven
        }
        val mavenProjectManager = MavenProjectsManager.getInstance(project)

        val rootFile = LocalFileSystem.getInstance().findFileByPath(path.toString())!!
        mavenProjectManager.addManagedFilesOrUnignore(rootFile.findAllPomFiles())
    }

    private fun VirtualFile.findAllPomFiles(): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()

        fun VirtualFile.find() {
            when {
                !isDirectory && name == "pom.xml" -> result += this
                isDirectory -> children.forEach(VirtualFile::find)
            }
        }

        find()
        return result
    }
}