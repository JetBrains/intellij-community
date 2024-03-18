// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.java.workspace.entities.javaSettings
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.PsiJavaModule
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

class MavenJarManifestImportingTest : MavenMultiVersionImportingTestCase() {
  
  @Test
  fun `test jar manifest automatic module name`() = runBlocking {
    assumeTrue(isWorkspaceImport)
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                        <plugin>
                          <groupId>org.apache.maven.plugins</groupId>
                          <artifactId>maven-jar-plugin</artifactId>
                          <configuration>
                            <archive>
                              <manifestEntries>
                                <Automatic-Module-Name>my.module.name</Automatic-Module-Name>
                              </manifestEntries>
                            </archive>
                          </configuration>
                        </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())

    val automaticModuleName = WorkspaceModel.getInstance(
      project).currentSnapshot.resolve(ModuleId("project"))?.javaSettings?.manifestAttributes?.get(PsiJavaModule.AUTO_MODULE_NAME)
    assertEquals("my.module.name", automaticModuleName)
  }
}