// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers

class Maven4SpyOutputExtractor : SpyOutputExtractor {
  private val regex = "\u001B\\[[;\\d]*m".toRegex()
  override fun isSpyLog(s: String?): Boolean {
    return s != null && s.replace(regex, "").startsWith(MavenSpyOutputParser.PREFIX_MAVEN_4)
  }

  override fun extract(line: String): String? {
    var clearedLine = line.replace(regex, "");
    if (clearedLine.startsWith(MavenSpyOutputParser.PREFIX_MAVEN_4)) {
      return clearedLine.substring(MavenSpyOutputParser.PREFIX_MAVEN_4.length)
    }
    return null
  }

  override fun isLengthEnough(s: String): Boolean {
    val line = s.replace(regex, "");
    return line.length >= MavenSpyOutputParser.PREFIX_MAVEN_4.length
  }
}
