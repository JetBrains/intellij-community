package org.jetbrains.idea.maven.project.actions

import com.intellij.lang.xml.XMLLanguage
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenEffectivePomEvaluator
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenCoroutineScopeProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil
import java.io.IOException

class MavenShowEffectivePom : AnAction(), DumbAware {
  override fun actionPerformed(event: AnActionEvent) {
    val project = MavenActionUtil.getProject(event.dataContext) ?: return
    val file = findPomXml(event.dataContext) ?: return
    if (!MavenServerManager.getInstance().isUseMaven2) {
      val cs = MavenCoroutineScopeProvider.getCoroutineScope(project)
      cs.launch { actionPerformed(project, file) }
    }
  }

  override fun update(e: AnActionEvent) {
    val p = e.presentation
    var visible = findPomXml(e.dataContext) != null
    visible = visible && !MavenServerManager.getInstance().isUseMaven2
    p.isVisible = visible
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private val LOG = Logger.getInstance(MavenShowEffectivePom::class.java)

    suspend fun actionPerformed(project: Project, file: VirtualFile) {
      val manager = MavenProjectsManager.getInstance(project)
      val mavenProject = manager.findProject(file)!!

      val s = MavenEffectivePomEvaluator.evaluateEffectivePom(project, mavenProject)

      withContext(Dispatchers.EDT) {
        if (project.isDisposed) return@withContext
        if (s == null) { // null means UnsupportedOperationException
          Notification(MavenUtil.MAVEN_NOTIFICATION_GROUP,
                       MavenProjectBundle.message("maven.effective.pom.failed.title"),
                       MavenProjectBundle.message("maven.effective.pom.failed"),
                       NotificationType.ERROR).notify(project)
          return@withContext
        }
        val fileName = mavenProject.mavenId.artifactId + "-effective-pom.xml"
        val file1 = PsiFileFactory.getInstance(project).createFileFromText(fileName, XMLLanguage.INSTANCE, s)
        try {
          file1.virtualFile.isWritable = false
        }
        catch (e: IOException) {
          LOG.error(e)
        }
        file1.navigate(true)
      }
    }

    private fun findPomXml(dataContext: DataContext): VirtualFile? {
      val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return null
      if (file.isDirectory) {
        val project = MavenActionUtil.getProject(dataContext) ?: return null
        val files = file.children.filter { MavenUtil.isPomFile(project, it) }
        if (files.isEmpty()) return null
        return files[0]
      }
      val manager = MavenActionUtil.getProjectsManager(dataContext) ?: return null
      manager.findProject(file) ?: return null
      return file
    }
  }
}
