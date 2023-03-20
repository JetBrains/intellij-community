// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import java.nio.file.Path

interface AndroidSdkProvider {
    companion object {
        val EP_NAME = ExtensionPointName<AndroidSdkProvider>("org.jetbrains.kotlin.idea.androidSdkProvider")
    }

    fun getSdkPath(): Path?
}