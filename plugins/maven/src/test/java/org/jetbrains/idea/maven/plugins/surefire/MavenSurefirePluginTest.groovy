/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.surefire

import org.jetbrains.idea.maven.MavenImportingTestCase
import org.jetbrains.idea.maven.dom.MavenDomTestCase

/**
 * @author Sergey Evdokimov
 */
class MavenSurefirePluginTest extends MavenDomTestCase {

  void testCompletion() {
    importProject("""
  <groupId>simpleMaven</groupId>
  <artifactId>simpleMaven</artifactId>
  <packaging>jar</packaging>
  <version>1.0</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <additionalClasspathElements>
            <additionalClasspathElement>\${basedir}/src/<caret></additionalClasspathElement>
          </additionalClasspathElements>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    createProjectSubFile("src/main/A.txt", "")
    createProjectSubFile("src/test/A.txt", "")
    createProjectSubFile("src/A.txt", "")

    assertCompletionVariants(myProjectPom, "main", "test")
  }

}
