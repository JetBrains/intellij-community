// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProgressPortionReporter(
    indicator: ProgressIndicator,
    private val start: Double,
    private val portion: Double
) : J2KDelegatingProgressIndicator(indicator) {

    init {
        fraction = 0.0
    }

    override fun start() {
        fraction = 0.0
    }

    override fun stop() {
        fraction = portion
    }

    override fun setFraction(fraction: Double) {
        super.setFraction(start + (fraction * portion))
    }

    override fun getFraction(): Double {
        return (super.getFraction() - start) / portion
    }

    override fun setText(text: String?) {
    }

    override fun setText2(text: String?) {
    }
}