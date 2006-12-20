/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 18, 2006
 * Time: 3:50:51 PM
 */
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ProjectRootManager;

public class XmlElementChangeUtil {
  static void doNameReplacement(final PsiNamedElement xmlElementDecl, XmlElement nameElement, final String name) throws
                                                                                                                 IncorrectOperationException {
    if (xmlElementDecl.isWritable() && isInProjectContent(xmlElementDecl.getProject(), xmlElementDecl.getContainingFile().getVirtualFile())) {

      if (nameElement!=null) {
        nameElement.replace(
          SourceTreeToPsiMap.treeElementToPsi(Factory.createSingleLeafElement(XmlTokenType.XML_NAME,name,0, name.length(),null,
                                                                              xmlElementDecl.getManager()))
        );
      }
    }
  }

  static boolean isInProjectContent(Project project, VirtualFile vfile) {
    return vfile== null || ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vfile)!=null;
  }
}