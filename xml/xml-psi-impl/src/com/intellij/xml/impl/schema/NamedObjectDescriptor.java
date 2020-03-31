/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.impl.schema;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Mossienko
 */
public class NamedObjectDescriptor implements PsiWritableMetaData, PsiMetaData {
  private XmlTag myDcl;

  public NamedObjectDescriptor() {}

  @Override
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

  @Override
  public PsiElement getDeclaration() {
    return myDcl;
  }

  @Override
  public String getName(PsiElement context) {
    return getName();
  }

  @Override
  public String getName() {
    return myDcl.getAttributeValue("name");
  }

  @Override
  public void init(PsiElement element) {
    myDcl = (XmlTag)element;
  }

  @Override
  public Object @NotNull [] getDependencies() {
    return new Object[] { myDcl };
  }

}
