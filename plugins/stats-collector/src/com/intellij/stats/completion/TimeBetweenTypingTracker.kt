package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.project.Project
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage
import java.util.concurrent.TimeUnit

/**
 * @author Vitaliy.Bibaev
 */
class TimeBetweenTypingTracker(private val project: Project) : PrefixChangeListener {
    private companion object {
        val MAX_ALLOWED_DELAY = TimeUnit.SECONDS.toMillis(10)
    }

    private var lastTypingTime: Long = -1L

    override fun beforeAppend(c: Char) = prefixChanged()
    override fun beforeTruncate() = prefixChanged()

    private fun prefixChanged() {
        if (lastTypingTime == -1L) {
            lastTypingTime = System.currentTimeMillis()
            return
        }

        val currentTime = System.currentTimeMillis()
        val delay = currentTime - lastTypingTime
        if (delay > MAX_ALLOWED_DELAY) return
        UserFactorStorage.applyOnBoth(project, UserFactorDescriptions.TIME_BETWEEN_TYPING) { updater ->
            updater.fireTypingPerformed(delay.toInt())
        }

        lastTypingTime = currentTime
    }
}