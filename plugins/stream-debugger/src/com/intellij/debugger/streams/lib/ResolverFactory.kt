// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib

import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.openapi.util.NlsSafe

/**
 * @author Vitaliy.Bibaev
 */
interface ResolverFactory {
  fun getResolver(@NlsSafe callName: String): ValuesOrderResolver
}