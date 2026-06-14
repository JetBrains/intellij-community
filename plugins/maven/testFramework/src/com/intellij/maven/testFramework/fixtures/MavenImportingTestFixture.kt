// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Path

interface MavenTestFixture {
  val project: Project
  val dir: Path
}

interface MavenImportingTestFixture : MavenTestFixture {
  val mavenVersion: String
  val modelVersion: String
  val disposable: Disposable
  val projectsManager: MavenProjectsManager
  var projectPom: VirtualFile
  var repositoryPath: Path
}