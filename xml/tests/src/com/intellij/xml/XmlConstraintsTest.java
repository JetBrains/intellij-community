/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.actions.validate.TestErrorReporter;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSElementDecl;
import org.apache.xerces.impl.xs.models.CMBuilder;
import org.apache.xerces.impl.xs.models.CMNodeFactory;
import org.apache.xerces.impl.xs.models.XSCMValidator;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.grammars.XSGrammar;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"UseOfObsoleteCollectionType"})
@TestApplication
public class XmlConstraintsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/xml/tests/testData/constraints");

  @Test
  public void testXercesGrammar() {
    XSModel xsModel = getXSModel("test.xml", "test.xsd");
    XSElementDeclaration elementDeclaration = xsModel.getElementDeclaration("a", "");
    XSComplexTypeDefinition typeDefinition = (XSComplexTypeDefinition)elementDeclaration.getTypeDefinition();
    CMBuilder cmBuilder = new CMBuilder(new CMNodeFactory());
    XSCMValidator validator = cmBuilder.getContentModel((XSComplexTypeDecl)typeDefinition, true);
    int[] ints = validator.startContentModel();
    Vector vector = validator.whatCanGoHere(ints);
    XSElementDecl o = (XSElementDecl)vector.get(0);
    assertEquals("b", o.getName());
  }

  @Test
  public void testXercesIncomplete() {
    XSModel xsModel = getXSModel("testIncomplete.xml", "test.xsd");
    XSElementDeclaration elementDeclaration = xsModel.getElementDeclaration("a", "");
    XSComplexTypeDefinition typeDefinition = (XSComplexTypeDefinition)elementDeclaration.getTypeDefinition();
    CMBuilder cmBuilder = new CMBuilder(new CMNodeFactory());
    XSCMValidator validator = cmBuilder.getContentModel((XSComplexTypeDecl)typeDefinition, true);
    int[] ints = validator.startContentModel();
    Vector vector = validator.whatCanGoHere(ints);
    XSElementDecl o = (XSElementDecl)vector.get(0);
    assertEquals("b", o.getName());
  }

  @Test
  public void testXercesForCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    XSModel xsModel = getXSModel("testCompletion.xml", "test.xsd");
    ReadAction.runBlocking(() -> {
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class);

      assert tag != null;
      XSElementDeclaration elementDeclaration = xsModel.getElementDeclaration(tag.getLocalName(), tag.getNamespace());
      XSComplexTypeDefinition typeDefinition = (XSComplexTypeDefinition)elementDeclaration.getTypeDefinition();
      CMBuilder cmBuilder = new CMBuilder(new CMNodeFactory());
      XSCMValidator validator = cmBuilder.getContentModel((XSComplexTypeDecl)typeDefinition, true);
      int[] ints = validator.startContentModel();
      Vector vector = validator.whatCanGoHere(ints);
      XSElementDecl o = (XSElementDecl)vector.get(0);
      assertEquals("b", o.getName());
    });
  }

  private XSModel getXSModel(String... files) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(files);

    XmlFile file = (XmlFile)myFixture.getFile();
    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(false) {
      @Override
      protected SAXParser createParser() throws SAXException, ParserConfigurationException {
        SAXParser parser = super.createParser();
        parser.getXMLReader().setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.CONTINUE_AFTER_FATAL_ERROR_FEATURE, true);
        return parser;
      }
    };
    handler.setErrorReporter(new TestErrorReporter(handler));
    ReadAction.runBlocking(() -> handler.doValidate(file));
    XMLGrammarPool grammarPool = ValidateXmlActionHandler.getGrammarPool(file);
    assert grammarPool != null;
    Grammar[] grammars = grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA);
    XSGrammar grammar = (XSGrammar)grammars[0];
    return grammar.toXSModel();
  }

  @Test
  public void testXsdConstraints() {
    Map<String, XmlElementDescriptor> map = configure("test.xml", "test.xsd");
    XmlElementDescriptor a = map.get("a");
    XmlElementsGroup topGroup = ReadAction.computeBlocking(() -> a.getTopGroup());
  }

  @Test
  public void testDtdConstraints() {
    Map<String, XmlElementDescriptor> map = configure("testDtd.xml");
    XmlElementDescriptor a = map.get("a");
    XmlElementDescriptor b = map.get("b");

    XmlElementDescriptor c = map.get("c");

    XmlElementDescriptor d = map.get("d");

    XmlElementDescriptor e = map.get("e");
  }

  private Map<String, XmlElementDescriptor> configure(String... files) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(files);
    return ReadAction.computeBlocking(() -> {
      XmlTag tag = ((XmlFile)myFixture.getFile()).getRootTag();
      assertNotNull(tag);
      XmlElementDescriptor descriptor = tag.getDescriptor();
      assertNotNull(descriptor);
      XmlElementDescriptor[] descriptors = descriptor.getElementsDescriptors(tag);
      Map<String, XmlElementDescriptor> map =
        new HashMap<>(ContainerUtil.newMapFromValues(Arrays.asList(descriptors).iterator(), o -> o.getName()));
      map.put(tag.getName(), tag.getDescriptor());
      return map;
    });
  }
}
