// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.ui

import com.intellij.openapi.util.NlsSafe
import javax.swing.Spring
import javax.swing.SpringLayout

internal operator fun Spring.plus(other: Spring) = Spring.sum(this, other)
internal operator fun Spring.plus(gap: Int) = Spring.sum(this, Spring.constant(gap))
internal operator fun Spring.minus(other: Spring) = this + Spring.minus(other)
internal operator fun Spring.unaryMinus() = Spring.minus(this)
internal operator fun Spring.times(by: Float) = Spring.scale(this, by)
internal fun Int.asSpring() = Spring.constant(this)
internal operator fun SpringLayout.Constraints.get(@NlsSafe edgeName: String) = getConstraint(edgeName)
internal operator fun SpringLayout.Constraints.set(@NlsSafe edgeName: String, spring: Spring) {
    setConstraint(edgeName, spring)
}

fun springMin(s1: Spring, s2: Spring) = -Spring.max(-s1, -s2)


