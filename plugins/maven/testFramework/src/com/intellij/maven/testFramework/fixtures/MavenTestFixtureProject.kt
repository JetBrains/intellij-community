// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.maven.testFramework.fixtures

import com.intellij.ide.DataManager
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.project.MavenSettingsCache
import org.jetbrains.idea.maven.utils.MavenLog
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.absolutePathString

// Project-model authoring: creating/updating poms, profiles, sub-files and settings.

val MavenTestFixture.projectRoot: VirtualFile
  get() = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(project.basePath!!))!!

fun MavenImportingTestFixture.createProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  return createPomFile(projectRoot, xml).also { projectPom = it }
}

/**
 * Writes [content] verbatim to the project-root `pom.xml` and makes it the project pom. Unlike [createProjectPom], it
 * does NOT wrap the content via [MavenTestCase.createPomXml], so the test keeps full control over the file (e.g. a
 * custom `<?xml ...?>` declaration, explicit `xmlns`, or model version).
 */
fun MavenImportingTestFixture.setRawPomFile(content: String) {
  val filePath = Path.of(projectRoot.path, "pom.xml")
  Files.writeString(filePath, content)
  projectRoot.refresh(false, false)
  val f = projectRoot.findChild("pom.xml") ?: throw AssertionError("can't find pom.xml in VFS")
  projectPom = f
  refreshFiles(listOf(f))
}

fun MavenImportingTestFixture.updateProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createProjectPom(xml)
  refreshFiles(listOf(pom))
  return pom
}

fun MavenImportingTestFixture.createModulePom(relativePath: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  return createPomFile(createProjectSubDir(relativePath), xml)
}

fun MavenImportingTestFixture.updateModulePom(relativePath: String, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
  val pom = createModulePom(relativePath, xml)
  refreshFiles(listOf(pom))
  return pom
}

fun MavenImportingTestFixture.createPomFile(dir: VirtualFile, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): VirtualFile {
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

// Ported from MavenTestCase.
fun MavenTestFixture.createFile(path: Path): VirtualFile {
  Files.createDirectories(path.parent)
  if (!Files.exists(path)) Files.createFile(path)
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
}

fun MavenTestFixture.createFile(path: Path, content: String): VirtualFile {
  val file = createFile(path)
  Files.writeString(path, content)
  refreshFiles(listOf(file))
  return file
}

fun MavenImportingTestFixture.createProfilesXml(@Language(value = "XML", prefix = "<profiles>", suffix = "</profiles>") xml: String): VirtualFile {
  val content = "<?xml version=\"1.0\"?><profilesXml><profiles>$xml</profiles></profilesXml>"
  val filePath = Path.of(projectRoot.path, "profiles.xml")
  Files.writeString(filePath, content)
  projectRoot.refresh(false, false)
  val f = projectRoot.findChild("profiles.xml") ?: throw AssertionError("can't find profiles.xml in VFS")
  refreshFiles(listOf(f))
  return f
}

fun MavenTestFixture.createProfilesXml(relativePath: String, xml: String): VirtualFile {
  return createProfilesFile(createProjectSubDir(relativePath), xml, false)
}

fun MavenImportingTestFixture.createProfilesXmlOldStyle(@Language(value = "XML", prefix = "<profiles>", suffix = "</profiles>") xml: String): VirtualFile {
  return createProfilesFile(projectRoot, xml, true)
}

private fun MavenTestFixture.createProfilesFile(dir: VirtualFile, xml: String, oldStyle: Boolean): VirtualFile {
  return createProfilesFile(dir, createValidProfiles(xml, oldStyle))
}

private fun MavenTestFixture.createProfilesFile(dir: VirtualFile, content: String): VirtualFile {
  val fileName = "profiles.xml"
  val filePath = Path.of(dir.path, fileName)
  setFileContent(filePath, content)
  var f = dir.findChild(fileName)
  if (null == f) {
    refreshFiles(listOf(dir))
    f = dir.findChild(fileName)!!
  }
  refreshFiles(listOf(f))
  return f
}

private fun MavenTestFixture.setFileContent(file: Path, content: String) {
  val relativePath = dir.relativize(file)
  MavenLog.LOG.debug("Writing content to $relativePath")
  Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
}

@Language("XML")
private fun createValidProfiles(@Language("XML") xml: String, oldStyle: Boolean): String {
  if (oldStyle) {
    return "<?xml version=\"1.0\"?>" +
           "<profiles>" +
           xml +
           "</profiles>"
  }
  return "<?xml version=\"1.0\"?>" +
         "<profilesXml>" +
         "<profiles>" +
         xml +
         "</profiles>" +
         "</profilesXml>"
}

fun MavenTestFixture.updateProjectSubFile(relativePath: String, content: String): VirtualFile {
  val nioPath = Path.of(project.basePath!!).resolve(relativePath)
  val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath)!!
  Files.writeString(nioPath, content)
  refreshFiles(listOf(file))
  return file
}

fun MavenImportingTestFixture.setPomContent(file: VirtualFile, @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String) {
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

fun MavenTestFixture.configConfirmationForYesAnswer(): AtomicInteger {
  val counter = AtomicInteger()
  TestDialogManager.setTestDialog {
    counter.getAndIncrement()
    Messages.YES
  }
  return counter
}

fun MavenTestFixture.configConfirmationForNoAnswer(): AtomicInteger {
  val counter = AtomicInteger()
  TestDialogManager.setTestDialog {
    counter.getAndIncrement()
    Messages.NO
  }
  return counter
}

fun MavenImportingTestFixture.createPomXml(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String): String {
  return MavenTestCase.createPomXml(modelVersion, xml, false)
}

val MavenTestFixture.projectPath: Path
  get() = Path.of(project.basePath!!)

fun MavenTestFixture.createModule(name: String): Module {
  return WriteCommandAction.writeCommandAction(project).compute<Module, RuntimeException> {
    val file = createProjectSubFile("$name/$name.iml")
    val module = ModuleManager.getInstance(project).newModule(file.path, JavaModuleType.getModuleType().id)
    PsiTestUtil.addContentRoot(module, file.parent!!)
    module
  }
}

fun MavenImportingTestFixture.createSettingsXml(@Language(value = "XML", prefix = "<settings>", suffix = "</settings>") innerContent: String): VirtualFile {
  val content = "<settings>$innerContent</settings>\r\n"
  val path = dir.resolve("settings.xml")
  Files.writeString(path, content)
  projectsManager.generalSettings.setUserSettingsFile(path.toString())
  return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
}

/** Builds a [DataContext] for an action under test, carrying the project and the pom as the selected file. */
fun MavenImportingTestFixture.createTestDataContext(pomFile: VirtualFile): DataContext {
  val defaultContext = DataManager.getInstance().getDataContext()
  return CustomizedDataContext.withSnapshot(defaultContext) { sink ->
    sink[CommonDataKeys.PROJECT] = project
    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(pomFile)
  }
}

fun <R, E : Throwable?> MavenTestFixture.runWriteAction(computable: ThrowableComputable<R, E>): R {
  return WriteCommandAction.writeCommandAction(project).compute(computable)
}

fun <E : Throwable?> MavenTestFixture.runWriteAction(runnable: ThrowableRunnable<E>) {
  WriteCommandAction.writeCommandAction(project).run(runnable)
}

fun MavenImportingTestFixture.createProjectPom(
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  omitModelVersionTag: Boolean,
): VirtualFile {
  return createPomFile(projectRoot, xml, omitModelVersionTag).also { projectPom = it }
}

fun MavenImportingTestFixture.createModulePom(
  relativePath: String,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  omitModelVersionTag: Boolean,
): VirtualFile {
  return createPomFile(createProjectSubDir(relativePath), xml, omitModelVersionTag)
}

fun MavenImportingTestFixture.updateModulePom(
  relativePath: String,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  omitModelVersionTag: Boolean,
): VirtualFile {
  val pom = createModulePom(relativePath, xml, omitModelVersionTag)
  refreshFiles(listOf(pom))
  return pom
}

fun MavenImportingTestFixture.createPomFile(
  dir: VirtualFile,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  omitModelVersionTag: Boolean,
): VirtualFile {
  val filePath = Path.of(dir.path, "pom.xml")
  Files.writeString(filePath, MavenTestCase.createPomXml(modelVersion, xml, omitModelVersionTag))
  dir.refresh(false, false)
  val f = dir.findChild("pom.xml") ?: throw AssertionError("can't find pom.xml ${filePath.absolutePathString()} in VFS")
  refreshFiles(listOf(f))
  return f
}

fun MavenImportingTestFixture.createPomFile(
  dir: VirtualFile,
  fileName: String,
  @Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String,
  omitModelVersionTag: Boolean = false,
): VirtualFile {
  val filePath = Path.of(dir.path, fileName)
  Files.writeString(filePath, MavenTestCase.createPomXml(modelVersion, xml, omitModelVersionTag))
  dir.refresh(false, false)
  val f = dir.findChild(fileName) ?: throw AssertionError("can't find $fileName ${filePath.absolutePathString()} in VFS")
  refreshFiles(listOf(f))
  return f
}

fun MavenImportingTestFixture.pathFromBasedir(relPath: String): String = pathFromBasedir(projectRoot, relPath)

fun MavenTestFixture.pathFromBasedir(root: VirtualFile?, relPath: String): String =
  FileUtil.toSystemIndependentName(root!!.path + "/" + relPath)
