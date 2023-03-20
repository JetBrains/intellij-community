// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.copyright;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;

public class ShUpdateCopyrightsProvider extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new ShUpdatePsiFileCopyright(project, module, file, options);
  }
}

class ShUpdatePsiFileCopyright extends UpdatePsiFileCopyright {
  protected ShUpdatePsiFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
    super(project, module, root, options);
  }

  @Override
  protected boolean accept() {
    return getFile() instanceof ShFile;
  }

  @Override
  protected void scanFile() {
    PsiFile file = getFile();
    PsiElement first = file.getFirstChild();
    if (first instanceof PsiComment && ((PsiComment)first).getTokenType() == ShTypes.SHEBANG) first = getNextSibling(first);
    PsiElement next = first;
    while (next instanceof PsiComment || ((next instanceof ASTNode) && ((ASTNode)next).getElementType() == ShTypes.LINEFEED)) {
      next = getNextSibling(next);
    }
    if (first != null) {
      checkComments(first, next, true);
    } else {
      checkComments(null, null, true);
    }
  }
}