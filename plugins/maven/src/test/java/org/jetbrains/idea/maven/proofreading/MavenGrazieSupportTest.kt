package org.jetbrains.idea.maven.proofreading

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