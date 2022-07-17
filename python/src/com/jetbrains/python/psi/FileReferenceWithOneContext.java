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
package com.jetbrains.python.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.CachingReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * This is a temporary fix for FileReference.bindToElement to take contexts from FileReference.getContexts() instead for helpers.
 *
 * @author traff
 */
public class FileReferenceWithOneContext extends FileReference {

  public FileReferenceWithOneContext(@NotNull FileReferenceSet fileReferenceSet,
                                     TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  public FileReferenceWithOneContext(FileReference original) {
    super(original);
  }

  @Override
  protected Collection<PsiFileSystemItem> getContextsForBindToElement(VirtualFile curVFile, Project project, FileReferenceHelper helper) {
    return getContexts();
  }

  @Override
  protected PsiElement rename(final String newName) throws IncorrectOperationException {
    if (FileUtil.isAbsolutePlatformIndependent(newName)) {
      return super.rename(newName);
    }
    else {
      PsiElement element = getElement();
      return CachingReference.getManipulator(element).handleContentChange(element, getRangeInElement(), newName);
    }
  }
}
