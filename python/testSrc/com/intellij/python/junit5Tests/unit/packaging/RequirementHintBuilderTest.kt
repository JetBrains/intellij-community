// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.PythonPackageMetadata
import com.jetbrains.python.requirements.RequirementHint
import com.jetbrains.python.requirements.computeRequirementHint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RequirementHintBuilderTest {

  // Matches the internal `defaultLabel` in PythonPackageMetadata.kt — copied
  // rather than imported because the const is module-internal and the test lives in a separate module.
  private val defaultLabel = "Repository"

  private fun pkg(name: String, version: String = "1.0"): PythonPackage =
    PythonPackage(name = name, version = version, isEditableMode = false)

  private fun metadata(
    summary: String = "",
    homePage: String = "",
    projectUrls: Map<String, String> = emptyMap(),
  ): PythonPackageMetadata = PythonPackageMetadata(
    name = "ignored",
    summary = summary,
    homePage = homePage,
    projectUrls = projectUrls,
  )

  @Test
  fun `local module wins over every other input installed and metadata are ignored`() {
    // Rationale: a module inside the workspace is the truth; the pyproject-driven `pip install -e .`
    // dist-info may exist AND have summary/URLs, but we still want to send the user to the folder.
    val hint = computeRequirementHint(
      packageName = "my-lib",
      isLocalModule = true,
      installed = pkg("my-lib"),
      metadata = metadata(summary = "would win otherwise", homePage = "https://example.com/my-lib"),
    )
    assertEquals(RequirementHint.Local("my-lib"), hint)
  }

  @Test
  fun `metadata summary wins over plain installed variant when both present`() {
    val hint = computeRequirementHint(
      packageName = "requests",
      isLocalModule = false,
      installed = pkg("requests", version = "2.32.3"),
      metadata = metadata(summary = "Python HTTP for Humans."),
    )
    assertEquals(
      RequirementHint.InstalledWithMetadata("requests", "Python HTTP for Humans.", defaultLabel),
      hint,
    )
  }

  @Test
  fun `blank metadata summary demotes to installedWithVersion (empty is not a real summary)`() {
    val hint = computeRequirementHint(
      packageName = "requests",
      isLocalModule = false,
      installed = pkg("requests", version = "2.32.3"),
      metadata = metadata(summary = "   "),
    )
    assertEquals(
      RequirementHint.InstalledWithVersion("requests", "2.32.3", defaultLabel),
      hint,
    )
  }

  @Test
  fun `installed without metadata falls into installedWithVersion`() {
    val hint = computeRequirementHint(
      packageName = "urllib3",
      isLocalModule = false,
      installed = pkg("urllib3", version = "2.1.0"),
      metadata = null,
    )
    assertEquals(
      RequirementHint.InstalledWithVersion("urllib3", "2.1.0", defaultLabel),
      hint,
    )
  }

  @Test
  fun `not-installed name still gets a hint with the default destination label`() {
    val hint = computeRequirementHint(
      packageName = "nowhere-lib",
      isLocalModule = false,
      installed = null,
      metadata = null,
    )
    assertEquals(RequirementHint.NotInstalled("nowhere-lib", defaultLabel), hint)
  }

  @Test
  fun `metadata preferred project URL label overrides the default destination`() {
    // preferredProjectUrl picks the first project URL, so the label in the produced hint should
    // match whichever URL entry the map exposes.
    val hint = computeRequirementHint(
      packageName = "django",
      isLocalModule = false,
      installed = pkg("django", version = "5.0"),
      metadata = metadata(summary = "The web framework", projectUrls = linkedMapOf("Homepage" to "https://www.djangoproject.com/")),
    )
    val installedWithMetadata = hint as RequirementHint.InstalledWithMetadata
    assertEquals("Homepage", installedWithMetadata.destinationLabel)
  }
}
