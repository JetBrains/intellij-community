// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.tools.model.updater

import org.jdom.Text
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

fun patchRunConfigurations(dotIdea: File, kotlincArtifactsMode: KotlincArtifactsMode) {
    listOf("runConfigurations/IDEA.xml", "runConfigurations/IDEA_Community.xml", "IDEA_Community_FIR.xml")
        .map { dotIdea.resolve(it) }
        .filter { it.exists() }
        .forEach { runConfigurationFile ->
            val root = runConfigurationFile.readXml()
            root.traverseChildren().filter {
                it.name == "option" && it.getAttributeValue("run_configuration_name") == PUBLISH_COMPILER_GRADLE_TASK_NAME
            }.single().detach()
            if (kotlincArtifactsMode == KotlincArtifactsMode.BOOTSTRAP) {
                root.traverseChildren().filter { it.name == "method" && it.getAttributeValue("v") == "2" }.single().addContent(
                    0,
                    ("""<option name="RunConfigurationTask" enabled="true" """ +
                            """run_configuration_name="$PUBLISH_COMPILER_GRADLE_TASK_NAME" """ +
                            """run_configuration_type="GradleRunConfiguration" />""").readXml().detach()
                )
                runConfigurationFile.writeText(root.writeXml())
            }
        }
}
