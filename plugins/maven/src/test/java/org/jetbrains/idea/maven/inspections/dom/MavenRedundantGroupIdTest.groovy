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

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.maven.testFramework.MavenDomTestCase
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenRedundantGroupIdTest extends MavenDomTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp()

    myFixture.enableInspections(MavenRedundantGroupIdInspection)
  }

  @Test
  void testHighlighting1() {
    createProjectPom("""
  <groupId>my.group</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>
""")

    checkHighlighting()
  }

  @Test
  void testHighlighting2() {
    createProjectPom("""
  <groupId>childGroupId</groupId>
  <artifactId>childA</artifactId>
  <version>1.0</version>

  <parent>
    <groupId>my.group</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
  </parent>
""")

    checkHighlighting()
  }

  @Test
  void testHighlighting3() {
    createProjectPom("""
  <warning><groupId>my.group</groupId></warning>
  <artifactId>childA</artifactId>
  <version>1.0</version>

  <parent>
    <groupId>my.group</groupId>
    <artifactId>parent</artifactId>
    <version>1.0</version>
  </parent>
""")

    checkHighlighting()
  }

  @Test
  void testQuickFix() {
    createProjectPom("""
    <artifactId>childA</artifactId>
    <groupId>mavenParen<caret>t</groupId>
    <version>1.0</version>

    <parent>
      <groupId>mavenParent</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version>
    </parent>
""")

    myFixture.configureFromExistingVirtualFile(myProjectPom)
    myFixture.doHighlighting()

    for (IntentionAction intention : myFixture.getAvailableIntentions()) {
      if (intention.getText().startsWith("Remove ") && intention.getText().contains("<groupId>")) {
        myFixture.launchAction(intention)
        break
      }
    }

    //doPostponedFormatting(myProject)
    PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting()

    myFixture.checkResult(createPomXml("""
    <artifactId>childA</artifactId>
    <version>1.0</version>

    <parent>
      <groupId>mavenParent</groupId>
      <artifactId>childA</artifactId>
      <version>1.0</version>
    </parent>
"""))
  }
}
