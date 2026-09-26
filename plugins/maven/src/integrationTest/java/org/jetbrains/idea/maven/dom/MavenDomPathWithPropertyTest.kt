// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.createProjectSubDir
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenDomPathWithPropertyTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  @Disabled("IDEA-357023")
  fun testRename() = runBlocking {
    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <properties>
          <ppp>aaa</ppp>
          <rrr>res</rrr>
        </properties>

        <build>
          <resources>
            <resource>
              <directory>aaa/bbb/res</directory>
            </resource>
            <resource>
              <directory>${'$'}{pom.basedir}/aaa/bbb/res</directory>
            </resource>
            <resource>
              <directory>${'$'}{pom.basedir}/@ppp@/bbb/res</directory>
            </resource>
            <resource>
              <directory>@ppp@/bbb/res</directory>
            </resource>
            <resource>
              <directory>@ppp@/bbb/@rrr@</directory>
            </resource>
          </resources>
        </build>
        """.trimIndent())

    withContext(Dispatchers.EDT) {
      val dir = maven.createProjectSubDir("aaa/bbb/res")

      val bbb = dir.getParent()

      maven.fixture.renameElement(PsiManager.getInstance(maven.fixture.getProject()).findDirectory(bbb)!!, "Z")

      val text = PsiManager.getInstance(maven.fixture.getProject()).findFile(maven.projectPom)!!.getText()
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/@rrr@</directory>"))
    }
  }

  @Test
  fun testCompletionDirectoriesOnly() = runBlocking {
    maven.createProjectPom(
      """
            <groupId>test</groupId>
            <artifactId>project</artifactId>
            <version>1</version>

            <properties>
              <ppp>aaa</ppp>
            </properties>

            <build>
              <resources>
                <resource>
                  <directory>aaa/<caret></directory>
                </resource>
              </resources>
            </build>
            """.trimIndent())

    maven.createProjectSubFile("aaa/a.txt")
    maven.createProjectSubFile("aaa/b.txt")
    maven.createProjectSubDir("aaa/res1")
    maven.createProjectSubDir("aaa/res2")

    maven.assertCompletionVariants(maven.projectPom, "res1", "res2")
  }
}
