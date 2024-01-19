// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ConverterSettings(
    var forceNotNullTypes: Boolean,
    var specifyLocalVariableTypeByDefault: Boolean,
    var specifyFieldTypeByDefault: Boolean,
    var openByDefault: Boolean,
    var publicByDefault: Boolean
) {

    companion object {
        val defaultSettings: ConverterSettings = ConverterSettings(
            forceNotNullTypes = true,
            specifyLocalVariableTypeByDefault = false,
            specifyFieldTypeByDefault = false,
            openByDefault = false,
            publicByDefault = false
        )

        val publicByDefault: ConverterSettings = defaultSettings.copy(publicByDefault = true)
    }
}