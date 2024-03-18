package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenCombineChildAttributeTest : MavenDomTestCase() {
  @Test
  fun testCompletion() = runBlocking {
    createProjectPom(
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

    assertCompletionVariants(projectPom, "override", "append")
  }
}
