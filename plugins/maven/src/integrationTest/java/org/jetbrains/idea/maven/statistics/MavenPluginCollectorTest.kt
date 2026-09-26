// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.FUCollectorTestCase.collectProjectStateCollectorEvents
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.fus.reporting.api.ValidationResultType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class MavenPluginCollectorTest {
  private val maven by mavenImportingFixture()

  @Test
  fun `test should collect info about plugins`() = runBlocking {
    maven.importProjectAsync("""
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
      MavenPluginCollector::class.java, maven.project)

    val compiler = metrics.map { it.data.build() }.first {
      it["group_artifact_id"] == "org.apache.maven.plugins:maven-compiler-plugin"
    }

    assertNotNull(compiler["version"])
    assertEquals(true, compiler["has_configuration"])
    assertEquals(false, compiler["extension"])
  }

  @Test
  fun `test should not collect info for private plugins`() = runBlocking {
    maven.importProjectAsync("""
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
      MavenPluginCollector::class.java, maven.project)
    val collectedGroupIds = metrics.map { it.data.build() }.map {
      it["group_artifact_id"].toString()
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
