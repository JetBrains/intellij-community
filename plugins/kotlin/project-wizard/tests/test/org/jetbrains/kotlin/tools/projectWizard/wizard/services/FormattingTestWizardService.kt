// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.services

import org.jetbrains.kotlin.tools.projectWizard.cli.TestWizardService
import org.jetbrains.kotlin.tools.projectWizard.core.service.FileFormattingService

class FormattingTestWizardService : FileFormattingService, TestWizardService {
    override fun formatFile(text: String, filename: String): String = text
}