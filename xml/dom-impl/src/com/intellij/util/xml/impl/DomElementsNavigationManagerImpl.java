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

package com.intellij.util.xml.impl;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsNavigationManagerImpl extends DomElementsNavigationManager {
  private final Map<String, DomElementNavigationProvider> myProviders = new HashMap<>();
  private final Project myProject;

  private final DomElementNavigationProvider myTextEditorProvider = new MyDomElementNavigateProvider();

  public DomElementsNavigationManagerImpl(final Project project) {
    myProject = project;
    myProviders.put(myTextEditorProvider.getProviderName(), myTextEditorProvider);
  }

  @Override
  public Set<DomElementNavigationProvider> getDomElementsNavigateProviders(DomElement domElement) {
    Set<DomElementNavigationProvider> result = new HashSet<>();

    for (DomElementNavigationProvider navigateProvider : myProviders.values()) {
      if (navigateProvider.canNavigate(domElement)) result.add(navigateProvider) ;
    }
    return result;
  }

  @Override
  public DomElementNavigationProvider getDomElementsNavigateProvider(String providerName) {
    return myProviders.get(providerName);
  }

  @Override
  public void registerDomElementsNavigateProvider(DomElementNavigationProvider provider) {
    myProviders.put(provider.getProviderName(), provider);
  }

  private class MyDomElementNavigateProvider extends DomElementNavigationProvider {

    @Override
    public String getProviderName() {
      return DEFAULT_PROVIDER_NAME;
    }

    @Override
    public void navigate(DomElement domElement, boolean requestFocus) {
      if (!domElement.isValid()) return;

      final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(domElement);
      if (fileElement == null) return;

      VirtualFile file = fileElement.getFile().getVirtualFile();
      if (file == null) return;

      XmlElement xmlElement = domElement.getXmlElement();
      if (xmlElement instanceof XmlAttribute) xmlElement = ((XmlAttribute)xmlElement).getValueElement();
      final OpenFileDescriptor fileDescriptor = xmlElement != null ?
        new OpenFileDescriptor(myProject, file, xmlElement.getTextOffset()) :
        new OpenFileDescriptor(myProject, file);

      FileEditorManagerEx.getInstanceEx(myProject).openTextEditor(fileDescriptor, requestFocus);
    }

    @Override
    public boolean canNavigate(DomElement domElement) {
      return domElement != null && domElement.isValid();
    }
  }
}
