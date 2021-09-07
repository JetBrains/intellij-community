// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.uast.UastLanguagePlugin

@ApiStatus.Internal
object KotlinConverter : BaseKotlinConverter {override val languagePlugin: UastLanguagePlugin
        get() = kotlinUastPlugin
    var forceUInjectionHost = Registry.`is`("kotlin.uast.force.uinjectionhost", false)
        @TestOnly
        set(value) {
            field = value
        }

    override fun forceUInjectionHost(): Boolean {
        return forceUInjectionHost
    }

}
