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

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
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
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class XmlConstraintsTest extends LightCodeInsightFixtureTestCase {

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

  public void testXercesForCompletion() {
    XSModel xsModel = getXSModel("testCompletion.xml", "test.xsd");
    PsiElement element = myFixture.getFile().findElementAt(getEditor().getCaretModel().getOffset());
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
  }

  private XSModel getXSModel(String... files) {
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
    handler.doValidate(file);
    XMLGrammarPool grammarPool = ValidateXmlActionHandler.getGrammarPool(file);
    assert grammarPool != null;
    Grammar[] grammars = grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA);
    XSGrammar grammar = (XSGrammar)grammars[0];
    return grammar.toXSModel();
  }

  public void testXsdConstraints() {
    Map<String, XmlElementDescriptor> map = configure("test.xml", "test.xsd");
    XmlElementDescriptor a = map.get("a");
    XmlElementsGroup topGroup = a.getTopGroup();
  }

  public void testDtdConstraints() {

    Map<String, XmlElementDescriptor> map = configure("testDtd.xml");
    XmlElementDescriptor a = map.get("a");
    XmlElementDescriptor b = map.get("b");

    XmlElementDescriptor c = map.get("c");

    XmlElementDescriptor d = map.get("d");

    XmlElementDescriptor e = map.get("e");
  }

  private Map<String, XmlElementDescriptor> configure(String... files) {
    myFixture.configureByFiles(files);
    XmlTag tag = ((XmlFile)getFile()).getRootTag();
    assertNotNull(tag);
    XmlElementDescriptor descriptor = tag.getDescriptor();
    assertNotNull(descriptor);
    XmlElementDescriptor[] descriptors = descriptor.getElementsDescriptors(tag);
    Map<String, XmlElementDescriptor> map =
      ContainerUtil.newMapFromValues(Arrays.asList(descriptors).iterator(), o -> o.getName());
    map.put(tag.getName(), tag.getDescriptor());
    return map;
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/constraints";
  }
}
