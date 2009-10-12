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

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.daemon.impl.TextEditorHighlightingPassRegistrarEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

import java.util.List;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class XsltHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new XsltStuffProvider(null).getInspectionClasses());
    }

    public void xtestBackwardIncludedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownTemplate() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownMode() throws Throwable {
        doXsltHighlighting();
    }

    public void testCurrentModeXslt2() throws Throwable {
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

    public void testShadowedVariable() throws Throwable {
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

    public void xtestPerformance() throws Throwable {
        myFixture.configureByFile(getTestFileName() + ".xsl");
        final long l = runHighlighting();
        assertTrue("Highlighting took " + l + "ms", l < 6000);
    }

    private long runHighlighting() {
        final Project project = myFixture.getProject();
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        return ApplicationManager.getApplication().runReadAction(new Computable<Long>() {
                    public Long compute() {
                        final long l = System.currentTimeMillis();
                        List<TextEditorHighlightingPass> passes =
                                TextEditorHighlightingPassRegistrarEx.getInstanceEx(myFixture.getProject()).instantiatePasses(myFixture.getFile(), myFixture.getEditor(), ArrayUtil.EMPTY_INT_ARRAY);
                        ProgressIndicator progress = new DaemonProgressIndicator();
                        for (TextEditorHighlightingPass pass : passes) {
                            pass.collectInformation(progress);
                        }
                        return System.currentTimeMillis() - l;
                    }
                });
    }

    private void doXsltHighlighting(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xsl"));
    }

    protected String getSubPath() {
        return "xslt/highlighting";
    }
}
