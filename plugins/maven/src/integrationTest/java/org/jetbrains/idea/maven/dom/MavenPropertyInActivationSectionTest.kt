package org.jetbrains.idea.maven.dom

import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import org.jetbrains.idea.maven.fixtures.resolveReference
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenPropertyInActivationSectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testResolvePropertyFromActivationSection() = runBlocking {
    maven.importProjectAsync(
      """
          <groupId>example</groupId>
          <artifactId>parent</artifactId>
          <packaging>jar</packaging>
          <version>1.0</version>
          <name>example</name>

          <profiles>
            <profile>
              <id>glassfish-env-path</id>
              <activation>
                <property>
                  <name>env.GLASSFISH_HOME_123</name>
                </property>
              </activation>

              <properties>
                <glassfish.home.path>${'$'}{env.GLASSFISH_HOME_123}</glassfish.home.path>
              </properties>
            </profile>

          </profiles>

          <properties>
            <aaa>\${'$'}{env.GLASSFISH_HOME_123}</aaa>
          </properties>
        """.trimIndent())

    assert(maven.resolveReference(maven.projectPom, "env.GLASSFISH_HOME_123", 1) != null)
    assert(maven.resolveReference(maven.projectPom, "env.GLASSFISH_HOME_123", 2) == null)
  }
}
