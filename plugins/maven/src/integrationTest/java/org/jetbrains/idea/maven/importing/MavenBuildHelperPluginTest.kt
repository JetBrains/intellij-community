package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.updateProjectPom
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.assertCompletionVariants
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenBuildHelperPluginTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
    
  @Test
  fun testCompletion() = runBlocking {
    maven.importProjectAsync(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

          <build>
            <plugins>
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                  <execution>
                    <id>yyy</id>
                    <goals>
                      <goal>reserve-network-port</goal>
                    </goals>
                    <configuration>
                      <portNames>
                        <portName>someNewProperty1</portName>
                        <portName>someNewProperty2</portName>
                      </portNames>
                    </configuration>
                  </execution>
                  <execution>
                    <id>xxx</id>
                    <goals>
                      <goal>foo</goal>
                    </goals>
                    <configuration>
                      <portNames>
                        <portName>someNewProperty3</portName>
                      </portNames>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>

          <properties>
            <aaa>${'$'}{someNew}</aaa>
          </properties>
        """.trimIndent())

    maven.updateProjectPom(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

          <build>
            <plugins>
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>build-helper-maven-plugin</artifactId>
                <executions>
                  <execution>
                    <id>yyy</id>
                    <goals>
                      <goal>reserve-network-port</goal>
                    </goals>
                    <configuration>
                      <portNames>
                        <portName>someNewProperty1</portName>
                        <portName>someNewProperty2</portName>
                      </portNames>
                    </configuration>
                  </execution>
                  <execution>
                    <id>xxx</id>
                    <goals>
                      <goal>foo</goal>
                    </goals>
                    <configuration>
                      <portNames>
                        <portName>someNewProperty3</portName>
                      </portNames>
                    </configuration>
                  </execution>
                </executions>
              </plugin>
            </plugins>
          </build>

          <properties>
            <aaa>${'$'}{someNew<caret>}</aaa>
          </properties>
        """.trimIndent())

    maven.assertCompletionVariants(maven.projectPom, "someNewProperty1", "someNewProperty2")
  }
}
