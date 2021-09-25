// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import org.jetbrains.uast.UastBinaryExpressionWithTypeKind

object KotlinBinaryExpressionWithTypeKinds {
    init {
        // We trigger class initialization for UastBinaryExpressionWithTypeKind early in order to
        // avoid a class initializer deadlock (https://youtrack.jetbrains.com/issue/KT-32444).
        UastBinaryExpressionWithTypeKind.UNKNOWN
    }

    @JvmField
    val NEGATED_INSTANCE_CHECK = UastBinaryExpressionWithTypeKind.InstanceCheck("!is")

    @JvmField
    val SAFE_TYPE_CAST = UastBinaryExpressionWithTypeKind.TypeCast("as?")
}