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
package org.jetbrains.idea.maven.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenPropertyInActivationSectionTest extends MavenDomTestCase {

  @Test
  void testResolvePropertyFromActivationSection() throws IOException {
    importProject("""
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
        <glassfish.home.path>\${env.GLASSFISH_HOME_123}</glassfish.home.path>
      </properties>
    </profile>

  </profiles>

  <properties>
    <aaa>\${env.GLASSFISH_HOME_123}</aaa>
  </properties>
""")


    assert getReference(myProjectPom, "env.GLASSFISH_HOME_123", 1).resolve() != null
    assert getReference(myProjectPom, "env.GLASSFISH_HOME_123", 2).resolve() == null
  }

}
