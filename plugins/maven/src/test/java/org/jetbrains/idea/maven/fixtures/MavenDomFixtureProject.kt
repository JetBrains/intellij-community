// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.project.MavenSettingsCache
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

// Project-model authoring: creating/updating poms, profiles, sub-files and settings.

fun MavenTestFixture.createProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  return createPomFile(projectRoot, xml).also { projectPom = it }
}

fun MavenTestFixture.updateProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createProjectPom(xml)
  refreshFiles(listOf(pom))
  return pom
}

fun MavenTestFixture.createModulePom(relativePath: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  return createPomFile(createProjectSubDir(relativePath), xml)
}

fun MavenTestFixture.updateModulePom(relativePath: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createModulePom(relativePath, xml)
  refreshFiles(listOf(pom))
  return pom
}

fun MavenTestFixture.createPomFile(dir: VirtualFile, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val filePath = Path.of(dir.path, "pom.xml")
  Files.writeString(filePath, MavenTestCase.createPomXml(modelVersion, xml, false))
  dir.refresh(false, false)
  val f = dir.findChild("pom.xml") ?: throw AssertionError("can't find pom.xml ${filePath.absolutePathString()} in VFS")
  refreshFiles(listOf(f))
  return f
}

fun MavenTestFixture.createProjectSubDir(relativePath: String): VirtualFile {
  val f = Path.of(project.basePath!!).resolve(relativePath)
  Files.createDirectories(f)
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)!!
}

fun MavenTestFixture.createProjectSubFile(relativePath: String, content: String = ""): VirtualFile {
  val f = Path.of(project.basePath!!).resolve(relativePath)
  Files.createDirectories(f.parent)
  Files.writeString(f, content)
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(f)!!
}

fun MavenTestFixture.createProfilesXml(@Language(value = "XML", prefix = "<profiles>", suffix = "</profiles>") xml: String): VirtualFile {
  val content = "<?xml version=\"1.0\"?><profilesXml><profiles>$xml</profiles></profilesXml>"
  val filePath = Path.of(projectRoot.path, "profiles.xml")
  Files.writeString(filePath, content)
  projectRoot.refresh(false, false)
  val f = projectRoot.findChild("profiles.xml") ?: throw AssertionError("can't find profiles.xml in VFS")
  refreshFiles(listOf(f))
  return f
}

fun MavenTestFixture.updateProjectSubFile(relativePath: String, content: String): VirtualFile {
  val nioPath = Path.of(project.basePath!!).resolve(relativePath)
  val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath)!!
  Files.writeString(nioPath, content)
  refreshFiles(listOf(file))
  return file
}

fun MavenTestFixture.setPomContent(file: VirtualFile, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
  Files.writeString(file.toNioPath(), MavenTestCase.createPomXml(modelVersion, xml, false))
  refreshFiles(listOf(file))
}

suspend fun MavenImportingTestFixture.updateSettingsXml(@Language(value = "XML", prefix = "<settings>", suffix = "</settings>") content: String): VirtualFile {
  val ioFile = dir.resolve("settings.xml")
  ioFile.findOrCreateFile()
  VfsRootAccess.allowRootAccess(disposable, ioFile.toString())
  Files.writeString(ioFile, "<settings>$content</settings>\r\n")
  val f = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile)!!
  refreshFiles(listOf(f))
  projectsManager.generalSettings.setUserSettingsFile(f.path)
  MavenSettingsCache.getInstance(project).reloadAsync()
  return f
}

fun MavenTestFixture.refreshFiles(files: List<VirtualFile>) {
  LocalFileSystem.getInstance().refreshFiles(files)
}

fun MavenTestFixture.assertModules(vararg expectedNames: String) {
  val actualNames = project.modules.map { it.name }
  assertSameElements(actualNames, *expectedNames)
}

fun MavenTestFixture.mn(parent: String, moduleName: String): String = moduleName

fun MavenTestFixture.configConfirmationForYesAnswer() {
  TestDialogManager.setTestDialog { Messages.YES }
}
