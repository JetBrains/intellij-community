// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.projectModel

import java.io.Serializable

@Deprecated("Use org.jetbrains.kotlin.idea.projectModel.CachedArgsInfo instead", level = DeprecationLevel.ERROR)
interface KotlinCompilationArguments : Serializable {
    val defaultArguments: Array<String>
    val currentArguments: Array<String>
}