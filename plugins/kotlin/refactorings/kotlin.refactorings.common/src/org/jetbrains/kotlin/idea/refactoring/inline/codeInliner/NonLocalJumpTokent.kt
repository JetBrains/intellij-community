// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline.codeInliner

/**
 * Token to mark non-local `break` and `continue` inside lambda arguments of inline functions.
 * The jump and the containing loop will have the same token in their user data.
 */
class NonLocalJumpToken
