// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.GitSilentFileAdderProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector.trigger
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.io.IOException

open class MavenModuleBuilderHelper(protected val myProjectId: MavenId,
                                    protected val myAggregatorProject: MavenProject?,
                                    private val myParentProject: MavenProject?,
                                    private val myInheritGroupId: Boolean,
                                    private val myInheritVersion: Boolean,
                                    private val myArchetype: MavenArchetype?,
                                    private val myPropertiesToCreateByArtifact: Map<String, String>?,
                                    protected val myCommandName: @NlsContexts.Command String?) {
  open fun configure(project: Project, root: VirtualFile, isInteractive: Boolean) {
    trigger(project, MavenActionsUsagesCollector.CREATE_MAVEN_PROJECT)

    val psiFiles = if (myAggregatorProject != null
    ) arrayOf(getPsiFile(project, myAggregatorProject.file))
    else PsiFile.EMPTY_ARRAY
    val pom = WriteCommandAction.writeCommandAction(project, *psiFiles).withName(myCommandName).compute<VirtualFile?, RuntimeException> {
      val vcsFileAdder = GitSilentFileAdderProvider.create(project)
      var file: VirtualFile? = null
      try {
        try {
          file = root.findChild(MavenConstants.POM_XML)
          file?.delete(this)
          file = root.createChildData(this, MavenConstants.POM_XML)
          vcsFileAdder.markFileForAdding(file)
          MavenUtil.runOrApplyMavenProjectFileTemplate(project, file, myProjectId, isInteractive)
        }
        catch (e: IOException) {
          showError(project, e)
          return@compute file
        }

        updateProjectPom(project, file)
      }
      finally {
        vcsFileAdder.finish()
      }

      if (myAggregatorProject != null) {
        setPomPackagingForAggregatorProject(project, file)
      }
      file
    }

    if (pom == null) return

    if (myAggregatorProject == null) {
      val manager = MavenProjectsManager.getInstance(project)
      manager.addManagedFilesOrUnignoreNoUpdate(listOf(pom))
    }

    if (myArchetype == null) {
      try {
        VfsUtil.createDirectories(root.path + "/src/main/java")
        VfsUtil.createDirectories(root.path + "/src/main/resources")
        VfsUtil.createDirectories(root.path + "/src/test/java")
      }
      catch (e: IOException) {
        MavenLog.LOG.info(e)
      }
    }

    MavenLog.LOG.info("${this.javaClass.simpleName} forceUpdateAllProjectsOrFindAllAvailablePomFiles")
    MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()

    val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
    cs.launch {
      // execute when current dialog is closed (e.g. Project Structure)
      withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        if (!pom.isValid) {
          showError(project, RuntimeException("Project is not valid"))
          return@withContext
        }
        writeIntentReadAction {
          EditorHelper.openInEditor(getPsiFile(project, pom)!!)
        }
        if (myArchetype != null) generateFromArchetype(project, pom)
      }
    }
  }

  protected fun setPomPackagingForAggregatorProject(project: Project, file: VirtualFile?) {
    val aggregatorProjectFile = myAggregatorProject!!.file
    val model = MavenDomUtil.getMavenDomProjectModel(project, aggregatorProjectFile)
    if (model != null) {
      model.packaging.stringValue = "pom"
      val module = model.modules.addModule()
      module.value = getPsiFile(project, file)
      unblockAndSaveDocuments(project, aggregatorProjectFile)
    }
  }

  protected fun updateProjectPom(project: Project, pom: VirtualFile?) {
    if (myParentProject == null) return

    WriteCommandAction.writeCommandAction(project).withName(myCommandName).run<RuntimeException> {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      val model = MavenDomUtil.getMavenDomProjectModel(project, pom!!)
      if (model == null) return@run

      MavenDomUtil.updateMavenParent(model, myParentProject)

      if (myInheritGroupId) {
        val el = model.groupId.xmlElement
        el?.delete()
      }
      if (myInheritVersion) {
        val el = model.version.xmlElement
        el?.delete()
      }

      CodeStyleManager.getInstance(project).reformat(getPsiFile(project, pom)!!)

      val pomFiles: MutableList<VirtualFile> = ArrayList(2)
      pomFiles.add(pom)

      if (!FileUtil.namesEqual(MavenConstants.POM_XML, myParentProject.file.name)) {
        pomFiles.add(myParentProject.file)
        //MavenProjectsManager.getInstance(project).scheduleForceUpdateMavenProject(myParentProject)
      }
      unblockAndSaveDocuments(project, *pomFiles.toTypedArray())
    }
  }

  private suspend fun generateFromArchetype(project: Project, pom: VirtualFile) {
    trigger(project, MavenActionsUsagesCollector.CREATE_MAVEN_PROJECT_FROM_ARCHETYPE)

    val workingDir: File
    try {
      workingDir = FileUtil.createTempDirectory("archetype", "tmp")
      workingDir.deleteOnExit()
    }
    catch (e: IOException) {
      showError(project, e)
      return
    }

    val params = MavenRunnerParameters(
      false, workingDir.path, null as String?,
      listOf("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"),
      emptyList())

    val runner = MavenRunner.getInstance(project)
    val settings = runner.state.clone()

    val props = settings.mavenProperties
    props["interactiveMode"] = "false"
    if (null != myPropertiesToCreateByArtifact) {
      props.putAll(myPropertiesToCreateByArtifact)
    }

    withContext(Dispatchers.Default) {
      runner.run(params, settings) { copyGeneratedFiles(workingDir, pom, project, props["artifactId"]) }
    }
  }

  @VisibleForTesting
  fun copyGeneratedFiles(workingDir: File?, pom: VirtualFile, project: Project, artifactId: String?) {
    var artifactId = artifactId
    val vcsFileAdder = GitSilentFileAdderProvider.create(project)
    try {
      try {
        artifactId = artifactId ?: myProjectId.artifactId
        if (artifactId != null) {
          val sourceDir = File(workingDir, artifactId)
          val targetDir = File(pom.parent.path)
          vcsFileAdder.markFileForAdding(targetDir, true) // VFS is refreshed below
          FileUtil.copyDir(sourceDir, targetDir)
        }
        FileUtil.delete(workingDir!!)
      }
      catch (e: Exception) {
        showError(project, e)
        return
      }

      pom.parent.refresh(false, false)
      pom.refresh(false, false)
      updateProjectPom(project, pom)

      LocalFileSystem.getInstance().refreshWithoutFileWatcher(true)
    }
    finally {
      vcsFileAdder.finish()
    }
  }

  companion object {
    private fun unblockAndSaveDocuments(project: Project, vararg files: VirtualFile) {
      val fileDocumentManager = FileDocumentManager.getInstance()
      val psiDocumentManager = PsiDocumentManager.getInstance(project)
      for (file in files) {
        val document = fileDocumentManager.getDocument(file)
        if (document == null) continue
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
        fileDocumentManager.saveDocument(document)
      }
    }

    @JvmStatic
    protected fun getPsiFile(project: Project?, pom: VirtualFile?): PsiFile? {
      return PsiManager.getInstance(project!!).findFile(pom!!)
    }

    @JvmStatic
    protected fun showError(project: Project?, e: Throwable?) {
      MavenUtil.showError(project, MavenProjectBundle.message("notification.title.failed.to.create.maven.project"), e)
    }
  }
}
