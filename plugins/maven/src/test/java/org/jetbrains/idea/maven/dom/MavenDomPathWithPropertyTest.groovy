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
import com.intellij.psi.PsiManager
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenDomPathWithPropertyTest extends MavenDomTestCase {

  @Test
  void testRename() {
    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<properties>
  <ppp>aaa</ppp>
  <rrr>res</rrr>
</properties>

<build>
  <resources>
    <resource>
      <directory>aaa/bbb/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/aaa/bbb/res</directory>
    </resource>
    <resource>
      <directory>\${pom.basedir}/@ppp@/bbb/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/bbb/res</directory>
    </resource>
    <resource>
      <directory>@ppp@/bbb/@rrr@</directory>
    </resource>
  </resources>
</build>
""")

    def dir = createProjectSubDir("aaa/bbb/res")

    def bbb = dir.parent
    myFixture.renameElement(PsiManager.getInstance(myFixture.project).findDirectory(bbb), "Z")


    def text = PsiManager.getInstance(myFixture.project).findFile(myProjectPom).text
    assert text.contains("<directory>aaa/Z/res</directory>");
    assert text.contains("<directory>aaa/Z/res</directory>");
    assert text.contains("<directory>aaa/Z/res</directory>");
    assert text.contains("<directory>aaa/Z/res</directory>");
    assert text.contains("<directory>aaa/Z/@rrr@</directory>");
  }

  @Test
  void testCompletionDirectoriesOnly() {
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <version>1</version>

    <properties>
      <ppp>aaa</ppp>
    </properties>

    <build>
      <resources>
        <resource>
          <directory>aaa/<caret></directory>
        </resource>
      </resources>
    </build>
    """)

    createProjectSubFile("aaa/a.txt")
    createProjectSubFile("aaa/b.txt")
    createProjectSubDir("aaa/res1")
    createProjectSubDir("aaa/res2")

    assertCompletionVariants(myProjectPom, "res1", "res2")
  }
}
