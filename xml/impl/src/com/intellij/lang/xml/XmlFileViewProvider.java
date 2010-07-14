/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.xml;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CompositeLanguageFileViewProvider;
import com.intellij.psi.PsiManager;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class XmlFileViewProvider extends CompositeLanguageFileViewProvider {
  private Set<Language> myRelevantLanguages = null;
  private final XMLLanguage myLanguage;

  public XmlFileViewProvider(final PsiManager manager, final VirtualFile virtualFile, final boolean physical, XMLLanguage language) {
    super(manager, virtualFile, physical, language);
    myLanguage = language;
    assert !(myLanguage instanceof TemplateLanguage);
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
    ContainerUtil.addAll(relevantLanguages, myLanguage.getLanguageExtensionsForFile(getPsi(myLanguage)));
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
