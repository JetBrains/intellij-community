package com.intellij.psi.impl.source.xml;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IDTDElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

/**
 * @author ik
 */
public class XmlTokenImpl extends LeafPsiElement implements XmlToken, Navigatable {

  public XmlTokenImpl(IElementType type, CharSequence buffer, int startOffset, int endOffset, CharTable table) {
    super(type, buffer, startOffset, endOffset, table);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return false;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlToken(this);
    }
    else {
      visitor.visitElement(this);
    }
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

  @NotNull
  public PsiReference[] getReferences() {
    final IElementType elementType = getElementType();

    if (elementType == XmlTokenType.XML_DATA_CHARACTERS ||
        elementType == XmlTokenType.XML_CHAR_ENTITY_REF
      ) {
      return ReferenceProvidersRegistry.getReferencesFromProviders(this, XmlToken.class);
    } else if (elementType == XmlTokenType.XML_NAME && getParent() instanceof PsiErrorElement) {
      final PsiElement element = getPrevSibling();
      
      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
        return new PsiReference[] { new TagNameReference(getNode(), false)};
      }
    }

    return super.getReferences();
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return getTokenType() == XmlTokenType.XML_NAME && EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }
}
