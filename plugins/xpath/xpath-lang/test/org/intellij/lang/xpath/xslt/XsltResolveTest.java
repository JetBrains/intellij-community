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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltFunction;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class XsltResolveTest {
    private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
    private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
    private String testFileName;

    @BeforeEach
    void setUp(TestInfo testInfo) {
      codeInsightFixture.get().setTestDataPath(XPathTestUtil.getTestDataPath("xslt/resolve"));
      testFileName = XPathTestUtil.getTestFileName(testInfo);
    }

    @Test
    public void testResolveSingleVariableGlobal() {
        doVariableResolveTest(true);
    }

    @Test
    public void testResolveForwardVariable() {
        doVariableResolveTest(true);
    }

    @Test
    public void testResolveSingleVariable() {
        doVariableResolveTest(false);
    }

    @Test
    public void testResolveShadowedVariable() {
        doVariableResolveTest(false);
    }

    @Test
    public void testResolveIncludedFunction() {
        doFunctionResolveTest("included-2.xsl");
    }

    @Test
    public void testResolveFunction() {
        doFunctionResolveTest();
    }

    @Test
    public void testResolveSameName() {
        final XsltVariable variable = doVariableResolveTest(false);

        ReadAction.runBlocking(() -> {
            final XsltTemplate template = XsltCodeInsightUtil.getTemplate(variable, false);
            assertNotNull(template);
            assertNotNull(template.getName());
        });
    }

    @Test
    public void testResolveIncludedTemplateParam() {
        CodeInsightTestFixture myFixture = codeInsightFixture.get();
        final String name = testFileName;
        final PsiReference reference = myFixture.getReferenceAtCaretPositionWithAssertion(name + ".xsl", "included.xsl");

        ReadAction.runBlocking(() -> {
            final PsiElement element = resolveXsltReference(reference);
            assertNotNull(element, "reference did not resolve to XsltElement: " + reference);

            assertTrue(element instanceof XsltParameter);
            assertEquals("foo", ((XsltParameter)element).getName());
        });
    }

    @Nullable
    private static PsiElement resolveXsltReference(PsiReference reference) {
        final PsiElement element = reference.resolve();
        if (element != null) {
            return element;
        }
        if (reference instanceof PsiPolyVariantReference) {
            final ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
            for (ResolveResult result : results) {
                if (result.isValidResult() && result.getElement() instanceof XsltElement) {
                    return result.getElement();
                }
            }
        }
        return null;
    }

    private XsltVariable doVariableResolveTest(boolean global) {
        final PsiReference reference = findInjectedReferenceAtCaret();

        return ReadAction.computeBlocking(() -> {
            final PsiElement element = reference.resolve();
            assertTrue(element instanceof XsltVariable);

            final XsltVariable var = (XsltVariable)element;
            assertEquals(var.getName(), ((XPathVariableReference)reference).getReferencedName());
            assertEquals(global, XsltSupport.isTopLevelElement(var.getTag()));

            return var;
        });
    }

    private XsltFunction doFunctionResolveTest(String... files) {
        final PsiReference reference = findInjectedReferenceAtCaret(files);

        return ReadAction.computeBlocking(() -> {
            final PsiElement element = reference.resolve();
            assertTrue(element instanceof XsltFunction);

            final XsltFunction func = (XsltFunction)element;
            final XPathFunctionCall call = (XPathFunctionCall)reference.getElement();
            assertEquals(func.getName(), call.getFunctionName());
            assertEquals(func.getParameters().length, call.getArgumentList().length);
            return func;
        });
    }

    @NotNull
    private PsiReference findInjectedReferenceAtCaret(String... moreFiles) {
        CodeInsightTestFixture myFixture = codeInsightFixture.get();
        configure(moreFiles);

        return ReadAction.computeBlocking(() -> {
            final PsiElement e = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
            assertNotNull(e);

            final PsiReference reference = e.getContainingFile().findReferenceAt(e.getTextOffset());
            assertNotNull(reference);
            return reference;
        });
    }

    private void configure(String... moreFiles) {
      CodeInsightTestFixture myFixture = codeInsightFixture.get();
      myFixture.configureByFiles(ArrayUtil.mergeArrays(new String[]{testFileName + ".xsl"}, moreFiles));
    }
}