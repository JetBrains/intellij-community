// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.k1.scratch.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.kotlin.idea.jvm.k1.scratch.K1KotlinScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.actions.ScratchAction

class RunScratchFromHereAction : ScratchAction(
    KotlinJvmBundle.messagePointer("scratch.run.from.here.button"),
    AllIcons.Diff.ArrowRight
) {

    override fun actionPerformed(e: AnActionEvent) {
        val scratchFile = e.currentScratchFile as? K1KotlinScratchFile ?: return

        Handler.doAction(scratchFile)
    }

    object Handler {
        fun doAction(scratchFile: K1KotlinScratchFile) {
            val executor = scratchFile.replScratchExecutor ?: return

            try {
                executor.executeNew()
            } catch (ex: Throwable) {
                executor.errorOccurs(KotlinJvmBundle.message("exception.occurred.during.run.scratch.action1"), ex, true)
            }
        }
    }
}