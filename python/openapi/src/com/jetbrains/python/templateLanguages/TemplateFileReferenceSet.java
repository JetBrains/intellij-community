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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class TemplateFileReferenceSet extends FileReferenceSet {
  private final String mySeparatorString;

  public TemplateFileReferenceSet(@NotNull PsiElement element, @Nullable PsiReferenceProvider provider) {
    this(str(element), element, provider);
  }

  public TemplateFileReferenceSet(String text, @NotNull PsiElement element,
                                  @Nullable PsiReferenceProvider provider) {
    super(text, element, detectShift(element, text), provider,
          SystemInfo.isFileSystemCaseSensitive);
    mySeparatorString = detectSeparator(element);
    reparse();
  }

  private static String str(@NotNull PsiElement element) {
    return PythonStringUtil.stripQuotesAroundValue(element.getText());
  }

  public static int detectShift(PsiElement element, String text) {
    String elementText = element.getText();
    int from = 0;
    Pair<String, String> quotes = PythonStringUtil.getQuotes(elementText);
    if (quotes != null) {
      from = quotes.first.length();
    }

    return elementText.indexOf(text, from);
  }

  public static String detectSeparator(PsiElement element) {
    String winSeparator;
    if (PythonStringUtil.isRawString(element.getText())) {
      winSeparator = "\\";
    }
    else {
      winSeparator = "\\\\";
    }
    return str(element).contains(winSeparator) ? winSeparator : "/";
  }

  @Override
  public String getSeparatorString() {
    if (mySeparatorString == null) {
      return super.getSeparatorString();
    }
    else {
      return mySeparatorString;
    }
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
        final PsiFileSystemItem directory = getPsiDirectory(module, folder);
        if (directory != null) {
          contexts.add(directory);
        }
      }
    }
    return contexts;
  }

  @Nullable
  protected PsiFileSystemItem getPsiDirectory(Module module, VirtualFile folder) {
    return PsiManager.getInstance(module.getProject()).findDirectory(folder);
  }

  protected List<VirtualFile> getRoots(Module module) {
    return TemplatesService.getInstance(module).getTemplateFolders();
  }

  @Override
  public FileReference createFileReference(TextRange range, int index, String text) {
    return new TemplateFileReference(this, range, index, text);
  }
}
