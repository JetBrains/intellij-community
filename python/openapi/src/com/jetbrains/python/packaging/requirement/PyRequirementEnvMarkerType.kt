// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirement

enum class PyRequirementEnvMarkerType() {
  OS_NAME,
  SYS_PLATFORM,
  PLATFORM_MACHINE,
  PLATFORM_PYTHON_IMPLEMENTATION,
  PLATFORM_RELEASE,
  PLATFORM_SYSTEM,
  PLATFORM_VERSION,
  PYTHON_VERSION,
  PYTHON_FULL_VERSION,
  IMPLEMENTATION_VERSION,
  EXTRA
}