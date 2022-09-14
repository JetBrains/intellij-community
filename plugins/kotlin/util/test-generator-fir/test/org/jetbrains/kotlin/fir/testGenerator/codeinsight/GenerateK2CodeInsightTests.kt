// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator.codeinsight

import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace

internal fun MutableTWorkspace.generateK2CodeInsightTests() {
    generateK2InspectionTests()
    generateK2IntentionTests()
    generateK2StructureViewTests()
    generateK2PostfixTemplateTests()
}