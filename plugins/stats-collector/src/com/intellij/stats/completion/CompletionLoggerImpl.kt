package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import java.util.*

class CompletionFileLoggerProvider(private val logFileManager: LogFileManager) : CompletionLoggerProvider() {
    override fun dispose() {
        logFileManager.dispose()
    }

    private fun String.shortedUUID(): String {
        val start = this.lastIndexOf('-')
        if (start > 0 && start + 1 < this.length) {
            return this.substring(start + 1)
        }
        return this
    }

    override fun newCompletionLogger(): CompletionLogger {
        val installationUID = UpdateChecker.getInstallationUID(PropertiesComponent.getInstance())
        val completionUID = UUID.randomUUID().toString()
        return CompletionFileLogger(installationUID.shortedUUID(), completionUID.shortedUUID(), logFileManager)
    }
}


class CompletionFileLogger(private val installationUID: String,
                           private val completionUID: String,
                           private val logFileManager: LogFileManager) : CompletionLogger() {


    override fun completionStarted(lookup: LookupImpl, isExperimentPerformed: Boolean, experimentVersion: Int) {
    }


    override fun customMessage(message: String) {
    }


    override fun afterCharTyped(c: Char, lookup: LookupImpl) {
    }


    override fun downPressed(lookup: LookupImpl) {
    }


    override fun upPressed(lookup: LookupImpl) {
    }


    override fun completionCancelled() {
    }


    override fun itemSelectedByTyping(lookup: LookupImpl) {
    }


    override fun itemSelectedCompletionFinished(lookup: LookupImpl) {
    }


    override fun afterBackspacePressed(lookup: LookupImpl) {
    }

}

enum class Action {
    COMPLETION_STARTED,
    TYPE,
    BACKSPACE,
    UP,
    DOWN,
    COMPLETION_CANCELED,
    EXPLICIT_SELECT,
    TYPED_SELECT,
    CUSTOM
}

class LogLineBuilder(val installationUID: String, val completionUID: String, val action: Action) {
    private val timestamp = System.currentTimeMillis()
    private val builder = StringBuilder()

    init {
        builder.append("$installationUID $completionUID $timestamp $action")
    }

    fun addText(any: Any) = builder.append(" $any")

    fun addPair(name: String, value: Any) = builder.append(" $name=$value")

    fun text() = builder.toString()

}