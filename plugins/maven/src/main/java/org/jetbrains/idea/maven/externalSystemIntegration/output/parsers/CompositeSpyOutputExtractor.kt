// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

internal class CompositeSpyOutputExtractor(vararg val extractors: SpyOutputExtractor) : SpyOutputExtractor {
  override fun isSpyLog(s: String?): Boolean {
    return extractors.any { it.isSpyLog(s) }
  }

  override fun extract(s: String): String? {
    return extractors.firstNotNullOfOrNull {
      if (it.isSpyLog(s)) {
        it.extract(s)
      } else {
        null
      }
    }
  }

  override fun isLengthEnough(s: String): Boolean {
    return extractors.all { it.isLengthEnough(s) }
  }
}
