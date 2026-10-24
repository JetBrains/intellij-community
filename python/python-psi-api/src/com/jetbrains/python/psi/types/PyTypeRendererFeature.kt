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
     * Render fully qualified names of all classes, type forms and functions, e.g. `typing.Callable[[mod.MyClass], typing.Any]`
     * or `def mod.func() -> builtins.int`.
     */
    USE_FQN,

    /**
     * Render bounds and constraints of TypeVars.
     */
    TYPE_VAR_BOUNDS,

    /**
     * Render literal types bare, as just their value: `1` instead of `Literal[1]`, `'a' | 'b'` instead of
     * `Literal['a', 'b']`. Used where the `Literal[...]` wrapper is redundant noise, such as the structural type diff.
     * A multi-line string shows its first line, a `…` and its closing quote, so that the value stays on one line.
     */
    BARE_LITERAL,

    /**
     * Render an instance of a callback protocol, a protocol whose only member is `__call__`, as the signature of its
     * `__call__`: `(a: int) -> str` instead of `Handler`. Used where the structure matters more than the name, such as
     * the structural type diff.
     */
    @ApiStatus.Internal
    CALLBACK_PROTOCOL_AS_CALLABLE,
}
