// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.data

import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.TestCase

abstract class TestCaseTemplate(val ideInfo: IdeInfo) {
  protected fun getTemplate() = TestCase(ideInfo = ideInfo)
}

