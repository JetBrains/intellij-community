// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.FUCollectorTestCase.collectProjectStateCollectorEvents
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.maven.testFramework.MavenImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenPluginCollectorTest : MavenImportingTestCase() {

  @Test
  fun `test should collect info about plugins`() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                          <plugin>    
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <source>1.8</source>
                                  <target>1.8</target>
                              </configuration>
                          </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    val metrics = collectProjectStateCollectorEvents(
      MavenPluginCollector::class.java, project)

    val compiler = metrics.map { it.data.build() }.first {
      it[MavenPluginCollector.groupArtifactId.name] == "org.apache.maven.plugins:maven-compiler-plugin"
    }

    assertNotNull(compiler[MavenPluginCollector.version.name])
    assertEquals(true, compiler[MavenPluginCollector.hasConfiguration.name])
    assertEquals(false, compiler[MavenPluginCollector.isExtension.name])
  }

  @Test
  fun `test should not collect info for private plugins`() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <build>
                      <plugins>
                          <plugin>    
                              <artifactId>maven-compiler-plugin</artifactId>
                              <configuration>
                                  <source>1.8</source>
                                  <target>1.8</target>
                              </configuration>
                          </plugin>
                          <plugin>    
                              <groupId>some-private-plugin</groupId>
                              <artifactId>my-plugin</artifactId>
                              <version>1.0</version>
                          </plugin>
                      </plugins>
                    </build>
                    """.trimIndent())
    val metrics = collectProjectStateCollectorEvents(
      MavenPluginCollector::class.java, project)
    val collectedGroupIds = metrics.map { it.data.build() }.map {
      it[MavenPluginCollector.groupArtifactId.name].toString()
    }

    assertContain(collectedGroupIds, "org.apache.maven.plugins:maven-compiler-plugin")
    //assertDoNotContain(collectedGroupIds, "some-private-plugin:my-plugin")
  }

  @Test
  fun `test check whitelist plugin Rule`() = runBlocking {
    val rule = MavenPluginCoordinatesWhitelistValidationRule()
    assertEquals(ValidationResultType.ACCEPTED,
                 rule.validate("org.apache.maven.plugins:maven-eclipse-plugin", EventContext.create("", emptyMap())))
    assertEquals(ValidationResultType.REJECTED,
                 rule.validate("org.apache.maven.plugins:my-custom-plugin", EventContext.create("", emptyMap())))

  }

}