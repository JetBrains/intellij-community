// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@VisibleForTesting
enum class RequirementsProviderType {
  REQUIREMENTS_TXT, SETUP_PY, ENVIRONMENT_YML
}