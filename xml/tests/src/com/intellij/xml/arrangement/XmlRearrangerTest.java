package com.intellij.xml.arrangement;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.arrangement.RearrangerTestUtil;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.psi.codeStyle.arrangement.RearrangerTestUtil.nameRule;
import static com.intellij.psi.codeStyle.arrangement.RearrangerTestUtil.ruleWithOrder;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class XmlRearrangerTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void doTest(@NotNull Map<String, ?> args) {
    RearrangerTestUtil.doTest(codeInsightFixture.get(), XmlFileType.INSTANCE, XMLLanguage.INSTANCE, args);
  }

  @Test
  public void testAttributeSorting1() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root xmlns:ns="http://ns.com" attr2="value2" attr1="value1" attr3="value3"/>""");
    map.put("expected", """
      <root attr1="value1" attr2="value2" attr3="value3" xmlns:ns="http://ns.com"/>""");
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSorting2() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr3="value3" attr2="value2" attr1="value1" xmlns:ns="http://ns.com"/>""");
    map.put("expected", """
      <root attr1="value1" attr2="value2" attr3="value3" xmlns:ns="http://ns.com"/>""");
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSorting3() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr2="value2" attr1="value1">
        <tag1 attr2="value2" attr1="value1">
            <tag2 attr1="value1" attr2="value2"/>
            <tag3 attr2="value2" attr1="value1" xmlns:ns3="http://ns.com"/>
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" attr2="value2">
        <tag1 attr1="value1" attr2="value2">
            <tag2 attr1="value1" attr2="value2"/>
            <tag3 xmlns:ns3="http://ns.com" attr1="value1" attr2="value2"/>
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSorting4() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr3="value3" attr2="value2" attr1="value1" xmlns:ns="http://ns.com"/>""");
    map.put("expected", """
      <root xmlns:ns="http://ns.com" attr1="value1" attr2="value2" attr3="value3"/>""");
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace1() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" ns1:attr1="value1" ns1:attr2="value2">
        <tag1 attr1="value1" attr2="value2" ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace2() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
        <tag1 ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2" attr2="value2" attr1="value1">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns.*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace3() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
        <tag1 ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2" attr1="value1" attr2="value2">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns.*")),
                    ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace4() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" ns1:attr1="value1" ns1:attr2="value2">
        <tag1 ns2:attr1="value1" ns2:attr2="value2" attr1="value1" attr2="value2" ns1:attr1="value1" ns1:attr2="value2">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns2.com")),
                    ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace5() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
        <tag1 ns2:attr1="value1" ns2:attr2="value2" ns1:attr1="value1" ns1:attr2="value2" attr2="value2" attr1="value1">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")),
                    ruleWithOrder(BY_NAME, namespaceRule("http://ns2.com")),
                    ruleWithOrder(BY_NAME, namespaceRule("http://ns1.com")))));
    doTest(map);
  }

  @Test
  public void testAttributeSortingByNamespace6() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
        <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("expected", """
      <root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr2="value2" attr1="value1" ns1:attr1="value1">
        <tag1 ns1:attr2="value2" ns2:attr2="value2" attr1="value1" attr2="value2" ns1:attr1="value1" ns2:attr1="value1">
        </tag1>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, compositeRule(".*:attr2", "http://ns.*")),
                    ruleWithOrder(BY_NAME, nameRule(".*")))));
    doTest(map);
  }

  @Test
  public void testTagSorting() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root>
        <meta></meta>
        <title></title>
      </root>
      """);
    map.put("expected", """
      <root>
        <title></title>
        <meta></meta>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("title")))));
    doTest(map);
  }

  @Test
  public void testTagAndAttrSorting() {
    LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(3);
    map.put("initial", """
      <root beta="">
        <meta></meta>
        <title></title>
      </root>
      """);
    map.put("expected", """
      <root beta="">
        <title></title>
        <meta></meta>
      </root>
      """);
    map.put("rules", new ArrayList<>(
      Arrays.asList(ruleWithOrder(BY_NAME, nameRule("title")))));
    doTest(map);
  }

  @NotNull
  private static StdArrangementMatchRule namespaceRule(@NotNull String filter) {
    return new StdArrangementMatchRule(
      new StdArrangementEntryMatcher(new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, filter)));
  }

  @NotNull
  protected static StdArrangementMatchRule compositeRule(@NotNull String nameFilter, @NotNull String namespaceFilter) {
    return RearrangerTestUtil.rule(new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter),
                                   new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, namespaceFilter));
  }
}
