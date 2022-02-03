// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.*
import java.io.File

private const val PUBLISH_COMPILER_GRADLE_TASK_NAME = "Publish compiler-for-ide jars"

fun generateRunConfigurations(dotIdea: File, kotlincArtifactsMode: KotlincArtifactsMode, kotlincVersion: String) {
    val publishRunConfigurationXml = dotIdea
        .resolve("runConfigurations")
        .resolve(PUBLISH_COMPILER_GRADLE_TASK_NAME.jpsEntityNameToFilename() + ".xml")
    when (kotlincArtifactsMode) {
        KotlincArtifactsMode.MAVEN -> {
            publishRunConfigurationXml.delete()
        }
        KotlincArtifactsMode.BOOTSTRAP -> {
            publishRunConfigurationXml.writeText(
                """
                    <component name="ProjectRunConfigurationManager">
                      <configuration default="false" name="$PUBLISH_COMPILER_GRADLE_TASK_NAME" type="GradleRunConfiguration" factoryName="Gradle">
                        <ExternalSystemSettings>
                          <option name="executionName" />
                          <option name="externalProjectPath" value="${'$'}PROJECT_DIR${'$'}/.." />
                          <option name="externalSystemIdString" value="GRADLE" />
                          <option name="scriptParameters" value="publishIdeArtifacts :prepare:ide-plugin-dependencies:kotlin-dist-for-ide:publish -Ppublish.ide.plugin.dependencies=true -PdeployVersion=${kotlincVersion}" />
                          <option name="taskDescriptions">
                            <list />
                          </option>
                          <option name="taskNames">
                            <list />
                          </option>
                          <option name="vmOptions" value="" />
                        </ExternalSystemSettings>
                        <ExternalSystemDebugServerProcess>true</ExternalSystemDebugServerProcess>
                        <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
                        <DebugAllEnabled>false</DebugAllEnabled>
                        <method v="2" />
                      </configuration>
                    </component>
                """.trimIndent()
            )
        }
    }
}
