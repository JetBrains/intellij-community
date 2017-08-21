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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.psi.XPathFunctionCall;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.psi.*;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XsltResolveTest extends TestBase {

    public void testResolveSingleVariableGlobal() throws Throwable {
        doVariableResolveTest(true);
    }

    public void testResolveForwardVariable() throws Throwable {
        doVariableResolveTest(true);
    }

    public void testResolveSingleVariable() throws Throwable {
        doVariableResolveTest(false);
    }

    public void testResolveShadowedVariable() throws Throwable {
        doVariableResolveTest(false);
    }

    public void testResolveIncludedFunction() throws Throwable {
        doFunctionResolveTest("included-2.xsl");
    }

    public void testResolveFunction() throws Throwable {
        doFunctionResolveTest();
    }

    public void testResolveSameName() throws Throwable {
        final XsltVariable variable = doVariableResolveTest(false);

        final XsltTemplate template = XsltCodeInsightUtil.getTemplate(variable, false);
        assertNotNull(template);
        assertNotNull(template.getName());
    }

    public void testResolveIncludedTemplateParam() {
        final String name = getTestFileName();
        final PsiReference reference = myFixture.getReferenceAtCaretPositionWithAssertion(name + ".xsl", "included.xsl");

        final PsiElement element = resolveXsltReference(reference);
        assertNotNull("reference did not resolve to XsltElement: " + reference, element);

        assertTrue(element instanceof XsltParameter);
        assertEquals("foo", ((XsltParameter)element).getName());
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

        final PsiElement element = reference.resolve();
        assertTrue(element instanceof XsltVariable);

        final XsltVariable var = (XsltVariable)element;
        assertEquals(var.getName(), ((XPathVariableReference)reference).getReferencedName());
        assertEquals(global, XsltSupport.isTopLevelElement(var.getTag()));

        return var;
    }

    private XsltFunction doFunctionResolveTest(String... files) {
        final PsiReference reference = findInjectedReferenceAtCaret(files);

        final PsiElement element = reference.resolve();
        assertTrue(element instanceof XsltFunction);

        final XsltFunction func = (XsltFunction)element;
        final XPathFunctionCall call = (XPathFunctionCall)reference.getElement();
        assertEquals(func.getName(), call.getFunctionName());
        assertEquals(func.getParameters().length, call.getArgumentList().length);
        return func;
    }

    @NotNull
    private PsiReference findInjectedReferenceAtCaret(String... moreFiles) {
        configure(moreFiles);

        final PsiElement e = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
        assertNotNull(e);

        final PsiReference reference = e.getContainingFile().findReferenceAt(e.getTextOffset());
        assertNotNull(reference);
        return reference;
    }

    private void configure(String... moreFiles) {
      myFixture.configureByFiles(ArrayUtil.mergeArrays(new String[]{getTestFileName() + ".xsl"}, moreFiles));
    }

    @Override
    protected String getSubPath() {
        return "xslt/resolve";
    }
}