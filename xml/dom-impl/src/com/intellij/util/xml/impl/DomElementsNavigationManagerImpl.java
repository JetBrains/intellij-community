/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
  private final Map<String, DomElementNavigationProvider> myProviders = new HashMap<String, DomElementNavigationProvider>();
  private final Project myProject;

  private final DomElementNavigationProvider myTextEditorProvider = new MyDomElementNavigateProvider();

  public DomElementsNavigationManagerImpl(final Project project) {
    myProject = project;
    myProviders.put(myTextEditorProvider.getProviderName(), myTextEditorProvider);
  }

  public Set<DomElementNavigationProvider> getDomElementsNavigateProviders(DomElement domElement) {
    Set<DomElementNavigationProvider> result = new HashSet<DomElementNavigationProvider>();

    for (DomElementNavigationProvider navigateProvider : myProviders.values()) {
      if (navigateProvider.canNavigate(domElement)) result.add(navigateProvider) ;
    }
    return result;
  }

  public DomElementNavigationProvider getDomElementsNavigateProvider(String providerName) {
    return myProviders.get(providerName);
  }

  public void registerDomElementsNavigateProvider(DomElementNavigationProvider provider) {
    myProviders.put(provider.getProviderName(), provider);
  }

  private class MyDomElementNavigateProvider extends DomElementNavigationProvider {

    public String getProviderName() {
      return DEFAULT_PROVIDER_NAME;
    }

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

    public boolean canNavigate(DomElement domElement) {
      return domElement != null && domElement.isValid();
    }
  }
}
