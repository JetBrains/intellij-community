package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltParameter;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public class XsltResolveTest extends TestBase {

    public void testResolveSingleVariableGlobal() throws Throwable {
        doVariableResolveTest(true);
    }

    public void testResolveSingleVariable() throws Throwable {
        doVariableResolveTest(false);
    }

    public void testResolveShadowedVariable() throws Throwable {
        doVariableResolveTest(false);
    }

    public void testResolveSameName() throws Throwable {
        final XsltVariable variable = doVariableResolveTest(false);

        final XsltTemplate template = XsltCodeInsightUtil.getTemplate(variable, false);
        assertNotNull(template);
        assertNotNull(template.getName());
    }

    public void testResolveIncludedTemplateParam() throws Throwable {
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

    private XsltVariable doVariableResolveTest(boolean global) throws Throwable {
        final PsiReference reference = findInjectedReferenceAtCaret();

        final PsiElement element = reference.resolve();
        assertTrue(element instanceof XsltVariable);

        final XsltVariable var = (XsltVariable)element;
        assertEquals(var.getName(), ((XPathVariableReference)reference).getReferencedName());
        assertEquals(global, XsltSupport.isTopLevelElement(var.getTag()));

        return var;
    }

    @NotNull
    private PsiReference findInjectedReferenceAtCaret() throws Throwable {
        configure();

        final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(myFixture.getProject());
        final PsiElement e = manager.findInjectedElementAt(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset());
        assertNotNull(e);

        final PsiReference reference = e.getContainingFile().findReferenceAt(e.getTextOffset());
        assertNotNull(reference);
        return reference;
    }

    private void configure() throws Throwable {
        myFixture.configureByFile(getTestFileName() + ".xsl");
    }

    protected String getSubPath() {
        return "xslt/resolve";
    }
}