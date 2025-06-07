// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

class Maven3SpyOutputExtractor : SpyOutputExtractor {
  override fun isSpyLog(s: String?): Boolean {
    return s != null && s.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_3)
  }

  override fun extract(line: String): String? {
    if (line.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_3)) {
      return line.substring(MavenSpyOutputParser.PREFIX_MAVEN_3.length)
    }
    return null
  }

  override fun isLengthEnough(s: String): Boolean {
    return s.length >= MavenSpyOutputParser.PREFIX_MAVEN_3.length
  }
}
