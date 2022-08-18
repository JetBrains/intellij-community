// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.settings

import org.jetbrains.annotations.Nls

interface DisplayableSettingItem {
    @get:Nls
    val text: String

    @get:Nls
    val greyText: String?
        get() = null
}
