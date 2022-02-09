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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class EncodingImportingTest extends MavenMultiVersionImportingTestCase {

  @Test
  void testEncodingDefinedByProperty() {
    byte[] text = [-12, -59, -53, -45, -44] // Russian text in koi8-r encoding.

    VirtualFile file = createProjectSubFile("src/main/resources/A.txt")
    ApplicationManager.application.runWriteAction { file.setBinaryContent(text) }

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<properties>
<project.build.sourceEncoding>koi8-r</project.build.sourceEncoding>
</properties>
""")

    def loadedText = VfsUtil.loadText(file)

    assert loadedText == new String(text, "koi8-r")
  }

  @Test
  void testEncodingDefinedByPluginConfig() {
    byte[] text = [-12, -59, -53, 45, -44] // Russian text in koi8-r encoding.

    VirtualFile file = createProjectSubFile("src/main/resources/A.txt")
    ApplicationManager.application.runWriteAction { file.setBinaryContent(text) }

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-resources-plugin</artifactId>
        <configuration>
          <encoding>koi8-r</encoding>
        </configuration>
      </plugin>
    </plugins>
  </build>
""")

    def loadedText = VfsUtil.loadText(file)

    assert loadedText == new String(text, "koi8-r")
  }

}
