package com.intellij.psi.impl.source.xml;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
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

  @NotNull
  public PsiReference[] getReferences() {
    final IElementType elementType = getElementType();

    if (elementType == XmlTokenType.XML_DATA_CHARACTERS ||
        elementType == XmlTokenType.XML_CHAR_ENTITY_REF
      ) {
      return ResolveUtil.getReferencesFromProviders(this, XmlToken.class);
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
