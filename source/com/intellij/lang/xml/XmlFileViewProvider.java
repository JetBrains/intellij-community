package com.intellij.lang.xml;

import com.intellij.psi.CompositeLanguageFileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;

import java.util.*;

public class XmlFileViewProvider extends CompositeLanguageFileViewProvider {
  private Set<Language> myRelevantLanguages = null;
  private XMLLanguage myLanguage;

  public XmlFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical, XMLLanguage language) {
    super(manager, virtualFile, physical);
    myLanguage = language;
  }


  public void contentsSynchronized() {
    myRelevantLanguages = null;
  }

  public Set<Language> getRelevantLanguages() {
    if(myRelevantLanguages != null) return myRelevantLanguages;
    List<Language> relevantLanguages = new ArrayList<Language>();
    relevantLanguages.add(myLanguage);
    relevantLanguages.addAll(Arrays.asList(myLanguage.getLanguageExtensionsForFile(getPsi(myLanguage))));
    return myRelevantLanguages = new LinkedHashSet<Language>(relevantLanguages);
  }


  public LanguageExtension[] getLanguageExtensions() {
    return myLanguage.getLanguageExtensions();
  }
}
