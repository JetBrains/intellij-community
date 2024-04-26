// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
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
      final Set<Language> languages = new HashSet<>();
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

  @Override
  public @Nullable IElementType getContentElementType(@NotNull Language language) {
    if (language == getTemplateDataLanguage()) {
      return RestPythonElementTypes.PYTHON_BLOCK_DATA;
    }
    else if (language.getID().equals("DjangoTemplate")) {
      return RestPythonElementTypes.DJANGO_BLOCK_DATA;
    }
    return null;
  }
}
