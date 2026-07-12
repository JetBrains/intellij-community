// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.jetbrains.annotations.ApiStatus

@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message = "This class can only be used by PyCharm Exec Subteam. " +
            "Do not touch it unless you were explicitly asked to do so by Exec Subteam member. " +
            "Really, do not touch it, this class might become `internal` in a next minor update. " +
            "If you are 100% sure you need it, ask PyCharm Exec Subteam first."
)
@ApiStatus.Internal
annotation class PyInternalExecApi
