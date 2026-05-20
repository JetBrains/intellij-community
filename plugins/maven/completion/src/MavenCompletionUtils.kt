// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.icons.AllIcons
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.repository.search.completion.api.DependencyCompletionContext
import com.intellij.repository.search.completion.api.DependencyCompletionContextImpl
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.utils.MavenUtil
import javax.swing.Icon

@ApiStatus.Internal
fun CompletionParameters.getCompletionContext(): DependencyCompletionContext =
  DependencyCompletionContextImpl(originalFile.virtualFile.toNioPath().getEelDescriptor(), MavenUtil.SYSTEM_ID)

@get:ApiStatus.Internal
val BaseDependencyCompletionResult.icon: Icon
  get() = when (source) {
    LOCAL -> AllIcons.Build.CompletionLocalCache
    SERVER -> AllIcons.Build.CompletionCloud
  }
