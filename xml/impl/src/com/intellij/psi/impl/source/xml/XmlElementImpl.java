/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 26, 2002
 * Time: 6:25:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

public abstract class XmlElementImpl extends CompositePsiElement implements XmlElement {
  public XmlElementImpl(IElementType type) {
    super(type);
  }

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    return XmlUtil.processXmlElements(this, processor, false);
  }

  public XmlElement findElementByTokenType(final IElementType type){
    final XmlElement[] result = new XmlElement[1];
    result[0] = null;

    processElements(new PsiElementProcessor(){
      public boolean execute(PsiElement element){
        if(element instanceof TreeElement && ((ASTNode)element).getElementType() == type){
          result[0] = (XmlElement)element;
          return false;
        }
        return true;
      }
    }, this);

    return result[0];
  }

  public PsiElement getContext() {
    final XmlElement data = getUserData(ORIGINAL_ELEMENT);
    if(data != null) return data;
    return super.getParent();
  }

  public PsiElement getParent(){
    return getContext();
  }

  public TextRange getTextRange() {
    final int textOffset = getStartOffset();
    return new TextRange(textOffset, textOffset + getTextLength());
  }

  @NotNull
  public Language getLanguage() {
    final FileType fileType = getContainingFile().getFileType();
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : StdLanguages.XML;
  }

  protected static String getNameFromEntityRef(final CompositeElement compositeElement, final IElementType xmlEntityDeclStart) {
    final ASTNode node = compositeElement.findChildByType(xmlEntityDeclStart);
    ASTNode name = node.getTreeNext();

    if (name != null && name.getElementType() == WHITE_SPACE) {
      name = name.getTreeNext();
    }

    if (name != null && name.getElementType() == XmlElementType.XML_ENTITY_REF) {
      final StringBuilder builder = new StringBuilder();

      ((XmlElement)name.getPsi()).processElements(new PsiElementProcessor() {
        public boolean execute(final PsiElement element) {
          builder.append(element.getText());
          return true;
        }
      }, name.getPsi());
      if (builder.length() > 0) return builder.toString();
    }
    return null;
  }
}
