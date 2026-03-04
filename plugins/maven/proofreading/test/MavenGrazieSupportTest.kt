// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.proofreading

import com.intellij.grazie.GrazieTestBase

class MavenGrazieSupportTest: GrazieTestBase() {
  override fun getBasePath() = "plugins/grazie/src/test/testData"
  override fun isCommunity() = true

  fun `test no proofreading checks in maven-related files`() {
    runHighlightTestForFile("ide/language/maven/pom.xml")
    runHighlightTestForFile("ide/language/maven/library-1.0.0.pom")
    runHighlightTestForFile("ide/language/maven/_remote.repositories")
    runHighlightTestForFile("ide/language/maven/library-1.0.0.pom.sha1")
  }
}