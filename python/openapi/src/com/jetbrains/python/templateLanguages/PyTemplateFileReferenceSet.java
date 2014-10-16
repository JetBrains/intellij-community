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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralFileReferenceSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PyTemplateFileReferenceSet extends PyStringLiteralFileReferenceSet {
  public PyTemplateFileReferenceSet(PyStringLiteralExpression element) {
    super(element, false);
  }

  @NotNull
  @Override
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    List<PsiFileSystemItem> contexts = ContainerUtil.newArrayList();
    if (getPathString().startsWith("/") || getPathString().startsWith("\\")) {
      return contexts;
    }
    Module module = ModuleUtilCore.findModuleForPsiElement(getElement());
    if (module != null) {
      List<VirtualFile> templatesFolders = getRoots(module);
      for (VirtualFile folder : templatesFolders) {
        final PsiDirectory directory = PsiManager.getInstance(module.getProject()).findDirectory(folder);
        if (directory != null) {
          contexts.add(directory);
        }
      }
    }
    return contexts;
  }

  @Override
  public String getSeparatorString() {
    return TemplateFileReferenceSet.detectSeparator(getElement()); //we need it not to change slashes during rebind
  }

  protected List<VirtualFile> getRoots(Module module) {
    return TemplatesService.getInstance(module).getTemplateFolders();
  }

  @Override
  public FileReference createFileReference(TextRange range, int index, String text) {
    return new TemplateFileReference(this, range, index, text);
  }
}
