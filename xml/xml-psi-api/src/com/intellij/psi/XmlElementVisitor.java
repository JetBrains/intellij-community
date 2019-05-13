/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.xml.*;

public abstract class XmlElementVisitor extends PsiElementVisitor {
  public void visitXmlElement(XmlElement element) {
    visitElement(element);
  }

  public void visitXmlFile(XmlFile file) {
    visitFile(file);
  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    visitXmlElement(attribute);
  }

  public void visitXmlComment(XmlComment comment) {
    visitXmlElement(comment);
  }

  public void visitXmlDecl(XmlDecl decl) {
    visitXmlElement(decl);
  }                                    

  public void visitXmlDocument(XmlDocument document) {
    visitXmlElement(document);
  }

  public void visitXmlProlog(XmlProlog prolog) {
    visitXmlElement(prolog);
  }

  public void visitXmlText(XmlText text) {
    visitXmlElement(text);
  }

  public void visitXmlTag(XmlTag tag) {
    visitXmlElement(tag);
  }

  public void visitXmlToken(XmlToken token) {
    visitXmlElement(token);
  }

  public void visitXmlAttributeValue(XmlAttributeValue value) {
    visitXmlElement(value);
  }

  public void visitXmlDoctype(XmlDoctype xmlDoctype) {
    visitXmlElement(xmlDoctype);
  }

  public void visitXmlProcessingInstruction(XmlProcessingInstruction processingInstruction) {
    visitXmlElement(processingInstruction);
  }
}
