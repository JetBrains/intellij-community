package com.intellij.python.processOutput.frontend

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

internal object ProcessOutputBundle {
    private const val BUNDLE_FQN: @NonNls String = "messages.ProcessOutputBundle"
    private val BUNDLE = DynamicBundle(ProcessOutputBundle::class.java, BUNDLE_FQN)

    fun message(
        key: @PropertyKey(resourceBundle = BUNDLE_FQN) String,
        vararg params: Any,
    ): @Nls String {
        return BUNDLE.getMessage(key, *params)
    }
}
