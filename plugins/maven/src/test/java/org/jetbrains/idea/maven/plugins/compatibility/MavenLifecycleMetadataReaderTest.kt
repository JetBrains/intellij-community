// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.plugins.compatibility

import com.intellij.maven.testFramework.MavenTestCase

class MavenLifecycleMetadataReaderTest : MavenTestCase() {

  fun `test read data`() {
    val lifecycleInfo = MavenLifecycleMetadataReader().read(
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
    assertTrue(lifecycleInfo.runOnIncremental("some-goal"))
    assertTrue(lifecycleInfo.runOnConfiguration("some-goal"))
    assertTrue(lifecycleInfo.runOnConfiguration("another-goal"))
  }
}