// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Holds the qualified class names of inspections that a visitor filter must recognize
 * when the filter cannot depend on the module of the inspection.
 */
@ApiStatus.Internal
object PyInspectionClassNames {
  const val PACKAGE_REQUIREMENTS: @NonNls String = "com.jetbrains.python.inspections.PyPackageRequirementsInspection"

  const val UNRESOLVED_REFERENCES: @NonNls String =
    "com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection"
}
