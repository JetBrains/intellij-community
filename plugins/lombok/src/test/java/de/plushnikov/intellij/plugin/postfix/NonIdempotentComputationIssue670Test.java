package de.plushnikov.intellij.plugin.postfix;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

public class NonIdempotentComputationIssue670Test extends AbstractLombokLightCodeInsightTestCase {

  public void testIssue670() {
    Registry.get("platform.random.idempotence.check.rate").setValue(1, getTestRootDisposable());

    myFixture.configureByFile("/postfix/issue670/" + getTestName(false) + ".java");

    final PsiMethod[] firstCallGetMethods = myFixture.findClass("Issue670").getMethods();
    assertNotEmpty(Arrays.asList(firstCallGetMethods));

    // change something in the file
    myFixture.type('0');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final PsiMethod[] secondCallGetMethods = myFixture.findClass("Issue670").getMethods();
    assertNotEmpty(Arrays.asList(secondCallGetMethods));

    // all methods should be in same order
    assertArrayEquals(firstCallGetMethods, secondCallGetMethods);
  }
}
