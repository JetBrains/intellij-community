/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.WriteAction

/**
 * @author mike
 */
class XmlXIncludeTest extends LightCodeInsightFixtureTestCase {
  private static final String BASE_PATH = "/psi/xi"

  void testBasicXi() throws Exception {
    configure()
    assertSubtagNames("before", "include1", "after")
  }

  void testXpointerXi1() throws Exception {
    configure()
    assertSubtagNames("before", "foo", "bar", "after")
  }

  void testXmlBase() throws Exception {
    myFixture.copyDirectoryToProject("xmlBase", "xmlBase")
    myFixture.enableInspections(XmlPathReferenceInspection.class)
    myFixture.testHighlighting("xmlBase/XmlBase.xml")
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/" + BASE_PATH
  }

  private void assertSubtagNames(String... expectedNames) {
    final XmlTag tag = ((XmlFile)myFixture.file).document.rootTag
    final List<String> names = tag.getSubTags().collect { it.name }

    assertEquals(
      "subtags are different",
      StringUtil.join(expectedNames, "\n"), StringUtil.join(names, "\n")
    )
  }

  private void configure() throws Exception {
    myFixture.configureByFiles(getName() + ".xml", "/include1.xml")
  }

  void testModifyingIncludedFile() {
    myFixture.configureByText 'a.xml', '''
<root xmlns:xi="http://www.w3.org/2001/XInclude">
  <before/>
  <xi:include href="include1.xml" xpointer="xpointer(/include1/*)"/>
  <after/>
</root>
'''
    def inc1 = myFixture.copyFileToProject("include1.xml")
    assertSubtagNames("before", "foo", "bar", "after")

    changeText inc1, '<include1><a/></include1>'
    
    assertSubtagNames("before", "a", "after")
  }

  void testModifyingTransitivelyIncludedFile() {
    myFixture.configureByText 'a.xml', '''
<root xmlns:xi="http://www.w3.org/2001/XInclude">
  <before/>
  <xi:include href="include1.xml" xpointer="xpointer(/include1/*)"/>
  <after/>
</root>
'''
    myFixture.addFileToProject("include1.xml", """
<include1 xmlns:xi="http://www.w3.org/2001/XInclude">
  <foo attr1="val1"> <fooChild/> </foo>
  <bar/>
  <xi:include href="include2.xml""/>
</include1>
""")
    def inc2 = myFixture.addFileToProject("include2.xml", '<a/>')

    assertSubtagNames("before", "foo", "bar", "a", "after")

    changeText inc2.virtualFile, '<b/>'

    assertSubtagNames("before", "foo", "bar", "b", "after")

  }

  private changeText(VirtualFile inc1, String s) {
    def token = WriteAction.start()
    try {
      FileDocumentManager.instance.getDocument(inc1).setText s
      PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    finally {
      token.finish()
    }
  }
}
