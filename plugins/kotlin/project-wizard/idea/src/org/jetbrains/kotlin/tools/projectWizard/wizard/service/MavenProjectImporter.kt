// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.PlatformVersion
import java.nio.file.Path

class MavenProjectImporter(private val project: Project) {
    fun importProject(path: Path) {
        if (PlatformVersion.isAndroidStudio()) {
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