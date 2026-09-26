// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.jetbrains.python.packaging.common.DEFAULT_PROJECT_URL_LABEL
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.packaging.common.preferredProjectUrl

/**
 * Pure representation of the four hint variants shown for a requirement on Ctrl-hover.
 * Kept side-effect free so the branch logic can be unit-tested without an SDK / project fixture;
 * [RequirementDocumentationTarget] does the I/O (snapshot lookups, module scan) and delegates
 * the decision to [computeRequirementHint].
 */
internal sealed interface RequirementHint {
  val packageName: String

  data class Local(override val packageName: String) : RequirementHint
  data class InstalledWithMetadata(
    override val packageName: String,
    val summary: String,
    val destinationLabel: String,
  ) : RequirementHint
  data class InstalledWithVersion(
    override val packageName: String,
    val version: String,
    val destinationLabel: String,
  ) : RequirementHint
  data class NotInstalled(
    override val packageName: String,
    val destinationLabel: String,
  ) : RequirementHint
}

/**
 * Picks the hint variant from already-materialised snapshot data.
 * Precedence (matches the pre-extraction inline `when`):
 * `isLocalModule` → METADATA with non-blank summary → installed without summary → not installed.
 * `destinationLabel` prefers the METADATA-provided project URL label so callers see the same
 * upstream branding they'd click through to.
 */
internal fun computeRequirementHint(
  packageName: String,
  isLocalModule: Boolean,
  installed: PythonPackage?,
  metadata: PythonPackageMetadata?,
): RequirementHint {
  val destinationLabel = metadata?.preferredProjectUrl()?.label ?: DEFAULT_PROJECT_URL_LABEL
  val metadataSummary = metadata?.summary?.takeIf { it.isNotBlank() }
  return when {
    isLocalModule -> RequirementHint.Local(packageName)
    metadataSummary != null -> RequirementHint.InstalledWithMetadata(packageName, metadataSummary, destinationLabel)
    installed != null -> RequirementHint.InstalledWithVersion(packageName, installed.version, destinationLabel)
    else -> RequirementHint.NotInstalled(packageName, destinationLabel)
  }
}
