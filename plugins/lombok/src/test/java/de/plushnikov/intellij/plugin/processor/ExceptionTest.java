package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Plushnikov Michail
 */
public class ExceptionTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/augment/exception";
  }

  public void testError526() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());

    PsiFile psiFile = loadToPsiFile(getTestName(false) + ".java");
    assertNotNull(psiFile);

    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiFile.findElementAt(myFixture.getCaretOffset()), PsiClass.class);
    assertNotNull(psiClass);

    // call augmentprovider first time
    final PsiMethod[] psiClassMethods = psiClass.getMethods();
    assertEquals(8, psiClassMethods.length);

    // change something to trigger cache drop
    WriteCommandAction.writeCommandAction(getProject(), psiFile).compute(() ->
      {
        psiClass.getModifierList().addAnnotation("java.lang.SuppressWarnings");
        return true;
      }
    );

    // call augment provider second time
    final PsiMethod[] psiClassMethods2 = psiClass.getMethods();
    assertArrayEquals(psiClassMethods, psiClassMethods2);
  }
}
