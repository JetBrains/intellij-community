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
package com.jetbrains.python.templateLanguages;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceHelper;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileSystemItemUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.WeakFileReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public class TemplateFileReference extends WeakFileReference {
  public TemplateFileReference(@NotNull FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  @Nullable
  @Override
  public String getUnresolvedDescription() {
    return "Template file '" + getCanonicalText() + "' not found";
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element, boolean absolute) throws IncorrectOperationException {
    if (!(element instanceof PsiFileSystemItem)) {
      throw new IncorrectOperationException("Cannot bind to element, should be instanceof PsiFileSystemItem: " + element);
    }

    final PsiFileSystemItem fileSystemItem = (PsiFileSystemItem)element;
    VirtualFile dstVFile = fileSystemItem.getVirtualFile();
    if (dstVFile == null) throw new IncorrectOperationException("Cannot bind to non-physical element:" + element);

    PsiFile file = getElement().getContainingFile();
    PsiElement contextPsiFile = InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file);
    if (contextPsiFile != null) file = contextPsiFile.getContainingFile(); // use host file!
    final VirtualFile curVFile = file.getVirtualFile();
    if (curVFile == null) throw new IncorrectOperationException("Cannot bind from non-physical element:" + file);

    String newName;

    PsiFileSystemItem curItem = null;
    PsiFileSystemItem dstItem = null;

    PsiFileSystemItem _dstItem = FileReferenceHelper.getPsiFileSystemItem(file.getManager(), dstVFile);
    PsiFileSystemItem _curItem = FileReferenceHelper.getPsiFileSystemItem(file.getManager(), curVFile);
    if (_dstItem != null && _curItem != null) {
      curItem = _curItem;
      dstItem = _dstItem;
    }

    final Collection<PsiFileSystemItem> contexts = getContexts();
    switch (contexts.size()) {
      case 0:
        break;
      default:
        for (PsiFileSystemItem context : contexts) {
          final VirtualFile contextFile = context.getVirtualFile();
          assert contextFile != null;
          if (VfsUtil.isAncestor(contextFile, dstVFile, true)) {
            final String path = VfsUtilCore.getRelativePath(dstVFile, contextFile, '/');
            if (path != null) {
              return rename(path.replace("/", getFileReferenceSet().getSeparatorString()));
            }
          }
        }
    }
    if (curItem == null) {
      throw new IncorrectOperationException("Cannot find path between files; " +
                                            "src = " + curVFile.getPresentableUrl() + "; " +
                                            "dst = " + dstVFile.getPresentableUrl() + "; " +
                                            "Contexts: " + contexts);
    }
    if (curItem.equals(dstItem)) {
      if (getCanonicalText().equals(dstItem.getName())) {
        return getElement();
      }
      return ElementManipulators.getManipulator(getElement()).handleContentChange(getElement(), getRangeInElement(), file.getName());
    }
    newName = PsiFileSystemItemUtil.getRelativePath(curItem, dstItem);
    if (newName == null) {
      return getElement();
    }

    return rename(newName);
  }
}
