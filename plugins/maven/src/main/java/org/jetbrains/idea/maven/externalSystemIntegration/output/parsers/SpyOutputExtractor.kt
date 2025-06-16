// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

interface SpyOutputExtractor {
  fun isSpyLog(s: String?): Boolean
  fun extract(spyLine: String): String?
  fun isLengthEnough(s: String): Boolean
}