// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
/**
 * Fast wrapper for another element type.
 * These elements can be easily calculated if a type of wrapped element is known.
 */
interface PyWrapperTypeProvider : PyTypedElement