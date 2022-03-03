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
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicatePluginInspection
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenDuplicatedPluginInspectionTest extends MavenDomTestCase {

  @Test
  void testDuplicatedPlugin() {
    myFixture.enableInspections(MavenDuplicatePluginInspection)

    createProjectPom("""
  <groupId>mavenParent</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>

  <build>
    <plugins>
      <<warning>plugin</warning>>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>2.2</version>
      </plugin>
      <<warning>plugin</warning>>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-jar-plugin</artifactId>
        <version>2.2</version>
      </plugin>
    </plugins>
  </build>
""")

    checkHighlighting()
  }
}
