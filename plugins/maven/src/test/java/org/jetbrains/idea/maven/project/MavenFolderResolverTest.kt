// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.build.SyncViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenFolderResolverTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  private lateinit var myTestSyncViewManager: SyncViewManager
  private val myEvents: MutableList<BuildEvent> = ArrayList()

  @BeforeEach
  fun setUp() {
    myTestSyncViewManager = object : SyncViewManager(maven.project) {
      override fun onEvent(buildId: Any, event: BuildEvent) {
        myEvents.add(event)
      }
    }
    maven.project.replaceService(SyncViewManager::class.java, myTestSyncViewManager, maven.disposable)
  }

  @Test
  fun `test generate sources problems reported to console`() = runBlocking {
    maven.createProjectPom("""
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
    maven.importProjectAsync()
    MavenFolderResolver(maven.project).resolveFoldersAndImport()
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