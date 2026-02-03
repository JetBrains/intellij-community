package com.intellij.xml.arrangement;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;

public class XmlRearrangerTest extends AbstractRearrangerTest {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    fileType = XmlFileType.INSTANCE;
    language = XMLLanguage.INSTANCE;
  }

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
    return AbstractRearrangerTest.rule(new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter),
                                       new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, namespaceFilter));
  }
}
