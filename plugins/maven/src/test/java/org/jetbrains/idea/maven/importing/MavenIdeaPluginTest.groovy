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

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.maven.testFramework.MavenDomTestCase
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenIdeaPluginTest extends MavenDomTestCase {
  @Test
  void testConfigureJdk() {
    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<build>
  <plugins>
    <plugin>
        <groupId>com.googlecode</groupId>
        <artifactId>maven-idea-plugin</artifactId>
        <version>1.6.1</version>

        <configuration>
          <jdkName>invalidJdk</jdkName>
        </configuration>
    </plugin>
  </plugins>
</build>
""")

    def module = getModule("project")
    assert module != null

    assert !ModuleRootManager.getInstance(module).sdkInherited
    assert ModuleRootManager.getInstance(module).sdk == null
  }

}
