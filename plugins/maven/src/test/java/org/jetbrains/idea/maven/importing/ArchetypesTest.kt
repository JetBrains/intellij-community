// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.*
import org.junit.Assert
import java.io.File
import java.lang.Boolean
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.String

class ArchetypesTest : MavenTestCase() {
  
  /**
   * very time consumed test (uses the network and -U maven flag)
   */
  fun ignoreTestGenerating() = runBlocking {
    val dir = File(dir.path, "generated")
    dir.mkdirs()

    val params = MavenRunnerParameters(
      false, dir.path, null as String?,
      mutableListOf("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"),
      emptyList()
    )

    val settings = MavenRunnerSettings()
    val props: MutableMap<String, String> = HashMap()
    props["archetypeGroupId"] = "org.apache.maven.archetypes"
    props["archetypeArtifactId"] = "maven-archetype-quickstart"
    props["archetypeVersion"] = "1.0"
    props["interactiveMode"] = "false"
    props["groupId"] = "foo"
    props["artifactId"] = "bar"

    settings.mavenProperties = props
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA)
    val latch = CountDownLatch(1)
    MavenRunner.getInstance(project).run(params, settings) { latch.countDown() }

    val tryAcquire = latch.await(20, TimeUnit.SECONDS)
    assertTrue("Maven execution failed", tryAcquire)
    assertTrue(File(dir, "bar/pom.xml").exists())
  }

  fun testVmParametersGenerating() = runBlocking {
    val mavenArchetypeGeneratorPlugin = "org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"
    val params = MavenRunnerParameters(
      false, "generated", null as String?,
      Arrays.asList(mavenArchetypeGeneratorPlugin),
      emptyList()
    )

    val archetypeGroupId = "archetypeGroupId"
    val archetypeArtifactId = "archetypeArtifactId"
    val archetypeVersion = "archetypeVersion"
    val interactiveMode = "interactiveMode"
    val groupId = "groupId"
    val artifactId = "artifactId"

    val archetypeGroupIdValue = "org.apache.maven.archetypes"
    val archetypeArtifactIdValue = "maven-archetype-quickstart"
    val archetypeVersionValue = "1.0"
    val groupIdValue = "foo"
    val artifactIdValue = "bar"

    val settings = MavenRunnerSettings()
    val props: MutableMap<String, String> = HashMap()
    props[archetypeGroupId] = archetypeGroupIdValue
    props[archetypeArtifactId] = archetypeArtifactIdValue
    props[archetypeVersion] = archetypeVersionValue
    props[interactiveMode] = Boolean.FALSE.toString()
    props[groupId] = groupIdValue
    props[artifactId] = artifactIdValue

    settings.mavenProperties = props
    settings.setJreName(MavenRunnerSettings.USE_INTERNAL_JAVA)
    val configuration = MavenRunConfigurationType
      .createRunnerAndConfigurationSettings(null, settings, params, project)
      .getConfiguration() as MavenRunConfiguration

    val parameters = configuration.createJavaParameters(project)
    val parametersList = parameters.programParametersList
    Assert.assertEquals(archetypeGroupIdValue, parametersList.getPropertyValue(archetypeGroupId))
    Assert.assertEquals(archetypeArtifactIdValue, parametersList.getPropertyValue(archetypeArtifactId))
    Assert.assertEquals(archetypeVersionValue, parametersList.getPropertyValue(archetypeVersion))
    Assert.assertEquals(Boolean.FALSE.toString(), parametersList.getPropertyValue(interactiveMode))
    Assert.assertEquals(groupIdValue, parametersList.getPropertyValue(groupId))
    Assert.assertEquals(artifactIdValue, parametersList.getPropertyValue(artifactId))
    Assert.assertTrue(parametersList.hasParameter(mavenArchetypeGeneratorPlugin))
  }
}
