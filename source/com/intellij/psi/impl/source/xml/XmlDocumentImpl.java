package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.pom.xml.impl.events.XmlDocumentChangedImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNSDescriptor;
import gnu.trove.TObjectIntHashMap;

/**
 * @author Mike
 */
public class XmlDocumentImpl extends XmlElementImpl implements XmlDocument {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlDocumentImpl");

  public XmlDocumentImpl() {
    this(XML_DOCUMENT);
  }

  protected XmlDocumentImpl(IElementType type) {
    super(type);
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlDocument(this);
  }

  public int getChildRole(ASTNode child) {
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

  public TreeElement addInternal(final TreeElement first, final ASTNode last, final ASTNode anchor, final Boolean before) {
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    final TreeElement[] holder = new TreeElement[1];
    try{
      model.runTransaction(new PomTransactionBase(this) {
        public PomModelEvent run() throws IncorrectOperationException {
          holder[0] = XmlDocumentImpl.super.addInternal(first, last, anchor, before);
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      }, aspect);
    }
    catch(IncorrectOperationException e){}
    return holder[0];
  }

  public void deleteChildInternal(final ASTNode child) {
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    try{
      model.runTransaction(new PomTransactionBase(this) {
        public PomModelEvent run() throws IncorrectOperationException {
          XmlDocumentImpl.super.deleteChildInternal(child);
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      }, aspect);
    }
    catch(IncorrectOperationException e){}
  }

  public void replaceChildInternal(final ASTNode child, final TreeElement newElement) {
    final PomModel model = getProject().getModel();
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    try{
      model.runTransaction(new PomTransactionBase(this) {
        public PomModelEvent run() throws IncorrectOperationException {
          XmlDocumentImpl.super.replaceChildInternal(child, newElement); 
          return XmlDocumentChangedImpl.createXmlDocumentChanged(model, XmlDocumentImpl.this);
        }
      }, aspect);
    }
    catch(IncorrectOperationException e){}
  }
}
