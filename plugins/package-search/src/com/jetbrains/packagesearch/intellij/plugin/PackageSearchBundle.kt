package com.jetbrains.packagesearch.intellij.plugin

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

private const val BUNDLE_NAME = "messages.packageSearchBundle"

object PackageSearchBundle : DynamicBundle(BUNDLE_NAME) {

    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
        vararg params: Any
    ): String = getMessage(key, *params)

    @Nls
    fun messagePointer(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any): Supplier<String> =
        getLazyMessage(key, *params)
}
