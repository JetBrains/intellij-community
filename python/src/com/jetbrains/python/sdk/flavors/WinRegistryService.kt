// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.io.WindowsRegistryUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Win registry access service
 *
 * @author Ilya.Kazakevich
 */
@ApiStatus.Internal

interface WinRegistryService {
  /**
   * @param basePath path like "HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node"
   * @return list of subbranches
   */
  fun listBranches(basePath: String): List<String>

  /**
   * @param path path like "HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node"
   * @return its (Default) value
   */
  fun getDefaultKey(path: String): String?
}

internal class WinRegistryServiceImpl : WinRegistryService {
  override fun listBranches(basePath: String): List<String> = WindowsRegistryUtil.readRegistryBranch(basePath)

  override fun getDefaultKey(path: String): String? = WindowsRegistryUtil.readRegistryDefault(path)
}