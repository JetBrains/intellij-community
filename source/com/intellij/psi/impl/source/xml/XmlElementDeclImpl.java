package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Mike
 */
public class XmlElementDeclImpl extends XmlElementImpl implements XmlElementDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlElementDeclImpl() {
    super(XML_ELEMENT_DECL);
  }

  public int getChildRole(TreeElement child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else if (i == XML_ELEMENT_CONTENT_SPEC) {
      return ChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(ChildRole.XML_ELEMENT_CONTENT_SPEC);
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    if (isWritable() && isInProjectContent(getProject(),getContainingFile().getVirtualFile())) {
      final XmlElement nameElement = getNameElement();

      if (nameElement!=null) {
        nameElement.replace(
          SourceTreeToPsiMap.treeElementToPsi(Factory.createSingleLeafElement(XmlTokenType.XML_NAME,name.toCharArray(),0, name.length(),null,getManager()))
        );
      }
    }

    return null;
  }

  private static boolean isInProjectContent(Project project, VirtualFile vfile) {
    return vfile== null || ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vfile)!=null;
  }

  public String getName() {
    XmlElement name = getNameElement();
    return (name != null )? name.getText():null;
  }
}
