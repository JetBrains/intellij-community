// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package test.pkg

class Context {
    suspend fun inner(): Int = suspendPrivate()
    private suspend fun suspendPrivate(): Int = inner()
}


suspend fun top(): Int = Context().inner()
