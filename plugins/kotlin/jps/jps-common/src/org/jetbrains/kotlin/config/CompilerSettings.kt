// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.config

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.cli.common.arguments.Freezable

class CompilerSettings : Freezable() {
    var additionalArguments: String by FreezableVar(DEFAULT_ADDITIONAL_ARGUMENTS)
    var scriptTemplates: String by FreezableVar("")
    var scriptTemplatesClasspath: String by FreezableVar("")
    var copyJsLibraryFiles: Boolean by FreezableVar(true)
    var outputDirectoryForJsLibraryFiles: String by FreezableVar(DEFAULT_OUTPUT_DIRECTORY)

    companion object {
        val DEFAULT_ADDITIONAL_ARGUMENTS = "-version"
        private val DEFAULT_OUTPUT_DIRECTORY = "lib"
    }
}

val CompilerSettings.additionalArgumentsAsList: List<String>
    get() = splitArgumentString(additionalArguments)

fun splitArgumentString(arguments: String) = StringUtil.splitHonorQuotes(arguments, ' ').map {
    if (it.startsWith('"')) StringUtil.unescapeChar(StringUtil.unquoteString(it), '"') else it
}