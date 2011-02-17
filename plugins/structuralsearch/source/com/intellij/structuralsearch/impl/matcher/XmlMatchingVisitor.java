package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.*;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.ArrayBackedNodeIterator;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class XmlMatchingVisitor extends XmlElementVisitor {
  private final GlobalMatchingVisitor myMatchingVisitor;

  public XmlMatchingVisitor(GlobalMatchingVisitor matchingVisitor) {
    myMatchingVisitor = matchingVisitor;
  }

  @Override
  public void visitElement(final PsiElement element) {
    myMatchingVisitor.setResult(element.textMatches(element));
  }

  @Override public void visitXmlAttribute(XmlAttribute attribute) {
    final XmlAttribute another = (XmlAttribute)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(attribute.getName());

    myMatchingVisitor.setResult(attribute.getName().equals(another.getName()) || isTypedVar);
    if (myMatchingVisitor.getResult()) {
      myMatchingVisitor.setResult(myMatchingVisitor.match(attribute.getValueElement(), another.getValueElement()));
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler(attribute.getName());
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(another, myMatchingVisitor.getMatchContext()));
    }
  }

  @Override public void visitXmlAttributeValue(XmlAttributeValue value) {
    final XmlAttributeValue another = (XmlAttributeValue)myMatchingVisitor.getElement();
    final String text = value.getValue();

    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text);
    MatchingHandler handler;

    if (isTypedVar && (handler = myMatchingVisitor.getMatchContext().getPattern().getHandler( text )) instanceof SubstitutionHandler) {
      String text2 = another.getText();
      int offset = text2.length() > 0 && ( text2.charAt(0) == '"' || text2.charAt(0) == '\'') ? 1:0;
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(another, offset, text2.length() - offset,
                                                                        myMatchingVisitor.getMatchContext()));
    } else {
      myMatchingVisitor.setResult(text.equals(another.getValue()));
    }
  }

  @Override public void visitXmlTag(XmlTag tag) {
    final XmlTag another = (XmlTag)myMatchingVisitor.getElement();
    final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(tag.getName());

    myMatchingVisitor.setResult((tag.getName().equals(another.getName()) || isTypedVar) &&
                                myMatchingVisitor.matchInAnyOrder(tag.getAttributes(), another.getAttributes()));

    if(myMatchingVisitor.getResult()) {
      final XmlTagChild[] contentChildren = tag.getValue().getChildren();

      if (contentChildren.length > 0) {
        PsiElement[] patternNodes = contentChildren;
        PsiElement[] matchedNodes = another.getValue().getChildren();

        if (contentChildren.length != 1) {
          patternNodes = expandXmlTexts(patternNodes);
          matchedNodes = expandXmlTexts(matchedNodes);
        }

        myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(
          new ArrayBackedNodeIterator(patternNodes),
          new ArrayBackedNodeIterator(matchedNodes)
        ));
      }
    }

    if (myMatchingVisitor.getResult() && isTypedVar) {
      MatchingHandler handler = myMatchingVisitor.getMatchContext().getPattern().getHandler( tag.getName() );
      myMatchingVisitor.setResult(((SubstitutionHandler)handler).handle(another, myMatchingVisitor.getMatchContext()));
    }
  }

  private static PsiElement[] expandXmlTexts(PsiElement[] children) {
    List<PsiElement> result = new ArrayList<PsiElement>(children.length);
    for(PsiElement c:children) {
      if (c instanceof XmlText) {
        for(PsiElement p:c.getChildren()) {
          if (!(p instanceof PsiWhiteSpace)) result.add(p);
        }
      } else if (!(c instanceof PsiWhiteSpace)) {
        result.add(c);
      }
    }
    return PsiUtilBase.toPsiElementArray(result);
  }

  @Override public void visitXmlText(XmlText text) {
    myMatchingVisitor.setResult(myMatchingVisitor.matchSequentially(text.getFirstChild(), myMatchingVisitor.getElement().getFirstChild()));
  }

  @Override public void visitXmlToken(XmlToken token) {
    if (token.getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
      String text = token.getText();
      final boolean isTypedVar = myMatchingVisitor.getMatchContext().getPattern().isTypedVar(text);

      if (isTypedVar) {
        myMatchingVisitor.setResult(myMatchingVisitor.handleTypedElement(token, myMatchingVisitor.getElement()));
      } else {
        myMatchingVisitor.setResult(text.equals(myMatchingVisitor.getElement().getText()));
      }
    }
  }
}
