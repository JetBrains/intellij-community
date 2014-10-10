/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.arrangement

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens
import org.jetbrains.annotations.NotNull

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME

/**
 * @author Eugene.Kudelevsky
 */
class XmlRearrangerTest extends AbstractRearrangerTest {
  XmlRearrangerTest() {
    fileType = XmlFileType.INSTANCE
    language = XMLLanguage.INSTANCE
  }

  void testAttributeSorting1() {
    doTest(
      initial: '''<root xmlns:ns="http://ns.com" attr2="value2" attr1="value1" attr3="value3"/>''',
      expected: '''<root attr1="value1" attr2="value2" attr3="value3" xmlns:ns="http://ns.com"/>''',
      rules: [ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  void testAttributeSorting2() {
    doTest(
      initial: '''<root attr3="value3" attr2="value2" attr1="value1" xmlns:ns="http://ns.com"/>''',
      expected: '''<root attr1="value1" attr2="value2" attr3="value3" xmlns:ns="http://ns.com"/>''',
      rules: [ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  void testAttributeSorting3() {
    doTest(
      initial: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr2="value2" attr1="value1">
  <tag1 attr2="value2" attr1="value1">
      <tag2 attr1="value1" attr2="value2"/>
      <tag3 attr2="value2" attr1="value1" xmlns:ns3="http://ns.com"/>
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" attr2="value2">
  <tag1 attr1="value1" attr2="value2">
      <tag2 attr1="value1" attr2="value2"/>
      <tag3 xmlns:ns3="http://ns.com" attr1="value1" attr2="value2"/>
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  public void testAttributeSorting4() throws Exception {
    doTest(
      initial: '''<root attr3="value3" attr2="value2" attr1="value1" xmlns:ns="http://ns.com"/>''',
      expected: '''<root xmlns:ns="http://ns.com" attr1="value1" attr2="value2" attr3="value3"/>''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  public void testAttributeSortingByNamespace1() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" ns1:attr1="value1" ns1:attr2="value2">
  <tag1 attr1="value1" attr2="value2" ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule(".*"))]
    )
  }

  public void testAttributeSortingByNamespace2() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
  <tag1 ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2" attr2="value2" attr1="value1">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns.*"))]
    )
  }

  public void testAttributeSortingByNamespace3() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
  <tag1 ns1:attr1="value1" ns1:attr2="value2" ns2:attr1="value1" ns2:attr2="value2" attr1="value1" attr2="value2">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns.*")),
        ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  public void testAttributeSortingByNamespace4() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" attr1="value1" ns1:attr1="value1" ns1:attr2="value2">
  <tag1 ns2:attr1="value1" ns2:attr2="value2" attr1="value1" attr2="value2" ns1:attr1="value1" ns1:attr2="value2">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns2.com")),
        ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  public void testAttributeSortingByNamespace5() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1" ns1:attr2="value2" attr1="value1">
  <tag1 ns2:attr1="value1" ns2:attr2="value2" ns1:attr1="value1" ns1:attr2="value2" attr2="value2" attr1="value1">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, namespaceRule("http://ns2.com")),
        ruleWithOrder(BY_NAME, namespaceRule("http://ns1.com"))]
    )
  }

  public void testAttributeSortingByNamespace6() throws Exception {
    doTest(
      initial: '''\
<root attr1="value1" ns1:attr2="value2" xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr1="value1">
  <tag1 ns2:attr2="value2" attr2="value2" attr1="value1" ns2:attr1="value1" ns1:attr2="value2" ns1:attr1="value1">
  </tag1>
</root>
''',
      expected: '''\
<root xmlns:ns1="http://ns1.com" xmlns:ns2="http://ns2.com" ns1:attr2="value2" attr1="value1" ns1:attr1="value1">
  <tag1 ns1:attr2="value2" ns2:attr2="value2" attr1="value1" attr2="value2" ns1:attr1="value1" ns2:attr1="value1">
  </tag1>
</root>
''',
      rules: [ruleWithOrder(BY_NAME, nameRule("xmlns:.*")), ruleWithOrder(BY_NAME, compositeRule(".*:attr2", "http://ns.*")),
        ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  @NotNull
  private static StdArrangementMatchRule namespaceRule(@NotNull String filter) {
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(
      new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, filter)))
  }

  @NotNull
  protected static StdArrangementMatchRule compositeRule(@NotNull String nameFilter, @NotNull String namespaceFilter) {
    return rule(new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter),
                new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.XML_NAMESPACE, namespaceFilter));
  }
}
