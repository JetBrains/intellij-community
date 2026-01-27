// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.actions

import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionPlaces.PROJECT_VIEW_POPUP
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ex.MessagesEx
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.pathBeforeJavaToKotlinConversion
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK2Mode
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.KotlinPlatformUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.commitAndUnblockDocument
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.idea.util.getAllFilesRecursively
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension
import org.jetbrains.kotlin.j2k.J2kPreprocessorExtension
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import java.io.IOException
import kotlin.io.path.notExists
import kotlin.time.measureTimedValue

class JavaToKotlinAction : AnAction() {
    object Handler {
        val title: String = KotlinBundle.message("action.j2k.name")

        suspend fun convertFiles(
            files: List<PsiJavaFile>,
            project: Project,
            module: Module,
            enableExternalCodeProcessing: Boolean = true,
            askExternalCodeProcessing: Boolean = true,
            bodyFilter: ((PsiElement) -> Boolean)? = null,
            settings: ConverterSettings = ConverterSettings.defaultSettings,
            preprocessorExtensions: List<J2kPreprocessorExtension> = J2kPreprocessorExtension.EP_NAME.extensionList,
            postprocessorExtensions: List<J2kPostprocessorExtension> = J2kPostprocessorExtension.EP_NAME.extensionList
        ) {
            val javaFiles = files.filter { it.virtualFile.isWritable }.ifEmpty { return }

            val j2kKind = getJ2kKind()
            val converter = J2kConverterExtension.extension(j2kKind).createJavaToKotlinConverter(project, module, settings)
            val postProcessor = J2kConverterExtension.extension(j2kKind).createPostProcessor()

            val (result, conversionTime) = measureTimedValue {
                converter.filesToKotlin(
                    javaFiles,
                    postProcessor,
                    bodyFilter = bodyFilter,
                    preprocessorExtensions = preprocessorExtensions,
                    postprocessorExtensions = postprocessorExtensions
                )
            }

            // TODO: Support K2 J2K in FUS
            J2KFusCollector.log(
                ConversionType.FILES,
                j2kKind == K1_NEW,
                conversionTime.inWholeMilliseconds,
                result.javaLines,
                javaFiles.size
            )

            val externalCodeProcessing = result.externalCodeProcessing
            val externalCodeUpdate = if (enableExternalCodeProcessing && externalCodeProcessing != null) readAction {
                externalCodeProcessing.prepareWriteOperation(null)
            } else null

            val userConfirmed = !askExternalCodeProcessing || withContext(Dispatchers.EDT) {
                Messages.showYesNoDialog(
                    project,
                    KotlinBundle.message("action.j2k.correction.required"),
                    title,
                    Messages.getQuestionIcon()
                ) == Messages.YES
            }

            withCommandOnEdt(project) {
                val newFiles = edtWriteAction {
                    saveResults(result.kotlinCodeByJavaFile)
                        .map { it.toPsiFile(project) as KtFile }
                        .onEach { it.commitAndUnblockDocument() }
                }

                if (externalCodeProcessing != null) {
                    val contextElement = newFiles.firstOrNull()
                    if (contextElement != null) {
                        readAction {
                            analyze(contextElement) {
                                externalCodeProcessing.bindJavaDeclarationsToConvertedKotlinOnes(newFiles)
                            }
                        }
                    }

                    if (userConfirmed) {
                        edtWriteAction {
                            externalCodeUpdate?.invoke()
                        }
                    }
                }

                edtWriteAction {
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                    newFiles.singleOrNull()?.let {
                        FileEditorManager.getInstance(project).openFile(it.virtualFile, true)
                    }
                }
            }
        }

        private fun saveResults(kotlinCodeByJavaFile: Map<PsiJavaFile, String>): List<VirtualFile> {
            fun uniqueKotlinFileName(javaFile: VirtualFile): String {
                val nioFile = javaFile.fileSystem.getNioPath(javaFile)

                var i = 0
                while (true) {
                    val fileName = javaFile.nameWithoutExtension + (if (i > 0) i else "") + ".kt"
                    if (nioFile == null || nioFile.resolveSibling(fileName).notExists()) return fileName
                    i++
                }
            }

            val result = ArrayList<VirtualFile>()
            for ((psiFile, text) in kotlinCodeByJavaFile) {
                try {
                    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
                    val errorMessage = when {
                        document == null -> KotlinBundle.message("action.j2k.error.cant.find.document", psiFile.name)
                        !document.isWritable -> KotlinBundle.message("action.j2k.error.read.only", psiFile.name)
                        else -> null
                    }
                    if (errorMessage != null) {
                        val message = KotlinBundle.message("action.j2k.error.cant.save.result", errorMessage)
                        MessagesEx.error(psiFile.project, message).showLater()
                        continue
                    }
                    document!!.replaceString(0, document.textLength, text)
                    FileDocumentManager.getInstance().saveDocument(document)

                    val virtualFile = psiFile.virtualFile
                    if (ScratchRootType.getInstance().containsFile(virtualFile)) {
                        val mapping = ScratchFileService.getInstance().scratchesMapping
                        mapping.setMapping(virtualFile, KotlinFileType.INSTANCE.language)
                    } else {
                        val fileName = uniqueKotlinFileName(virtualFile)
                        virtualFile.putUserData(pathBeforeJavaToKotlinConversion, virtualFile.path)
                        virtualFile.rename(this, fileName)
                    }
                    result += virtualFile
                } catch (e: IOException) {
                    MessagesEx.error(psiFile.project, e.message ?: "").showLater()
                }
            }
            return result
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = CommonDataKeys.PROJECT.getData(e.dataContext) ?: return
        val module = e.getData(PlatformCoreDataKeys.MODULE) ?: return
        val javaFiles = getSelectedWritableJavaFiles(e)
        if (javaFiles.isEmpty()) {
            showNothingToConvertErrorMessage(project)
            return
        }
        val j2kKind = getJ2kKind()
        val j2kConverterExtension = J2kConverterExtension.extension(j2kKind)
        if (shouldSkipConversionOfErroneousCode(javaFiles, project)) return
        if (j2kConverterExtension.doCheckBeforeConversion(project, module)) {
            currentThreadCoroutineScope().launch {
                Handler.convertFiles(
                    files = javaFiles,
                    project = project,
                    module = module,
                )
            }
        } else {
            j2kConverterExtension.setUpAndConvert(project, module, javaFiles) { files, project, module ->
                currentThreadCoroutineScope().launch {
                    Handler.convertFiles(
                        files = files,
                        project = project,
                        module = module,
                    )
                }
            }
        }
    }

    private fun getSelectedWritableJavaFiles(e: AnActionEvent): List<PsiJavaFile> {
        val virtualFilesAndDirectories = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return emptyList()
        val project = e.project ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        return getAllFilesRecursively(virtualFilesAndDirectories)
            .asSequence()
            .mapNotNull { psiManager.findFile(it) as? PsiJavaFile }
            .filter { it.fileType == JavaFileType.INSTANCE } // skip .jsp files
            .filter { it.isWritable }
            .toList()
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

        if (Messages.showOkCancelDialog(project, question, Handler.title, okText, cancelText, Messages.getWarningIcon()) == Messages.OK) {
            activateFileWithPsiElement(firstSyntaxError.navigationElement)
            return true
        }

        return false
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = isEnabled(e)
    }

    private fun isEnabled(e: AnActionEvent): Boolean {
        if (KotlinPlatformUtils.isCidr) return false
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return false
        val project = e.project ?: return false
        if (project.isDisposed) return false
        if (e.getData(PlatformCoreDataKeys.MODULE) == null) return false

        fun isWritableJavaFile(file: VirtualFile): Boolean {
            val psiManager = PsiManager.getInstance(project)
            return file.extension == JavaFileType.DEFAULT_EXTENSION && psiManager.findFile(file) is PsiJavaFile && file.isWritable
        }

        fun isWritablePackageDirectory(file: VirtualFile): Boolean {
            val directory = file.toPsiDirectory(project) ?: return false
            return PsiDirectoryFactory.getInstance(project).isPackage(directory) && file.isWritable
        }

        if (e.place != PROJECT_VIEW_POPUP && files.any(::isWritablePackageDirectory)) {
            // If a package is selected, we consider that it may contain Java files,
            // but don't actually check, because this check is recursive and potentially expensive: KTIJ-12688.
            //
            // This logic is disabled for the project view popup to avoid cluttering it.
            return true
        }

        return files.any(::isWritableJavaFile)
    }
}

private fun getJ2kKind(): J2kConverterExtension.Kind = when {
    isK2Mode() -> K2
    else -> K1_NEW
}

suspend inline fun <T> withCommandOnEdt(project: Project, action: () -> T): T {
    val commandProcessor = CommandProcessor.getInstance() as CommandProcessorEx
    val token = withContext(Dispatchers.EDT) {
        writeIntentReadAction {
            commandProcessor.startCommand(
                project,
                KotlinBundle.message("action.j2k.name"),
                null,
                UndoConfirmationPolicy.REQUEST_CONFIRMATION
            )
        }
    }
    var throwable: Throwable? = null
    return try {
        action()
    } catch (e: Throwable) {
        throwable = e
        throw e
    } finally {
        if (token != null) {
            withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                    commandProcessor.finishCommand(token, throwable)
                }
            }
        }
    }
}