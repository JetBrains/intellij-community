// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.kotlin.internal.firKotlinUastPlugin

internal object FirKotlinConverter : BaseKotlinConverter {

    override val languagePlugin: UastLanguagePlugin
        get() = firKotlinUastPlugin

    private var forceUInjectionHost = Registry.`is`("kotlin.fir.uast.force.uinjectionhost", true)
        @TestOnly
        set(value) {
            field = value
        }

    override fun forceUInjectionHost(): Boolean {
        return forceUInjectionHost
    }

}
