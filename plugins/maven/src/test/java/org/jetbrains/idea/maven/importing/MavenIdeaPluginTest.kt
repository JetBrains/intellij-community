// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.roots.ModuleRootManager
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenIdeaPluginTest : MavenDomTestCase() {
  override fun runInDispatchThread() = false
  
  @Test
  fun testConfigureJdk() = runBlocking {
    importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <build>
          <plugins>
            <plugin>
                <groupId>com.googlecode</groupId>
                <artifactId>maven-idea-plugin</artifactId>
                <version>1.6.1</version>

                <configuration>
                  <jdkName>invalidJdk</jdkName>
                </configuration>
            </plugin>
          </plugins>
        </build>
        """.trimIndent())

    val module = getModule("project")
    assert(!ModuleRootManager.getInstance(module).isSdkInherited())
    assert(ModuleRootManager.getInstance(module).getSdk() == null)
  }
}
