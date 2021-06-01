// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import org.jetbrains.kotlin.idea.j2k.IdeaJavaToKotlinServices
import org.jetbrains.kotlin.j2k.JavaToKotlinConverterServices


interface NewJavaToKotlinServices {
    val oldServices: JavaToKotlinConverterServices
}

object IdeaNewJavaToKotlinServices : NewJavaToKotlinServices {
    override val oldServices: JavaToKotlinConverterServices
        get() = IdeaJavaToKotlinServices
}