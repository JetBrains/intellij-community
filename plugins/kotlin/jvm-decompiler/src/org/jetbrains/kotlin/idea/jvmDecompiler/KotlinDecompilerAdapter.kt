// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.jvmDecompiler

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.highlighter.isKotlinDecompiledFile
import org.jetbrains.kotlin.idea.jvm.shared.internal.DecompileFailedException
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
class KotlinBytecodeDecompilerTask(private val file: KtFile) : Task.Backgroundable(
    file.project,
    KotlinJvmDecompilerBundle.message("internal.action.text.decompile.kotlin.bytecode")
) {
    private var decompiledText: String? = null

    override fun run(indicator: ProgressIndicator) {
        indicator.text = KotlinJvmDecompilerBundle.message("internal.indicator.text.decompiling", file.name)

        decompiledText = try {
            KotlinBytecodeDecompiler.decompile(file)
        } catch (e: DecompileFailedException) {
            null
        }
    }

    override fun onSuccess() {
        if (!file.isValid) return

        val text = decompiledText
        if (text != null) {
            val decompiledVirtualFile = generateDecompiledVirtualFile(text)
            OpenFileDescriptor(file.project, decompiledVirtualFile).navigate(true)
        } else {
            Messages.showErrorDialog(
                KotlinJvmDecompilerBundle.message("internal.error.text.cannot.decompile", file.name),
                KotlinJvmDecompilerBundle.message("internal.title.decompiler.error")
            )
        }
    }

    fun generateDecompiledVirtualFile(decompiledText: String): VirtualFile {
        val virtualFile: VirtualFile = runWriteAction {
            val decompiledFileName = FileUtil.getNameWithoutExtension(file.name) + ".decompiled.java"
            val result = object: LightVirtualFile(decompiledFileName, JavaFileType.INSTANCE, decompiledText) {
                override fun shouldSkipEventSystem(): Boolean {
                    return true
                }
            }
            result
        }

        virtualFile.isKotlinDecompiledFile = true
        return virtualFile
    }
}
