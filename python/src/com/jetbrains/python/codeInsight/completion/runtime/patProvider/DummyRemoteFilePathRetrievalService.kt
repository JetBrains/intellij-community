// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion.runtime.patProvider

import com.intellij.codeInsight.completion.CompletionParameters
import com.jetbrains.python.codeInsight.completion.RuntimeLookupElement

class DummyRemoteFilePathRetrievalService : RemoteFilePathRetrievalService {
  override fun retrieveRemoteFileLookupElements(parameters: CompletionParameters): Map<String, RuntimeLookupElement> = emptyMap()
}