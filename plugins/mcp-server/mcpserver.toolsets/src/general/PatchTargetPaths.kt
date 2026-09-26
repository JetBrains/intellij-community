// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.mcpserver.toolsets.general

import org.jetbrains.annotations.ApiStatus

/**
 * Target file paths of a patch, as written in the patch headers (project-relative).
 */
@ApiStatus.Internal
data class PatchTargetPaths(
  val createdOrUpdated: List<String>,
  val deleted: List<String>,
)

/**
 * Extracts the target file paths from a patch in the apply_patch or unified git diff format
 * without applying it. For `*** Move to:` updates the destination path is reported.
 * Fails like the patch application does on malformed patch text.
 */
@ApiStatus.Internal
fun extractPatchTargetPaths(patchText: String): PatchTargetPaths {
  val createdOrUpdated = LinkedHashSet<String>()
  val deleted = LinkedHashSet<String>()
  for (operation in PatchApplyEngine.parsePatch(patchText)) {
    when (operation) {
      is AddPatchOperation -> createdOrUpdated += operation.path
      is UpdatePatchOperation -> createdOrUpdated += operation.moveTo ?: operation.path
      is DeletePatchOperation -> deleted += operation.path
    }
  }
  return PatchTargetPaths(createdOrUpdated = createdOrUpdated.toList(), deleted = deleted.toList())
}
