// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.messages

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

internal object SpaceBundleExtensions {
  fun messagePointer(@PropertyKey(resourceBundle = SpaceBundle.BUNDLE) key: String, vararg params: Any): () -> @Nls String = {
    SpaceBundle.message(key, *params)
  }
}