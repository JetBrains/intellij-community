// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenFolderResolverTest : MavenMultiVersionImportingTestCase() {
  
  private lateinit var myTestSyncViewManager: SyncViewManager
  private val myEvents: MutableList<BuildEvent> = ArrayList()

  public override fun setUp() {
    super.setUp()
    myTestSyncViewManager = object : SyncViewManager(project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }
    project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, testRootDisposable)
  }

  @Test
  fun `test generate sources problems reported to console`() = runBlocking {
    createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-enforcer-plugin</artifactId>
                        <executions>
                          <execution>
                            <id>enforce-property</id>
                            <goals>
                              <goal>enforce</goal>
                            </goals>
                            <configuration>
                              <rules>
                                <requireProperty>
                                  <property>my-custom-property</property>
                                  <message>Please set my-custom-property system property
                                  </message>
                                </requireProperty>
                              </rules>
                            </configuration>
                          </execution>
                        </executions>
                      </plugin>
                    </plugins>
                  </build>
                  """.trimIndent())
    importProjectAsync()
    MavenFolderResolver(project).resolveFoldersAndImport()
    assertEvent { it.message.contains("Please set my-custom-property system property") }
  }

  private fun assertEvent(description: String = "Asserted", predicate: (BuildEvent) -> Boolean) {
    if (myEvents.isEmpty()) {
      fail("Message \"${description}\" was not found. No messages was recorded at all")
    }
    if (myEvents.any(predicate)) {
      return
    }

    fail("Message \"${description}\" was not found. Known messages:\n" +
         myEvents.joinToString("\n") { "${it}" })
  }

}