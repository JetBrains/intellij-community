// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.IdeInfo

interface IdeInstallator {
  fun install(ideInfo: IdeInfo): Pair<String, InstalledIde>
}