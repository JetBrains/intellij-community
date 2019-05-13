/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.flavors

import com.intellij.openapi.util.io.WindowsRegistryUtil

/**
 * Win registry access service
 *
 * @author Ilya.Kazakevich
 */
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

class WinRegistryServiceImpl : WinRegistryService {
  override fun listBranches(basePath: String): List<String> = WindowsRegistryUtil.readRegistryBranch(basePath)

  override fun getDefaultKey(path: String): String? = WindowsRegistryUtil.readRegistryDefault(path)
}