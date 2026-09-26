// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.venvReader

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * The subset of [PRUNED_SCAN_DIRS] with no dot at the start of the name.
 *
 * The `pyproject.toml` walk skips every name that starts with a dot. It therefore needs only these names.
 */
@ApiStatus.Internal
val PRUNED_SCAN_DIRS_NO_DOT: Set<@NonNls String> = setOf(
  "node_modules", "__pycache__", "site-packages",
)

/**
 * The well-known heavy or irrelevant directory names. A scan of a project tree never descends into them.
 *
 * None of them holds a user-selectable virtualenv. None of them holds a `pyproject.toml` that must become a module.
 * A check of the name costs no syscall.
 *
 * In a large monorepo, `node_modules` and `site-packages` hold most of the directories.
 * A recursive walk would `stat` and enumerate all of them (PY-91826).
 */
@ApiStatus.Internal
val PRUNED_SCAN_DIRS: Set<@NonNls String> = setOf(
  ".git", ".hg", ".svn", ".idea",
  ".mypy_cache", ".pytest_cache", ".ruff_cache",
) + PRUNED_SCAN_DIRS_NO_DOT
