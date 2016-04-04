/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.validation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.Attributes2Impl;
import org.xml.sax.ext.Locator2Impl;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 30.07.2007
*/
class Psi2SaxAdapter extends XmlElementVisitor implements PsiElementProcessor<PsiElement> {
  private final ContentHandler myHandler;

  public Psi2SaxAdapter(ContentHandler handler) {
    myHandler = handler;
  }

  @Override
  public void visitXmlElement(XmlElement element) {
    if (element instanceof XmlEntityRef) {
      XmlUtil.processXmlElements(element, this, false, true);
    }
    super.visitXmlElement(element);
  }

  @Override
  public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      handleText(token, token.getText());
    }
  }

  private void handleText(XmlElement element, String text) {
    try {
      setLocation(element);
      myHandler.characters(text.toCharArray(), 0, text.length());
    } catch (SAXException e) {
      throw new ParseError(e);
    }
  }

  @Override
  public boolean execute(@NotNull PsiElement element) {
    element.accept(this);
    return true;
  }

  @Override
  public void visitXmlDocument(XmlDocument document) {
    try {
      myHandler.startDocument();
      final XmlTag rootTag = document.getRootTag();
      if (rootTag != null) {
        rootTag.accept(this);
      }
      myHandler.endDocument();
    } catch (SAXException e) {
      throw new ParseError(e);
    }
  }

  @Override
  public void visitXmlTag(XmlTag tag) {
    try {
      setLocation(tag);

      final Map<String,String> map = tag.getLocalNamespaceDeclarations();
      final String[] prefixes = map.keySet().toArray(new String[map.size()]);
      for (String prefix : prefixes) {
        myHandler.startPrefixMapping(prefix, map.get(prefix));
      }

      final Attributes2Impl atts = new Attributes2Impl();
      final XmlAttribute[] xmlAttributes = tag.getAttributes();
      for (XmlAttribute attribute : xmlAttributes) {
        final String s = attribute.getName();
        if (!"xmlns".equals(s) && !s.startsWith("xmlns:")) {
          final String uri = attribute.getNamespace();
          atts.addAttribute(s.contains(":") ? uri : "", attribute.getLocalName(), s, "PCDATA", attribute.getValue());
        }
      }

      final String namespace = tag.getNamespace();
      final String localName = tag.getLocalName();
      final String name = tag.getName();
      myHandler.startElement(namespace, localName, name, atts);

      PsiElement child = tag.getFirstChild();
      while (child != null) {
        child.accept(this);
        child = child.getNextSibling();
      }

      myHandler.endElement(namespace, localName, name);

      for (int i = prefixes.length - 1; i >= 0; i--) {
        String prefix = prefixes[i];
        myHandler.endPrefixMapping(prefix);
      }
    } catch (SAXException e) {
      throw new ParseError(e);
    }
  }

  @Override
  public void visitXmlText(XmlText text) {
    handleText(text, text.getValue());
  }

  private void setLocation(PsiElement text) {
    final PsiFile psiFile = text.getContainingFile();
    final Document document = PsiDocumentManager.getInstance(text.getProject()).getDocument(psiFile);
    if (document == null) {
      return;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    final Locator2Impl locator = new Locator2Impl();
    locator.setSystemId(VfsUtilCore.fixIDEAUrl(virtualFile.getUrl()));

    final int offset = text.getTextRange().getEndOffset();
    final int lineNumber = document.getLineNumber(offset);

    locator.setLineNumber(lineNumber + 1);
    locator.setColumnNumber(1 + offset - document.getLineStartOffset(lineNumber));

    myHandler.setDocumentLocator(locator);
  }

  public static class ParseError extends RuntimeException {
    public ParseError(SAXException e) {
      super(e);
    }
  }
}
