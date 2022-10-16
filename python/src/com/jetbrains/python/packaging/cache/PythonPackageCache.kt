// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.cache

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PythonPackageCache<K> {
  val packages: List<String>
  operator fun contains(key: K): Boolean
  fun isEmpty(): Boolean
}