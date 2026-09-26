// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirement

interface PyRequirementEnvMarker {
  fun matches(platformData: Map<PyRequirementEnvMarkerType, String>): Boolean
}