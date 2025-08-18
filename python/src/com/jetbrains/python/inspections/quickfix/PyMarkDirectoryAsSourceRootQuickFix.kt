// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext
import com.jetbrains.python.psi.resolve.findCache
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File
import javax.swing.Icon

internal class PyMarkDirectoryAsSourceRootQuickFix(
  private val project: Project,
  private val sourceRoot: VirtualFile,
  private val module: Module,
  private val context: PyQualifiedNameResolveContext,
) : LocalQuickFix, Iconable {
  override fun startInWriteAction(): Boolean = true

  override fun getFamilyName(): @IntentionFamilyName String {
    return PyPsiBundle.message("QFIX.add.source.root.for.unresolved.import.family.name")
  }

  override fun getName(): @IntentionName String {
    return PyPsiBundle.message("QFIX.add.source.root.for.unresolved.import.name", getPathName())
  }

  private fun getPathName(): @NlsSafe String {
    val projectDir = project.guessProjectDir() ?: return sourceRoot.name
    val relativePath = VfsUtilCore.getRelativePath(sourceRoot, projectDir, File.separatorChar)
    return relativePath?.replace("\\", "/") ?: sourceRoot.name
  }

  override fun getIcon(flags: Int): Icon {
    return AllIcons.Modules.SourceRoot
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    sourceRoot.markAsSourceRoot(module)
    findCache(context)?.clearCache()
    showNotification()
  }

  private fun showNotification() {
    val message = PyPsiBundle.message("QFIX.add.source.root.notification.text", getPathName())
    val group = NotificationGroupManager.getInstance().getNotificationGroup("Python source root detection")
    val notification = group.createNotification(message, NotificationType.INFORMATION)

    // Ok action just closes the notification
    notification.addAction(NotificationAction.createSimpleExpiring(
      PyPsiBundle.message("QFIX.add.source.root.notification.ok")
    ) { /* no-op, expiring */ })

    // Revert action removes the just added source root
    notification.addAction(object : NotificationAction(PyPsiBundle.message("QFIX.add.source.root.notification.revert")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        ApplicationManager.getApplication().runWriteAction {
          sourceRoot.unmarkAsSourceRoot(module)
        }
        findCache(context)?.clearCache()
        notification.expire()
      }
    })

    notification.notify(project)
  }

  private fun VirtualFile.markAsSourceRoot(module: Module) {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val entry = MarkRootsManager.findContentEntry(model, this) ?: return
    entry.addSourceFolder(this, JavaSourceRootType.SOURCE)
    model.commit()
  }

  private fun VirtualFile.unmarkAsSourceRoot(module: Module) {
    val model = ModuleRootManager.getInstance(module).modifiableModel
    val entry = MarkRootsManager.findContentEntry(model, this) ?: return
    val toRemove = entry.sourceFolders.firstOrNull { it.file == this || it.url == this.url }
    if (toRemove != null) {
      entry.removeSourceFolder(toRemove)
    }
    model.commit()
  }
}
