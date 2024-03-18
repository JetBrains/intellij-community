package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenBuildHelperPluginTest : MavenDomTestCase() {
    
  @Test
  fun testCompletion() = runBlocking {
    importProjectAsync(
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

    createProjectPom(
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

    assertCompletionVariants(projectPom, "someNewProperty1", "someNewProperty2")
  }
}
