// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties

class KotlinPropertyBreakpointProperties(
    @Attribute var myFieldName: String = "",
    @Attribute var myClassName: String = ""
) : JavaBreakpointProperties<KotlinPropertyBreakpointProperties>() {
    var watchModification: Boolean = true
    var watchAccess: Boolean = false
    var watchInitialization: Boolean = false

    override fun getState() = this

    override fun loadState(state: KotlinPropertyBreakpointProperties) {
        super.loadState(state)

        watchModification = state.watchModification
        watchAccess = state.watchAccess
        watchInitialization = state.watchInitialization

        myFieldName = state.myFieldName
        myClassName = state.myClassName
    }
}
