/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.LibraryManager
import com.intellij.debugger.streams.lib.LibrarySupport
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
class LibraryManagerImpl(project: Project) : LibraryManager {
  private val mySupportedPackages: Set<String>
  private val myLibraries: List<LibrarySupport>

  init {
    val std = StandardLibrarySupport(project)
    val streamEx = StreamExLibrarySupport(project)
    myLibraries = listOf(std, streamEx)
    mySupportedPackages = myLibraries.map { it.description.packageName }.toSet()
  }

  override fun isPackageSupported(packageName: String): Boolean = mySupportedPackages.contains(packageName)

  override fun getLibraryByPackage(packageName: String): LibrarySupport {
    return myLibraries.find { packageName == it.description.packageName }!!
  }
}
