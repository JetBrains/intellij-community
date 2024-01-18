// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing

import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PyStubPackagesInstallingStatus {

  private val installing = ConcurrentHashMap.newKeySet<String>()

  fun markAsInstalling(stubPkgs: Collection<String>): Boolean = installing.addAll(stubPkgs)
  fun unmarkAsInstalling(stubPkgs: Collection<String>): Boolean = installing.removeAll(stubPkgs)

  fun markedAsInstalling(stubPkg: String): Boolean = installing.contains(stubPkg)
}