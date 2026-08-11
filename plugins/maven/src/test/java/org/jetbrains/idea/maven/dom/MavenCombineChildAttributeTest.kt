package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenCombineChildAttributeTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testCompletion() = runBlocking {
    maven.createProjectPom(
      """
              <groupId>test</groupId>
              <artifactId>project</artifactId>
              <version>1</version>

              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <executions>
                      <execution>
                        <goals>
                          <goal>test</goal>
                        </goals>
                        <phase>integration-test</phase>
                        <configuration combine.children="<caret>">
                          <groups>integrationsTests</groups>
                        </configuration>
                      </execution>
                    </executions>
                  </plugin>
                </plugins>
              </build>
        """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "override", "append", "merge")
  }
}
