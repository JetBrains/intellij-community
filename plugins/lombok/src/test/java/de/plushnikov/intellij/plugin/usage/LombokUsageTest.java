package de.plushnikov.intellij.plugin.usage;

import com.intellij.usageView.UsageInfo;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

  public void testFindUsageBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageBuilder.builder().bar", "findUsageBuilder.getBar");
  }

  public void testFindUsageSingularBuilder() {
    final Collection<UsageInfo> usages = loadTestClass();
    assertUsages(usages, "FindUsageSingularBuilder.builder().bar", "FindUsageSingularBuilder.builder().bars",
      "FindUsageSingularBuilder.builder().clearBars", "findUsageBuilder.getBars");
  }

  private void assertUsages(Collection<UsageInfo> usages, String... usageTexts) {
    assertEquals(usageTexts.length, usages.size());
    List<UsageInfo> sortedUsages = new ArrayList<UsageInfo>(usages);
    Collections.sort(sortedUsages, UsageInfo::compareToByStartOffset);
    for (int i = 0; i < usageTexts.length; i++) {
      assertEquals(usageTexts[i], sortedUsages.get(i).getElement().getText().replaceAll("\\s*", ""));
    }
  }

  @NotNull
  private Collection<UsageInfo> loadTestClass() {
    return myFixture.testFindUsages(getBasePath() + getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/usage/";
  }
}
