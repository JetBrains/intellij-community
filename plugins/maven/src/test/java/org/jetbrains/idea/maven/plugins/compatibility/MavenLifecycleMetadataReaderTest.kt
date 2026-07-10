// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import com.intellij.maven.testFramework.fixtures.MavenCustomRepositoryHelper
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.isRegularFile

@TestApplication
class MavenLifecycleMetadataReaderTest {
  private val maven by mavenFixture()

  @Test
  fun `test read data`() {
    val lifecycleInfo = MavenLifecycleMetadataReader.read("myplugin:myplugin:1.0",
      """
        <lifecycleMappingMetadata>
          <pluginExecutions>
            <pluginExecution>
              <pluginExecutionFilter>
                <goals>
                  <goal>some-goal</goal>
                </goals>
              </pluginExecutionFilter>
              <action>
                <execute>
                  <runOnIncremental>true</runOnIncremental>
                  <runOnConfiguration>true</runOnConfiguration>
                </execute>
              </action>
            </pluginExecution>
            <pluginExecution>
              <pluginExecutionFilter>
                <goals>
                  <goal>another-goal</goal>
                </goals>
              </pluginExecutionFilter>
              <action>
                <execute>
                  <runOnIncremental>false</runOnIncremental>
                  <runOnConfiguration>true</runOnConfiguration>
                </execute>
              </action>
            </pluginExecution>
          </pluginExecutions>
        </lifecycleMappingMetadata>
      """.toByteArray()
    )
    assertNotNull(lifecycleInfo)
    assertTrue(lifecycleInfo!!.runOnIncremental("some-goal"))
    assertTrue(lifecycleInfo.runOnConfiguration("some-goal"))
    assertTrue(lifecycleInfo.runOnConfiguration("another-goal"))
  }

  @Test
  fun `testMockPlugin`() {
    val helper = MavenCustomRepositoryHelper(maven.dir, "plugins")
    val path = helper.getTestData("plugins")
    val pathToFile = path.resolve("com/intellij/mavenplugin/maven-plugin-test-lifecycle/1.0/maven-plugin-test-lifecycle-1.0.jar")

    assertTrue(pathToFile.isRegularFile())
    val info = MavenArtifactUtil.readPluginInfo(pathToFile)
    assertNotNull(info)
    val lifecycles = info!!.lifecycles
    assertNotNull(lifecycles)
    assertTrue(lifecycles!!.runOnConfiguration("second"))
    assertFalse(lifecycles.runOnConfiguration("first"))

    assertFalse(lifecycles!!.runOnIncremental("second"))
    assertTrue(lifecycles.runOnIncremental("first"))
  }
}
