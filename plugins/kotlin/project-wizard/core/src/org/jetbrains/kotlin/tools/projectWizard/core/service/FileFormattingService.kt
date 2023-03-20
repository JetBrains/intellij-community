// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.core.service

interface FileFormattingService : WizardService {
    fun formatFile(text: String, filename: String): String
}

class DummyFileFormattingService : FileFormattingService, IdeaIndependentWizardService {
    override fun formatFile(text: String, filename: String): String = text
}