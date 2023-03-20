package com.intellij.performance.performancePlugin.commands

import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AbstractCommand
import com.intellij.openapi.util.ActionCallback
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromise
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions

class KotlinEditorOptionsChangeCommand(text: String, line: Int) : AbstractCommand(text, line) {

    companion object {
        const val PREFIX: @NonNls String = CMD_PREFIX + "changeKotlinEditorOptions"
    }

    override fun _execute(context: PlaybackContext): Promise<Any?> {
        val actionCallback: ActionCallback = ActionCallbackProfilerStopper()
        val parameter = extractCommandArgument(PREFIX)
        val split = parameter.split(" ")
        if (split.size != 2) {
            throw IllegalStateException("Provide parameters: $split")
        }
        when(split[0].lowercase()) {
            "dontshowconversiondialog" -> KotlinEditorOptions.getInstance().isDonTShowConversionDialog = split[1].toBoolean()
        }
        actionCallback.setDone()
        return actionCallback.toPromise()
    }
}