package com.intellij.lang.xml;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CompositeLanguageFileViewProvider;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class XmlFileViewProvider extends CompositeLanguageFileViewProvider {
  private Set<Language> myRelevantLanguages = null;
  private final XMLLanguage myLanguage;

  public XmlFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical, XMLLanguage language) {
    super(manager, virtualFile, physical);
    myLanguage = language;
  }

  public void contentsSynchronized() {
    super.contentsSynchronized();
    myRelevantLanguages = null;
  }

  @NotNull
  public Set<Language> getLanguages() {
    if (myRelevantLanguages != null) return myRelevantLanguages;
    List<Language> relevantLanguages = new ArrayList<Language>(1);
    relevantLanguages.add(myLanguage);
    relevantLanguages.addAll(Arrays.asList(myLanguage.getLanguageExtensionsForFile(getPsi(myLanguage))));
    return myRelevantLanguages = new LinkedHashSet<Language>(relevantLanguages);
  }

  // Specifically override super implementation method in order to prevent SOE when this class's getRelevantLanguages() is invoked before
  // getting psi for myLanguage (see also super.isRelevantLanguage as well)
  protected boolean isRelevantLanguage(final Language lang) {
    return myLanguage == lang || super.isRelevantLanguage(lang);
  }

  @NotNull
  public LanguageFilter[] getLanguageExtensions() {
    return myLanguage.getLanguageExtensions();
  }

  protected XmlFileViewProvider cloneInner(final VirtualFile copy) {
    return new XmlFileViewProvider(getManager(), copy, false, myLanguage);
  }
}
