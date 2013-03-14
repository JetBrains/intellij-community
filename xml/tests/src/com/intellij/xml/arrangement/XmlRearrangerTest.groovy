package com.intellij.xml.arrangement
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest

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
      expected: '''<root xmlns:ns="http://ns.com" attr1="value1" attr2="value2" attr3="value3"/>''',
      rules: [ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }

  void testAttributeSorting2() {
    doTest(
      initial: '''<root attr3="value3" attr2="value2" attr1="value1" xmlns:ns="http://ns.com"/>''',
      expected: '''<root xmlns:ns="http://ns.com" attr1="value1" attr2="value2" attr3="value3"/>''',
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
      rules: [ruleWithOrder(BY_NAME, nameRule(".*"))]
    )
  }
}
