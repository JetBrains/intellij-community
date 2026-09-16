// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.codeInsight.pathBeforeJavaToKotlinConversion
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.idea.statistics.J2KFusCollector
import org.jetbrains.kotlin.j2k.externalCodeProcessing.ExternalUsagesFixer
import org.jetbrains.kotlin.psi.KtFile
import java.io.IOException
import kotlin.time.measureTimedValue

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class JavaToKotlinService(val project: Project) {
    suspend fun convert(
        files: List<PsiJavaFile>,
        module: Module,
        settings: ConverterSettings = ConverterSettings.defaultSettings,
        updateExternalUsages: Boolean = true,
        conversionType: ConversionType = ConversionType.FILES,
        progress: RawProgressReporter? = null,
    ): J2kConversionResult {
        if (files.isEmpty()) return J2kConversionResult(emptyList(), emptyList())

        return withCommandOnEdt(project) {
            writeConversion(convertInMemory(files, module, settings, updateExternalUsages, conversionType, progress))
        }
    }

    /** Converts on copies, so the project is untouched until [writeConversion]. */
    private suspend fun convertInMemory(
        files: List<PsiJavaFile>,
        module: Module,
        settings: ConverterSettings,
        updateExternalUsages: Boolean,
        conversionType: ConversionType,
        progress: RawProgressReporter?,
    ): InMemoryConversion {
        val (result, conversionTime) = measureTimedValue {
            JavaToKotlinConverter(project = project, targetModule = module, settings = settings)
                .filesToKotlin(files = files, reporter = progress)
        }
        J2KFusCollector.log(conversionType, conversionTime.inWholeMilliseconds, result.javaLines, files.size, updateExternalUsages)

        val usages =
            if (updateExternalUsages) collectExternalUsages(result.externalCodeProcessing, progress) else emptyList()
        // The converter drops a file it could not translate instead of reporting it.
        val notTranslated = readAction {
            files.filterNot { it in result.kotlinCodeByJavaFile }.map { J2kFailedFile(it.virtualFile, J2kFailureReason.NO_KOTLIN_PRODUCED) }
        }
        return InMemoryConversion(result.kotlinCodeByJavaFile, result.externalCodeProcessing, usages, notTranslated)
    }

    private suspend fun writeConversion(conversion: InMemoryConversion): J2kConversionResult {
        val (toWrite, failedToPrepare) = prepareResultsToSave(conversion.kotlinCodeByJavaFile)
        val (converted, failedToWrite) = edtWriteAction { writeFiles(toWrite) }
        val convertedVirtualFiles = converted.mapTo(HashSet()) { it.kotlinFile }
        val externalUsageFiles = LinkedHashSet<VirtualFile>()

        val usages = conversion.externalUsages
        val externalCodeProcessing = conversion.externalCodeProcessing
            ?.takeIf { usages.isNotEmpty() && converted.isNotEmpty() }

        if (externalCodeProcessing != null) {
            smartReadAction(project) {
                val convertedFiles = converted.mapNotNull { it.kotlinFile.toPsiFile(project) as? KtFile }
                convertedFiles.firstOrNull()?.let { contextElement ->
                    analyze(contextElement) { externalCodeProcessing.bindJavaDeclarationsToConvertedKotlinOnes(convertedFiles) }
                }
                ExternalUsagesFixer.populateEffectiveModality(usages)
            }
            externalUsageFiles += edtWriteAction { ExternalUsagesFixer(usages).fix() }
        }

        edtWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        return J2kConversionResult(
            converted = converted,
            failed = conversion.notTranslated + failedToPrepare + failedToWrite,
            // The converted files themselves pick up `@JvmField`/`@JvmStatic` from the same pass; they are already
            // reported as converted, so this list is what changed *besides* them.
            externalUsageFiles = externalUsageFiles.filterNot { file -> file in convertedVirtualFiles },
        )
    }

    private suspend fun collectExternalUsages(
        externalCodeProcessing: ExternalCodeProcessing?,
        progress: RawProgressReporter?,
    ): List<ExternalUsagesFixer.JKMemberInfoWithUsages> {
        if (externalCodeProcessing == null) return emptyList()
        progress?.text(KotlinJ2kBundle.message("progress.searching.usages"))
        progress?.fraction(0.0)

        return smartReadAction(project) {
            externalCodeProcessing.collectUsages { done, total, name ->
                progress?.fraction(done.toDouble() / total.toDouble())
                progress?.details(name)
            }
        }
    }

    private suspend fun prepareResultsToSave(
        kotlinCodeByJavaFile: Map<PsiJavaFile, String>,
    ): Pair<List<PreparedConversionResult>, List<J2kFailedFile>> = readAction {
        val reservedFileNamesByDirectory = mutableMapOf<VirtualFile, MutableSet<String>>()
        val prepared = ArrayList<PreparedConversionResult>()
        val failed = ArrayList<J2kFailedFile>()
        for ((file, text) in kotlinCodeByJavaFile) {
            val virtualFile = file.virtualFile
            val document = PsiDocumentManager.getInstance(project).getDocument(file)
            if (document == null) {
                failed += J2kFailedFile(virtualFile, J2kFailureReason.NO_DOCUMENT)
                continue
            }
            if (!document.isWritable) {
                failed += J2kFailedFile(virtualFile, J2kFailureReason.READ_ONLY)
                continue
            }

            val newFileName =
                if (ScratchRootType.getInstance().containsFile(virtualFile)) null
                else uniqueKotlinFileName(virtualFile, reservedFileNamesByDirectory)
            prepared += PreparedConversionResult(virtualFile, document, text, newFileName)
        }
        prepared to failed
    }

    // rename before replacing the text, so a failed rename leaves a `.java` file holding Java rather than Kotlin
    private fun writeFiles(
        prepared: List<PreparedConversionResult>,
    ): Pair<List<J2kConvertedFile>, List<J2kFailedFile>> {
        val converted = ArrayList<J2kConvertedFile>()
        val failed = ArrayList<J2kFailedFile>()
        for ((virtualFile, document, text, newFileName) in prepared) {
            val javaPath = virtualFile.path
            try {
                if (newFileName == null) {
                    ScratchFileService.getInstance().scratchesMapping.setMapping(virtualFile, KotlinFileType.INSTANCE.language)
                }
                else {
                    virtualFile.putUserData(pathBeforeJavaToKotlinConversion, javaPath)
                    virtualFile.rename(this, newFileName)
                }
            }
            catch (e: IOException) {
                failed += J2kFailedFile(virtualFile, J2kFailureReason.WRITE_FAILED, e.message)
                continue
            }
            document.replaceString(0, document.textLength, text)
            converted += J2kConvertedFile(javaPath, virtualFile)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return converted to failed
    }

    private fun uniqueKotlinFileName(
        javaFile: VirtualFile,
        reservedFileNamesByDirectory: MutableMap<VirtualFile, MutableSet<String>>,
    ): String {
        val parent = javaFile.parent
        val reservedFileNames = reservedFileNamesByDirectory.getOrPut(parent) {
            parent.children.mapTo(mutableSetOf()) { it.name }
        }

        var i = 0
        while (true) {
            val fileName = javaFile.nameWithoutExtension + (if (i > 0) i else "") + ".kt"
            if (reservedFileNames.add(fileName)) return fileName
            i++
        }
    }

    private data class PreparedConversionResult(
        val virtualFile: VirtualFile,
        val document: Document,
        val text: String,
        val newFileName: String?,
    )
}

private class InMemoryConversion(
    val kotlinCodeByJavaFile: Map<PsiJavaFile, String>,
    val externalCodeProcessing: ExternalCodeProcessing?,
    val externalUsages: List<ExternalUsagesFixer.JKMemberInfoWithUsages>,
    /** Input files the converter produced no Kotlin for; reported as failed alongside the write failures. */
    val notTranslated: List<J2kFailedFile>,
)

@ApiStatus.Internal
enum class J2kFailureReason { NO_KOTLIN_PRODUCED, NO_DOCUMENT, READ_ONLY, WRITE_FAILED }

/** [javaPath] is the path the file had before it was renamed in place; [kotlinFile] is that same file, after the rename. */
@ApiStatus.Internal
data class J2kConvertedFile(val javaPath: String, val kotlinFile: VirtualFile)

/** A failure leaves the file untouched, so [javaFile] is still the Java file. [details] is technical text, never translated. */
@ApiStatus.Internal
data class J2kFailedFile(val javaFile: VirtualFile, val reason: J2kFailureReason, @NlsSafe val details: String? = null)

@ApiStatus.Internal
data class J2kConversionResult(
    val converted: List<J2kConvertedFile>,
    val failed: List<J2kFailedFile>,
    /** Files outside [converted] that reference a converted declaration: the list to verify, not the edits that landed. */
    val externalUsageFiles: List<VirtualFile> = emptyList(),
)

