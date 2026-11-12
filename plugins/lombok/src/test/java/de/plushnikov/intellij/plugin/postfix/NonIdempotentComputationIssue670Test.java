package de.plushnikov.intellij.plugin.postfix;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@TestApplication
public class NonIdempotentComputationIssue670Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testIssue670() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("platform.random.idempotence.check.rate").setValue(1, myFixture.getTestRootDisposable());

    myFixture.configureByFile("/postfix/issue670/" + myFixture.getTestName(false) + ".java");

    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiMethod[] firstCallGetMethods = myFixture.findClass("Issue670").getMethods();
      assertThat(Arrays.asList(firstCallGetMethods)).isNotEmpty();

      // change something in the file
      myFixture.type('0');
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

      final PsiMethod[] secondCallGetMethods = myFixture.findClass("Issue670").getMethods();
      assertThat(Arrays.asList(secondCallGetMethods)).isNotEmpty();

      // all methods should be in same order
      assertArrayEquals(firstCallGetMethods, secondCallGetMethods);
    });
  }
}
