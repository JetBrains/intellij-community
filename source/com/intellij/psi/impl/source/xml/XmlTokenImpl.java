package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IDTDElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.xml.util.XmlUtil;

/**
 * @author ik
 */
public class XmlTokenImpl extends LeafPsiElement implements XmlToken {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlTokenImpl");

  public XmlTokenImpl(IElementType type, char[] buffer, int startOffset, int endOffset, int lexerState, CharTable table) {
    super(type, buffer, startOffset, endOffset, lexerState, table);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlToken(this);
  }

  public String toString() {
    if(getTokenType() instanceof IDTDElementType){
      return "DTDToken:" + getTokenType().toString();
    }
    return "XmlToken:" + getTokenType().toString();
  }

// Implementation specific

  public IElementType getTokenType() {
    return getElementType();
  }

  public PsiReference[] getReferences() {
    if (getElementType() == XmlTokenType.XML_DATA_CHARACTERS) {
      return ResolveUtil.getReferencesFromProviders(this);
    } else {
      return super.getReferences();
    }
  }
}
