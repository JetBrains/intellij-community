package de.plushnikov.intellij.plugin.usage;

import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonProcessors;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Test for lombok find usage extension
 */
public class LombokUsageTest extends AbstractLombokLightCodeInsightTestCase {

  public void testFindUsageGetterSetter() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageGetterSetter.setBar", "findUsageGetterSetter.getBar");
  }

  public void testFindUsageAccessors() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageAccessors.setBar", "findUsageAccessors.getBar");
  }

  public void testFindUsageWither() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageWither.withBar", "findUsageWither.getBar");
  }

  public void testFindUsageWitherRecord() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "findUsageWitherRecord.withBar", "findUsageWitherRecord.bar");
  }

  public void testFindUsageBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageBuilder.builder().bar", "findUsageBuilder.getBar");
  }

  public void testFindUsageBuilderRecord() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageBuilderRecord.builder().bar", "findUsageBuilderRecord.bar");
  }

  public void testFindUsageSingularBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageSingularBuilder.builder().bar", "FindUsageSingularBuilder.builder().bars",
      "FindUsageSingularBuilder.builder().clearBars", "findUsageBuilder.getBars");
  }

  public void testFindUsageDelegateField() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "ctx.getId");
  }

  public void testFindUsageDelegateMethod() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "classA.foo");
  }

  public void testFieldUsages() {
    myFixture.configureByFile(getTestName(false) + ".java");
    PsiClass aClass = myFixture.findClass("Apple");
    JavaClassFindUsagesOptions options = new JavaClassFindUsagesOptions(getProject());
    options.isFieldsUsages=true;
    options.isMethodsUsages=true;
    options.isUsages=true;
    CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<>();
    FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(aClass.getProject())).getFindUsagesManager();
    FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(aClass, false);
    assertNotNull(handler);
    handler.processElementUsages(aClass, processor, options);

    Collection<UsageInfo> usages = processor.getResults();

    assertEmpty(StringUtil.join(usages, u -> u.getElement().getText() , ","), usages);
  }

  private static void assertUsages(Collection<UsageInfo> usages, String... usageTexts) {
    assertEquals(usageTexts.length, usages.size());
    List<UsageInfo> sortedUsages = new ArrayList<>(usages);
    sortedUsages.sort(UsageInfo::compareToByStartOffset);
    for (int i = 0; i < usageTexts.length; i++) {
      assertEquals(usageTexts[i], sortedUsages.get(i).getElement().getText().replaceAll("\\s*", ""));
    }
  }

  @NotNull
  private Collection<UsageInfo> loadTestClass() {
    return myFixture.testFindUsages(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/usage/";
  }
}
