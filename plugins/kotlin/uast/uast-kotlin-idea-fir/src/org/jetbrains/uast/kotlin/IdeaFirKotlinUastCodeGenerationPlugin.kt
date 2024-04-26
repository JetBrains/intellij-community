// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.kotlin

import org.jetbrains.uast.UElement
import org.jetbrains.uast.generate.UastCommentSaver
import org.jetbrains.uast.kotlin.generate.createUastCommentSaver


class IdeaFirKotlinUastCodeGenerationPlugin : FirKotlinUastCodeGenerationPlugin(){
    override fun grabComments(firstResultUElement: UElement, lastResultUElement: UElement): UastCommentSaver? {
        return createUastCommentSaver(firstResultUElement, lastResultUElement)
    }
}