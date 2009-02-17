package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public class XsltResolveTest extends TestBase {

    public void testResolveSingleVariableGlobal() throws Throwable {
        doResolveTest(true);
    }

    public void testResolveSingleVariable() throws Throwable {
        doResolveTest(false);
    }

    public void testResolveShadowedVariable() throws Throwable {
        doResolveTest(false);
    }

    public void testResolveSameName() throws Throwable {
        final XsltVariable variable = doResolveTest(false);

        final XsltTemplate template = XsltCodeInsightUtil.getTemplate(variable, false);
        assertNotNull(template);
        assertNotNull(template.getName());
    }

    private XsltVariable doResolveTest(boolean global) throws Throwable {
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