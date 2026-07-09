// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import org.jetbrains.annotations.ApiStatus

/**
 * Optional features that tweak how a [PyType] is rendered into a human-readable string.
 * 
 * @see com.jetbrains.python.documentation.PythonDocumentationProvider.getTypeName
 */
@ApiStatus.Experimental
enum class PyTypeRendererFeature {
    /**
     * Render fully qualified names of all classes and type forms, e.g. `typing.Callable[[mod.MyClass], typing.Any]`.
     */
    USE_FQN,

    /**
     * Render internal "unsafe" unions as `UnsafeUnion[...]`, otherwise render them as regular union types.
     */
    UNSAFE_UNION,

    /**
     * Render bounds and constraints of TypeVars.
     */
    TYPE_VAR_BOUNDS,
}
