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
package com.jetbrains.rest;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * User : catherine
 */
public class RestFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
  implements TemplateLanguageFileViewProvider {

  private Set<Language> myLanguages;

  public RestFileViewProvider(PsiManager manager, VirtualFile virtualFile, boolean physical) {
    super(manager, virtualFile, physical);
  }

  @NotNull
  @Override
  public Language getBaseLanguage() {
    return RestLanguage.INSTANCE;
  }

  @Override
  @NotNull
  public Language getTemplateDataLanguage() {
    return PythonLanguage.getInstance();
  }

  @NotNull
  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile virtualFile) {
    return new RestFileViewProvider(getManager(), virtualFile, false);
  }

  @Override
  @NotNull
  public Set<Language> getLanguages() {
    if (myLanguages == null) {
      final Set<Language> languages = ContainerUtil.newHashSet();
      languages.add(getBaseLanguage());
      Language djangoTemplateLanguage = Language.findLanguageByID("DjangoTemplate");
      if (djangoTemplateLanguage != null) {
        languages.add(djangoTemplateLanguage);
      }
      languages.add(getTemplateDataLanguage());
      myLanguages = languages;
    }
    return myLanguages;
  }

  @Override
  protected PsiFile createFile(@NotNull final Language lang) {
    ParserDefinition def = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (def == null) return null;
    if (lang == getTemplateDataLanguage()) {
      PsiFileImpl file = (PsiFileImpl)def.createFile(this);
      file.setContentElementType(RestPythonElementTypes.PYTHON_BLOCK_DATA);
      return file;
    }
    else if (lang.getID().equals("DjangoTemplate")) {
      PsiFileImpl file = (PsiFileImpl)def.createFile(this);
      file.setContentElementType(RestPythonElementTypes.DJANGO_BLOCK_DATA);
      return file;
    }
    else if (lang == RestLanguage.INSTANCE) {
      return def.createFile(this);
    }
    return null;
  }
}
