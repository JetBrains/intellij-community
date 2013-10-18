package com.jetbrains.rest;

import com.google.common.collect.Sets;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
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

  @Override
  protected MultiplePsiFilesPerDocumentFileViewProvider cloneInner(VirtualFile virtualFile) {
    return new RestFileViewProvider(getManager(), virtualFile, false);
  }

  @Override
  @NotNull
  public Set<Language> getLanguages() {
    if (myLanguages == null) {
      myLanguages = Sets.newLinkedHashSet();
      myLanguages.add(getBaseLanguage());
      Language djangoTemplateLanguage = Language.findLanguageByID("DjangoTemplate");
      if (djangoTemplateLanguage != null) {
        myLanguages.add(djangoTemplateLanguage);
      }
      myLanguages.add(getTemplateDataLanguage());
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
