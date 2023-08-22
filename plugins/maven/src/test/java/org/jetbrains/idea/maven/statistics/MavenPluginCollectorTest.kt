// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.FUCollectorTestCase.collectProjectStateCollectorEvents
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.maven.testFramework.MavenImportingTestCase
import org.junit.Test

class MavenPluginCollectorTest : MavenImportingTestCase() {

  @Test
  fun `test should collect info about plugins`() {
    importProject("""
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
      MavenPluginCollector::class.java, myProject)

    val compiler = metrics.map { it.data.build() }.first {
      it[MavenPluginCollector.groupArtifactId.name] == "org.apache.maven.plugins:maven-compiler-plugin"
    }

    assertNotNull(compiler[MavenPluginCollector.version.name])
    assertEquals(true, compiler[MavenPluginCollector.hasConfiguration.name])
    assertEquals(false, compiler[MavenPluginCollector.isExtension.name])
  }

  @Test
  fun `test should not collect info for private plugins`() {
    importProject("""
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
      MavenPluginCollector::class.java, myProject)
    val collectedGroupIds = metrics.map { it.data.build() }.map {
      it[MavenPluginCollector.groupArtifactId.name].toString()
    }

    assertContain(collectedGroupIds, "org.apache.maven.plugins:maven-compiler-plugin")
    //assertDoNotContain(collectedGroupIds, "some-private-plugin:my-plugin")
  }

  @Test
  fun `test check whitelist plugin Rule`() {
    val rule = MavenPluginCoordinatesWhitelistValidationRule()
    assertEquals(ValidationResultType.ACCEPTED,
                 rule.validate("org.apache.maven.plugins:maven-eclipse-plugin", EventContext.create("", emptyMap())))
    assertEquals(ValidationResultType.REJECTED,
                 rule.validate("org.apache.maven.plugins:my-custom-plugin", EventContext.create("", emptyMap())))

  }

  @Test
  fun `test check regexp version`() {
    val rule = MavenPluginVersionValidationRule()

    val acceptedVersions = listOf(
      "0.0.4",
      "1.2.3",
      "10.20.30",
      "1",
      "1.2",
      "1.1.2-prerelease+meta",
      "1.1.2+meta",
      "1.1.2+meta-valid",
      "1.0.0-alpha",
      "1.0.0-beta",
      "1.0.0-alpha.beta",
      "1.0.0-alpha.beta.1",
      "1.0.0-alpha.1",
      "1.0.0-alpha0.valid",
      "1.0.0-alpha.0valid",
      "1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay",
      "1.0.0-rc.1+build.1",
      "2.0.0-rc.1+build.123",
      "1.2.3-beta",
      "10.2.3-DEV-SNAPSHOT",
      "1.2.3-SNAPSHOT-123",
      "1.0.0",
      "2.0.0",
      "1.1.7",
      "2.0.0+build.1848",
      "2.0.1-alpha.1227",
      "1.0.0-alpha+beta",
      "1.2.3----RC-SNAPSHOT.12.9.1--.12+788",
      "1.2.3----R-S.12.9.1--.12+meta",
      "1.2.3----RC-SNAPSHOT.12.9.1--.12",
      "1.0.0+0.build.1-rc.10000aaa-kk-0.1",
      "99999999999999999999999.999999999999999999.99999999999999999",
      "1.0.0-0A.is.legal");

    acceptedVersions.forEach {
      assertEquals(ValidationResultType.ACCEPTED,
                   rule.validate(it, EventContext.create("", emptyMap())))

    }
    assertEquals(ValidationResultType.REJECTED,
                 rule.validate("1.A", EventContext.create("", emptyMap())))
    assertEquals(ValidationResultType.REJECTED,
                 rule.validate("Some.String.", EventContext.create("", emptyMap())))

  }
}