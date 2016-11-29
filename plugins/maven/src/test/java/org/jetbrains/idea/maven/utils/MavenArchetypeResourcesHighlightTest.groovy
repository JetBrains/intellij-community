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
package org.jetbrains.idea.maven.utils

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.psi.PsiFile

/**
 * @author Sergey Evdokimov
 */
class MavenArchetypeResourcesHighlightTest extends LightCodeInsightFixtureTestCase {

  void testHighlight() throws Exception {
    PsiFile file = myFixture.addFileToProject("src/main/resources/archetype-resources/src/main/java/A.java", """
import \${package};
class A {
}
""")
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.checkHighlighting()

    file = myFixture.addFileToProject("src/main/resources/B.java", """
import <error>\$</error><error><error>{</error>package};</error>
class B {
}
""")
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile())
    myFixture.checkHighlighting()
  }

}

