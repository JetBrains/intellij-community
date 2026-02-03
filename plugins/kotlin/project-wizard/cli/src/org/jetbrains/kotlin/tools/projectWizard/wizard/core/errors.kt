// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard.core

import org.jetbrains.kotlin.tools.projectWizard.core.ExceptionError
import org.yaml.snakeyaml.parser.ParserException

data class YamlParsingError(override val exception: ParserException) : ExceptionError()