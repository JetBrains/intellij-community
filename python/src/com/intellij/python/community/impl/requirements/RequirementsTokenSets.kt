// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.requirements

import com.intellij.psi.tree.TokenSet


interface RequirementsTokenSets {
  companion object {
    val COMMENTS = TokenSet.create(com.intellij.python.community.impl.requirements.psi.RequirementsTypes.COMMENT)
  }
}