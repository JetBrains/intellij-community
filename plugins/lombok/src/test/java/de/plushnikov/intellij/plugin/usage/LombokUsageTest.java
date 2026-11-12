package de.plushnikov.intellij.plugin.usage;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for lombok find usage extension
 */
@TestApplication
public class LombokUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture, "/usage/");

  @Test
  public void testFindUsageGetterSetter() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageGetterSetter.setBar", "findUsageGetterSetter.getBar");
  }

  @Test
  public void testFindUsageGetterSetterWithInterface() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages,
                 "service.setBar",
                 "service.getBar",
                 "impl.setBar",
                 "impl.getBar");
  }

  @Test
  public void testFindUsageAccessors() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageAccessors.setBar", "findUsageAccessors.getBar");
  }

  @Test
  public void testFindUsageWither() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageWither.withBar", "findUsageWither.getBar");
  }

  @Test
  public void testFindUsageWitherRecord() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageWitherRecord.withBar", "findUsageWitherRecord.bar");
  }

  @Test
  public void testFindUsageBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageBuilder.builder().bar", "findUsageBuilder.getBar");
  }

  @Test
  public void testFindUsageBuilderRecord() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageBuilderRecord.builder().bar", "findUsageBuilderRecord.bar");
  }

  @Test
  public void testFindUsageSingularBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageSingularBuilder.builder().bar", "FindUsageSingularBuilder.builder().bars",
      "FindUsageSingularBuilder.builder().clearBars", "findUsageBuilder.getBars");
  }

  @Test
  public void testFindUsageDelegateField() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "ctx.getId");
  }

  @Test
  public void testFindUsageDelegateMethod() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "classA.foo");
  }

  @Test
  public void testFieldUsages() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiClass aClass = myFixture.findClass("Apple");
      JavaClassFindUsagesOptions options = new JavaClassFindUsagesOptions(myFixture.getProject());
      options.isFieldsUsages=true;
      options.isMethodsUsages=true;
      options.isUsages=true;
      CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
      FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(aClass.getProject())).getFindUsagesManager();
      FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(aClass, false);
      assertNotNull(handler);
      handler.processElementUsages(aClass, processor, options);

      Collection<UsageInfo> usages = processor.getResults();

      assertTrue(usages.isEmpty(), StringUtil.join(usages, u -> u.getElement().getText() , ","));
    });
  }

  private static void assertUsages(Collection<UsageInfo> usages, String... usageTexts) {
    ReadAction.runBlocking(() -> {
      assertEquals(usageTexts.length, usages.size());
      List<UsageInfo> sortedUsages = new ArrayList<>(usages);
      sortedUsages.sort(UsageInfo::compareToByStartOffset);
      for (int i = 0; i < usageTexts.length; i++) {
        assertEquals(usageTexts[i], sortedUsages.get(i).getElement().getText().replaceAll("\\s*", ""));
      }
    });
  }

  @NotNull
  private Collection<UsageInfo> loadTestClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return myFixture.testFindUsages(myFixture.getTestName(false) + ".java");
  }
}
