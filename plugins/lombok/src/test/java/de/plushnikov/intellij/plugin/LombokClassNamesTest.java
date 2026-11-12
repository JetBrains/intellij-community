package de.plushnikov.intellij.plugin;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LombokClassNamesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testAllNames() throws IllegalAccessException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GlobalSearchScope allScope = GlobalSearchScope.allScope(myFixture.getProject());
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(myFixture.getProject());
    for (Field field : LombokClassNames.class.getDeclaredFields()) {
      if (String.class.equals(field.getType())) {
        String name = (String)field.get(null);
        assertNotNull(ReadAction.computeBlocking(() -> psiFacade.findClass(name, allScope)), name);
      }
    }
  }
}
