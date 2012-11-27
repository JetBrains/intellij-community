/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.lang.xpath.xslt;

import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class XsltHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("urn:my");
        }
      });
    }

    public void xtestBackwardIncludedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownTemplate() throws Throwable {
        doXsltHighlighting();
    }

    public void testDuplicateTemplate() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownMode() throws Throwable {
        doXsltHighlighting();
    }

    public void testUndeclaredParam() throws Throwable {
        doXsltHighlighting();
    }

    public void testMissingParam() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnusedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testDuplicateVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testNonDuplicateVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testShadowedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testShadowedVariable2() throws Throwable {
        doXsltHighlighting();
    }

    public void testValidPatterns() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern1() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern2() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern3() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern4() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern5() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidPattern6() throws Throwable {
        doXsltHighlighting();
    }

    public void testEmptyExpression() throws Throwable {
        doXsltHighlighting();
    }

    public void testEmptyAVT() throws Throwable {
        doXsltHighlighting();
    }

    public void testInvalidSingleClosingBrace() throws Throwable {
        doXsltHighlighting();
    }

    public void testEscapedXPathString() throws Throwable {
        doXsltHighlighting();
    }

    public void testXsltFreeze() throws Throwable {
      doXsltHighlighting();
    }

    public void testTemplateWithPrefix() throws Throwable {
        myFixture.enableInspections(XmlUnusedNamespaceInspection.class);
        doXsltHighlighting();
    }

    public void xtestPerformance() throws Throwable {
        myFixture.configureByFile(getTestFileName() + ".xsl");
        final long l = runHighlighting();
        assertTrue("Highlighting took " + l + "ms", l < 6000);
    }

    private long runHighlighting() {
        final Project project = myFixture.getProject();
        PsiDocumentManager.getInstance(project).commitAllDocuments();

      return ApplicationManager.getApplication().runReadAction(new Computable<Long>() {
        @Override
        public Long compute() {
          final long l = System.currentTimeMillis();
          CodeInsightTestFixtureImpl.instantiateAndRun(myFixture.getFile(), myFixture.getEditor(), ArrayUtil.EMPTY_INT_ARRAY, false);
          return System.currentTimeMillis() - l;
        }
      });
    }

    private void doXsltHighlighting() throws Throwable {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, name + ".xsl");
    }

    @Override
    protected String getSubPath() {
        return "xslt/highlighting";
    }
}
