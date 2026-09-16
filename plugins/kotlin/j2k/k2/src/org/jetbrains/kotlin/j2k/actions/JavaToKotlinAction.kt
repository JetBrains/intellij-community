// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k.actions

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ex.MessagesEx
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getAllFilesRecursively
import org.jetbrains.kotlin.idea.util.isJavaFileType
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2KKotlinConfigurationService
import org.jetbrains.kotlin.j2k.J2kFailedFile
import org.jetbrains.kotlin.j2k.J2kFailureReason
import org.jetbrains.kotlin.j2k.JavaToKotlinService
import org.jetbrains.kotlin.j2k.KotlinJ2kBundle
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.util.concurrent.atomic.AtomicReference

class JavaToKotlinActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.setEnabledAndVisible(childrenCount > 1 && isBuiltInActionEnabled(e))
        e.presentation.isDisableGroupIfEmpty = true
        e.presentation.isPopupGroup = true
    }
}

class JavaToKotlinActionForGroup : JavaToKotlinAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isBuiltInActionEnabled(e)
    }
}

@ApiStatus.Internal
@VisibleForTesting
var j2kJob: AtomicReference<Job>? = null

open class JavaToKotlinAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = CommonDataKeys.PROJECT.getData(e.dataContext) ?: return
        val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
        val javaFiles = getSelectedWritableJavaFiles(e)
        if (javaFiles.isEmpty()) {
            showNothingToConvertErrorMessage(project)
            return
        }
        val configurationService = project.service<J2KKotlinConfigurationService>()
        if (shouldSkipConversionOfErroneousCode(javaFiles, project)) return
        if (configurationService.kotlinIsConfigured(module)) {
            val launch = e.coroutineScope.launch {
                convertFilesInteractively(
                    files = javaFiles,
                    project = project,
                    module = module,
                    askExternalCodeProcessing = true
                )
            }
            j2kJob?.set(launch)
        } else {
            configurationService.setUpAndConvert(module, javaFiles) { files, project, module ->
                val launch = e.coroutineScope.launch {
                    convertFilesInteractively(
                        files = files,
                        project = project,
                        module = module,
                        askExternalCodeProcessing = !isUnitTestMode()
                    )
                }
                j2kJob?.set(launch)
            }
        }
    }

    private fun getSelectedWritableJavaFiles(e: AnActionEvent): List<PsiJavaFile> {
        val virtualFilesAndDirectories = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return emptyList()
        val project = e.project ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        return getAllFilesRecursively(virtualFilesAndDirectories).mapNotNull { findWritableJavaFile(it, psiManager) }
    }

    private fun showNothingToConvertErrorMessage(project: Project) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(KotlinBundle.message("action.j2k.error.nothing.to.convert"), MessageType.ERROR, null)
            .createBalloon()
            .showInCenterOf(statusBar.component)
    }

    private fun shouldSkipConversionOfErroneousCode(javaFiles: List<PsiJavaFile>, project: Project): Boolean {
        val firstSyntaxError = javaFiles.asSequence().map { it.findDescendantOfType<PsiErrorElement>() }.firstOrNull() ?: return false
        val count = javaFiles.count { PsiTreeUtil.hasErrorElements(it) }
        assert(count > 0)
        val firstFileName = firstSyntaxError.containingFile.name
        val question = when (count) {
            1 -> KotlinBundle.message("action.j2k.correction.errors.single", firstFileName)
            else -> KotlinBundle.message("action.j2k.correction.errors.multiple", firstFileName, count - 1)
        }
        val okText = KotlinBundle.message("action.j2k.correction.investigate")
        val cancelText = KotlinBundle.message("action.j2k.correction.proceed")

        return if (Messages.showOkCancelDialog(
                project,
                question,
                KotlinBundle.message("action.j2k.name"),
                okText,
                cancelText,
                Messages.getWarningIcon()
            ) == Messages.OK
        ) {
            activateFileWithPsiElement(firstSyntaxError.navigationElement)
            true
        } else {
            false
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction("ConvertJavaToKotlinGroup") as? DefaultActionGroup

        if (group != null && group.childrenCount > 1) {
            e.presentation.isEnabledAndVisible = false
        } else {
            e.presentation.isEnabledAndVisible = isBuiltInActionEnabled(e)
        }
    }
}

private suspend fun convertFilesInteractively(
    files: List<PsiJavaFile>,
    project: Project,
    module: Module,
    askExternalCodeProcessing: Boolean,
) {
    val updateExternalUsages = !askExternalCodeProcessing || withContext(Dispatchers.EDT) {
        Messages.showYesNoDialog(
            project,
            KotlinBundle.message("action.j2k.correction.required"),
            KotlinBundle.message("action.j2k.name"),
            Messages.getQuestionIcon()
        ) == Messages.YES
    }

    val result = withModalProgress(project, KotlinJ2kBundle.message("j2k.phase.converting")) {
        reportRawProgress { progress ->
            project.service<JavaToKotlinService>().convert(
                files = files,
                module = module,
                settings = ConverterSettings.defaultSettings,
                updateExternalUsages = updateExternalUsages,
                progress = progress,
            )
        }
    }

    for (failure in result.failed) {
        MessagesEx.error(project, failure.presentableMessage()).showLater()
    }
    result.converted.singleOrNull()?.let { converted ->
        withContext(Dispatchers.EDT) {
            FileEditorManager.getInstance(project).openFile(converted.kotlinFile, true)
        }
    }
}

@Nls
private fun J2kFailedFile.presentableMessage(): String {
    val name = javaFile.name
    return when (reason) {
        J2kFailureReason.NO_KOTLIN_PRODUCED -> KotlinBundle.message("action.j2k.error.nothing.converted", name)
        J2kFailureReason.NO_DOCUMENT -> cantSaveResult(KotlinBundle.message("action.j2k.error.cant.find.document", name))
        J2kFailureReason.READ_ONLY -> cantSaveResult(KotlinBundle.message("action.j2k.error.read.only", name))
        J2kFailureReason.WRITE_FAILED -> cantSaveResult(details ?: name)
    }
}

@Nls
private fun cantSaveResult(@Nls reason: String): String =
    KotlinBundle.message("action.j2k.error.cant.save.result", reason)

private const val MAX_SCANNED_FILE_COUNT = 10_000

private fun findWritableJavaFile(file: VirtualFile, psiManager: PsiManager): PsiJavaFile? {
    if (!file.isWritable || !file.isJavaFileType()) return null
    return (psiManager.findFile(file) as? PsiJavaFile)?.takeIf { it.fileType == JavaFileType.INSTANCE } // skip .jsp files
}

private fun isWritablePackageDirectory(file: VirtualFile, project: Project): Boolean {
    val directory = file.toPsiDirectory(project) ?: return false
    return PsiDirectoryFactory.getInstance(project).isPackage(directory) && file.isWritable
}

private fun isBuiltInActionEnabled(e: AnActionEvent): Boolean {
    if (KotlinPlatformUtils.isCidr) return false
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
    val project = e.project ?: return false
    if (project.isDisposed) return false
    if (e.getData(PlatformCoreDataKeys.MODULE) == null) return false

    // Directories are not considered in the project view popup to avoid cluttering it.
    val scanDirectories = e.place != PROJECT_VIEW_POPUP
    val psiManager = PsiManager.getInstance(project)

    val fileIndex = ProjectFileIndex.getInstance(project)
    var remainingFiles = MAX_SCANNED_FILE_COUNT

    for (file in files) {
        if (file.isDirectory) {
            if (!scanDirectories || !isWritablePackageDirectory(file, project)) continue

            var javaFileFound = false
            fileIndex.iterateContentUnderDirectory(file) { child ->
                javaFileFound = findWritableJavaFile(child, psiManager) != null
                !javaFileFound && --remainingFiles > 0
            }

            // Once the limit is reached, stay enabled rather than keep walking: actionPerformed reports the empty result.
            if (javaFileFound || remainingFiles <= 0) return true
        } else if (findWritableJavaFile(file, psiManager) != null) {
            return true
        }
    }

    return false
}
