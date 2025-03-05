// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.gradle

import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace

internal fun MutableTWorkspace.generateK2GradleTests() {
    generateK2GradleCodeInsightTests()
}