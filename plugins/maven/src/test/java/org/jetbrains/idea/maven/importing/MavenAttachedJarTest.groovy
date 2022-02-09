/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.maven.testFramework.MavenDomTestCase
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenAttachedJarTest extends MavenDomTestCase {
  @Test
  void testImporting() {
    createModulePom("m1", """
<groupId>test</groupId>
<artifactId>m1</artifactId>
<version>1</version>

    <dependencies>
        <dependency>
            <groupId>test</groupId>
            <artifactId>m2</artifactId>
            <version>1</version>
        </dependency>
    </dependencies>

""")

    def file = createProjectSubFile("m1/src/main/java/Foo.java", """
class Foo {
  void foo() {
    junit.framework.TestCase a = null;
    junit.framework.<error>TestCase123</error> b = null;
  }
}
""")

    def jarPath = PlatformTestUtil.getCommunityPath() + "/plugins/maven/src/test/data/local1/junit/junit/3.8.1/junit-3.8.1.jar"

    createModulePom("m2", """
<groupId>test</groupId>
<artifactId>m2</artifactId>
<version>1</version>
<packaging>pom</packaging>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <executions>
          <execution>
            <phase>compile</phase>
            <goals>
              <goal>attach-artifact</goal>
            </goals>
            <configuration>
              <artifacts>
                <artifact>
                  <file>${jarPath}</file>
                  <type>jar</type>
                </artifact>
              </artifacts>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
""")

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<packaging>pom</packaging>

<modules>
  <module>m1</module>
  <module>m2</module>
</modules>
""")

    checkHighlighting(file, true, false, true)
  }

}
