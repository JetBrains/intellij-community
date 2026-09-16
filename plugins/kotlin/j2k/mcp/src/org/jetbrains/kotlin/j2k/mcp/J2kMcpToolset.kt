// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("FunctionName")

package org.jetbrains.kotlin.j2k.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue.TRUE
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.project
import com.intellij.mcpserver.util.projectDirectory
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.mcpserver.util.resolveInProject
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.Serializable
import java.nio.file.Path
import org.jetbrains.kotlin.idea.statistics.ConversionType
import org.jetbrains.kotlin.j2k.J2KKotlinConfigurationService
import org.jetbrains.kotlin.j2k.J2kFailureReason
import org.jetbrains.kotlin.j2k.JavaToKotlinService

class J2kMcpToolset : McpToolset {
    @McpToolHints(destructiveHint = TRUE)
    @McpTool
    @McpDescription(
        """
      |Converts existing Java file(s) in the project to Kotlin using IntelliJ's built-in Java-to-Kotlin converter.
      |This is a DESTRUCTIVE, in-place operation: each .java file is renamed to .kt, its contents are replaced with
      |the converted Kotlin, and references to the converted declarations from other files in the project are updated
      |where the converter recognizes them. Prefer this over hand-writing Kotlin: it performs real type and
      |nullability inference, fixes imports, and patches cross-file usages. `externalUsageFiles` names the files
      |that referenced the converted code, including the ones it could not patch, so read them before you finish.
      |Requires Kotlin to be configured in the target module.
    """
    )
    suspend fun convert_java_to_kotlin(
        @McpDescription("Project-relative paths to existing .java files to convert. All files must belong to the same module.")
        file_paths: List<String>,
        @McpDescription(
            """
      |When true (default), also rewrites references to the converted declarations from other files in the project
      |and reports every file that held one in `externalUsageFiles`. Set to false to limit the edit to `file_paths`;
      |callers elsewhere in the project may then stop compiling, and none of them are reported.
    """
        )
        fix_external_usages: Boolean = true,
    ): ConvertJavaToKotlinResult {
        if (file_paths.isEmpty()) mcpFail("No file paths provided.")
        val project = currentCoroutineContext().project

        // A synchronous VFS refresh must not run under a read action, see VirtualFile.refresh
        val targets = file_paths.map { path ->
            val nioPath = project.resolveInProject(path)
            val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(nioPath)
                ?: VirtualFileManager.getInstance().refreshAndFindFileByNioPath(nioPath)
                ?: mcpFail("File not found: $path")
            Target(path, virtualFile)
        }

        val (javaFiles, module) = readAction {
            val psiManager = PsiManager.getInstance(project)
            val files = targets.map { target ->
                val psiFile = psiManager.findFile(target.virtualFile)
                if (psiFile !is PsiJavaFile) mcpFail("Not a Java file: ${target.inputPath}")
                if (!target.virtualFile.isWritable) mcpFail("File is not writable: ${target.inputPath}")
                psiFile
            }
            val modules = targets.map { ModuleUtilCore.findModuleForFile(it.virtualFile, project) }
            val first = modules.first() ?: mcpFail("Cannot determine the module for ${targets.first().inputPath}")
            targets.zip(modules).firstOrNull { (_, module) -> module != first }?.let { (target, module) ->
                mcpFail("All files must belong to the same module, but ${targets.first().inputPath} is in '${first.name}' " +
                        "and ${target.inputPath} is in '${module?.name ?: "none"}'.")
            }
            if (!project.service<J2KKotlinConfigurationService>().kotlinIsConfigured(first)) {
                mcpFail(
                    "Kotlin is not configured in module '${first.name}', so the converted code would not compile. " +
                    "Configure Kotlin for the module first, then run the conversion again."
                )
            }
            files to first
        }

        val result = project.service<JavaToKotlinService>().convert(
            files = javaFiles,
            module = module,
            updateExternalUsages = fix_external_usages,
            conversionType = ConversionType.MCP,
        )

        // Every path out of this tool is project-relative, including the Java ones. Echoing the caller's own
        // spelling back instead would pair an absolute `javaPath` with a relative `kotlinPath` in the same object
        // whenever the caller passed an absolute path, which reads as two unrelated files.
        val projectDirectory = project.projectDirectory
        return ConvertJavaToKotlinResult(
            converted = result.converted.map {
                ConvertedFile(
                    javaPath = projectDirectory.relativizeIfPossible(Path.of(it.javaPath)),
                    kotlinPath = projectDirectory.relativizeIfPossible(it.kotlinFile),
                )
            },
            errors = result.failed.map {
                ConversionError(
                    javaPath = projectDirectory.relativizeIfPossible(it.javaFile),
                    message = describe(it.reason, it.details),
                )
            },
            externalUsageFiles = result.externalUsageFiles
                .take(MAX_REPORTED_EXTERNAL_USAGE_FILES)
                .map { projectDirectory.relativizeIfPossible(it) },
        )
    }

    private class Target(val inputPath: String, val virtualFile: VirtualFile)

    private fun describe(reason: J2kFailureReason, details: String?): String = when (reason) {
        J2kFailureReason.NO_KOTLIN_PRODUCED -> "The converter produced no Kotlin code for this file."
        J2kFailureReason.NO_DOCUMENT -> "The file has no loaded document, so the conversion result could not be written."
        J2kFailureReason.READ_ONLY -> "The file is read-only."
        J2kFailureReason.WRITE_FAILED -> "Failed to write the conversion result" + (details?.let { ": $it" } ?: ".")
    }
}

@Serializable
data class ConvertJavaToKotlinResult(
    @property:McpDescription("Per-file mapping from the original Java path to the produced Kotlin path. Only files that were actually converted.")
    val converted: List<ConvertedFile>,
    @property:McpDescription("Files that could not be converted. Absent from `converted`; the remaining files were still applied.")
    val errors: List<ConversionError>,
    @property:McpDescription(
        """
      |Project-relative paths of files outside `converted` that reference a declaration this call renamed or turned
      |into a property. Most were rewritten to match; the ones the converter did not know how to rewrite -- a method
      |reference such as `m::getName` to a getter that is now a property -- are left broken. Read every one and fix
      |what does not compile. At most 50 are listed; if there are exactly 50, assume more exist and verify by
      |building the module rather than file by file.
    """
    )
    val externalUsageFiles: List<String> = emptyList(),
)

private const val MAX_REPORTED_EXTERNAL_USAGE_FILES: Int = 50

@Serializable
data class ConversionError(
    @property:McpDescription("Project-relative path of the file that could not be converted.")
    val javaPath: String,
    @property:McpDescription("Why the conversion could not be applied to this file.")
    val message: String,
)

@Serializable
data class ConvertedFile(
    @property:McpDescription("Project-relative path of the Java file that was converted.")
    val javaPath: String,
    @property:McpDescription("Project-relative path of the produced Kotlin file.")
    val kotlinPath: String,
)
