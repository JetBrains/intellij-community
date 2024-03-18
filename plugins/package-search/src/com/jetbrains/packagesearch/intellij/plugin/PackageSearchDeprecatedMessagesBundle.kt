package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.packageSearchDeprecatedMessagesBundle"

object PackageSearchDeprecatedMessagesBundle {
    private val bundle = DynamicBundle(PackageSearchDeprecatedMessagesBundle::class.java, BUNDLE)
    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String = bundle.getMessage(key, *params)
}