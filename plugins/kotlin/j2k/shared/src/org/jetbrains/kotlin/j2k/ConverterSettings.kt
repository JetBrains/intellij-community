// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

data class ConverterSettings(
    var forceNotNullTypes: Boolean,
    var specifyLocalVariableTypeByDefault: Boolean,
    var specifyFieldTypeByDefault: Boolean,
    var openByDefault: Boolean,
    var publicByDefault: Boolean,
    // TODO KTIJ-29063
    // In the basic mode, only essential conversions/processings are performed
    var basicMode: Boolean,
) {

    companion object {
        val defaultSettings: ConverterSettings = ConverterSettings(
            forceNotNullTypes = true,
            specifyLocalVariableTypeByDefault = false,
            specifyFieldTypeByDefault = false,
            openByDefault = false,
            publicByDefault = false,
            basicMode = false,
        )

        val publicByDefault: ConverterSettings = defaultSettings.copy(publicByDefault = true)
    }
}