// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;

public final class XmlElementChangeUtil {
  private XmlElementChangeUtil() {}

  static void doNameReplacement(final PsiNamedElement xmlElementDecl, XmlElement nameElement, final String name) throws
                                                                                                                 IncorrectOperationException {
    if (xmlElementDecl.isWritable() && isInProjectContent(xmlElementDecl.getProject(), xmlElementDecl.getContainingFile().getVirtualFile())) {

      if (nameElement!=null) {
        nameElement.replace(
          SourceTreeToPsiMap.treeElementToPsi(Factory.createSingleLeafElement(XmlTokenType.XML_NAME, name, null, xmlElementDecl.getManager()))
        );
      }
    }
  }

  static boolean isInProjectContent(Project project, VirtualFile vfile) {
    return vfile== null || ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vfile)!=null;
  }
}
