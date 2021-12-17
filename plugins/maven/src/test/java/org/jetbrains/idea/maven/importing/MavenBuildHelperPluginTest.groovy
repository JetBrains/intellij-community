/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenBuildHelperPluginTest extends MavenDomTestCase {
  @Test
  void testCompletion() {
    importProject("""
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
    <aaa>\${someNew}</aaa>
  </properties>
""")

    createProjectPom("""
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
    <aaa>\${someNew<caret>}</aaa>
  </properties>
""")

    assertCompletionVariants(myProjectPom, "someNewProperty1", "someNewProperty2")
  }

}
