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

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.psi.PsiElement
import org.junit.Test

/**
 * @author Sergey Evdokimov
 */
class MavenPomXmlDocumentationTest extends MavenDomTestCase {

  @Test
  void testDocumentation() {
    createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>
<scm>
  <connection<caret>></connection>
</scm>"""
    )

    def expectedText = """Tag name:&nbsp;<b>connection</b><br>Description  :&nbsp;The source control management system URL
                  that describes the repository and how to connect to the
                  repository. For more information, see the
                  <a href="http://maven.apache.org/scm/scm-url-format.html">URL format</a>
                  and <a href="http://maven.apache.org/scm/scms-overview.html">list of supported SCMs</a>.
                  This connection is read-only.<br>Version  :&nbsp;4.0.0"""

    PsiElement originalElement = getElementAtCaret(myProjectPom)
    PsiElement targetElement = DocumentationManager.getInstance(myProject).findTargetElement(getEditor(), getTestPsiFile(), originalElement)

    DocumentationProvider provider = DocumentationManager.getProviderFromElement(targetElement)

    assert expectedText.replaceAll(" +", " ") == provider.generateDoc(targetElement, originalElement).replaceAll(" +", " ")
  }

}
