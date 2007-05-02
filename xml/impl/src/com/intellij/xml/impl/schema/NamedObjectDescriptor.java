package com.intellij.xml.impl.schema;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 22, 2005
 * Time: 4:35:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class NamedObjectDescriptor implements PsiWritableMetaData, PsiMetaData {
  private XmlTag myDcl;

  public NamedObjectDescriptor() {}

  public void setName(String name) throws IncorrectOperationException {
    setName(myDcl, name);
  }

  static void setName(final XmlTag dcl, final String name) throws IncorrectOperationException {
    if (dcl.isWritable()) {
      final VirtualFile virtualFile = dcl.getContainingFile().getVirtualFile();

      if (virtualFile!=null &&
          ProjectRootManager.getInstance(dcl.getProject()).getFileIndex().getModuleForFile(virtualFile)!=null
          ) {
        dcl.setAttribute("name",name.substring(name.indexOf(':')+1));
      }
    }
  }

  public PsiElement getDeclaration() {
    return myDcl;
  }

  public String getName(PsiElement context) {
    return getName();
  }

  public String getName() {
    return myDcl.getAttributeValue("name");
  }

  public void init(PsiElement element) {
    myDcl = (XmlTag)element;
  }

  public Object[] getDependences() {
    return new Object[] { myDcl };
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement,
                                     PsiElement place) {
    return true;
  }
}
