// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.process

data class MacOsProcessMetaInfo(
  override val pid: Int,
  override val command: String
) : ProcessMetaInfo(pid, command)