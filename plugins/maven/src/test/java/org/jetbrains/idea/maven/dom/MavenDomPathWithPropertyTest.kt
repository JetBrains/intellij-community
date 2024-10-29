// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Ignore
import org.junit.Test


class MavenDomPathWithPropertyTest : MavenDomTestCase() {
  @Test
  @Ignore("IDEA-357023")
  fun testRename() = runBlocking {
    importProjectAsync(
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
      val dir = createProjectSubDir("aaa/bbb/res")

      val bbb = dir.getParent()

      fixture.renameElement(PsiManager.getInstance(fixture.getProject()).findDirectory(bbb)!!, "Z")

      val text = PsiManager.getInstance(fixture.getProject()).findFile(projectPom)!!.getText()
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/res</directory>"))
      assert(text.contains("<directory>aaa/Z/@rrr@</directory>"))
    }
  }

  @Test
  fun testCompletionDirectoriesOnly() = runBlocking {
    createProjectPom(
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

    createProjectSubFile("aaa/a.txt")
    createProjectSubFile("aaa/b.txt")
    createProjectSubDir("aaa/res1")
    createProjectSubDir("aaa/res2")

    assertCompletionVariants(projectPom, "res1", "res2")
  }
}
