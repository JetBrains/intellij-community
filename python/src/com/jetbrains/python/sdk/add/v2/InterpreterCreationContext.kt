// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

/** The UI that adds the interpreter. Each context shows a different set of options in the shared panels. */
@org.jetbrains.annotations.ApiStatus.Internal
enum class InterpreterCreationContext {
  /** The New Project wizard. The project directory is new. The panels hide the existing environments of a tool and the uv mode choice. */
  NEW_PROJECT_WIZARD,

  /** The Add Interpreter dialog, local or on a target. The panels show every option. */
  ADD_INTERPRETER,
}
