package com.intellij.psi.impl.source.xml;

import com.intellij.ant.impl.dom.xmlBridge.AntDOMNSDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlNSDescriptorSequence;
import com.intellij.xml.util.XmlUtil;
import gnu.trove.TObjectIntHashMap;

import java.lang.ref.WeakReference;

/**
 * @author Mike
 */
public class XmlDocumentImpl extends XmlElementImpl implements XmlDocument {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlDocumentImpl");

  private CachedValue myDescriptor = null;

  public XmlDocumentImpl() {
    this(XML_DOCUMENT);
  }

  protected XmlDocumentImpl(IElementType type) {
    super(type);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlDocument(this);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_PROLOG) {
      return ChildRole.XML_PROLOG;
    }
    else if (i == XML_TAG) {
      return ChildRole.XML_TAG;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlProlog getProlog() {
    return (XmlProlog)findElementByTokenType(XML_PROLOG);
  }

  public XmlTag getRootTag() {
    return (XmlTag)findElementByTokenType(XML_TAG);
  }

  public XmlNSDescriptor getRootTagNSDescriptor() {
    XmlTag rootTag = getRootTag();
    return rootTag != null ? rootTag.getNSDescriptor(rootTag.getNamespace(), false) : null;
  }

  public PsiElement copy() {
    final XmlDocumentImpl copy = (XmlDocumentImpl)super.copy();
    copy.myDescriptor = null;

    return copy;
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public void dumpStatistics(){
    System.out.println("Statistics:");
    final TObjectIntHashMap map = new TObjectIntHashMap();

    final PsiRecursiveElementVisitor psiRecursiveElementVisitor = new PsiRecursiveElementVisitor(){
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      public void visitXmlToken(XmlToken token) {
        inc("Tokens");
      }

      public void visitElement(PsiElement element) {
        inc("Elements");
        super.visitElement(element);
      }

      private void inc(final String key) {
        map.put(key, map.get(key) + 1);
      }
    };

    this.accept(psiRecursiveElementVisitor);

    final Object[] keys = map.keys();
    for (int i = 0; i < keys.length; i++) {
      final Object key = keys[i];
      System.out.println(key + ": " + map.get(key));
    }
  }
}
