/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.DomMetaData;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
public class DomApplicationComponent {
  private final FactoryMap<String,Set<DomFileDescription>> myRootTagName2FileDescription = new FactoryMap<String, Set<DomFileDescription>>() {
    protected Set<DomFileDescription> create(final String key) {
      return new THashSet<DomFileDescription>();
    }
  };
  private final Set<DomFileDescription> myAcceptingOtherRootTagNamesDescriptions = new THashSet<DomFileDescription>();

  public DomApplicationComponent() {
    for (final DomFileDescription description : Extensions.getExtensions(DomFileDescription.EP_NAME)) {
      registerFileDescription(description);
    }

    MetaDataRegistrar.getInstance().registerMetaData(new ElementFilter() {
      public boolean isAcceptable(Object element, PsiElement context) {
        if (element instanceof XmlTag) {
          final XmlTag tag = (XmlTag)element;
          final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
          if (domElement != null) {
            return domElement.getGenericInfo().getNameDomElement(domElement) != null;
          }
        }
        return false;
      }

      public boolean isClassAcceptable(Class hintClass) {
        return XmlTag.class.isAssignableFrom(hintClass);
      }
    }, DomMetaData.class);

  }

  public static DomApplicationComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(DomApplicationComponent.class);
  }

  public final synchronized Set<DomFileDescription> getFileDescriptions(String rootTagName) {
    return myRootTagName2FileDescription.get(rootTagName);
  }

  public final synchronized Set<DomFileDescription> getAcceptingOtherRootTagNameDescriptions() {
    return myAcceptingOtherRootTagNamesDescriptions;
  }

  public final synchronized void registerFileDescription(final DomFileDescription description) {
    myRootTagName2FileDescription.get(description.getRootTagName()).add(description);
    if (description.acceptsOtherRootTagNames()) {
      myAcceptingOtherRootTagNamesDescriptions.add(description);
    }
  }

  @Nullable
  public final synchronized Set<DomFileDescription> getAllFileDescriptions() {
    final THashSet<DomFileDescription> set = new THashSet<DomFileDescription>(myAcceptingOtherRootTagNamesDescriptions);
    for (final Set<DomFileDescription> descriptions : myRootTagName2FileDescription.values()) {
      set.addAll(descriptions);
    }
    return set;
  }

  @Nullable
  public final synchronized DomFileDescription findFileDescription(String className) {
    for (final Set<DomFileDescription> descriptions : myRootTagName2FileDescription.values()) {
      for (final DomFileDescription description : descriptions) {
        if (description.getClass().getName().equals(className)) return description;
      }
    }
    for (final DomFileDescription description : myAcceptingOtherRootTagNamesDescriptions) {
      if (description.getClass().getName().equals(className)) return description;
    }
    return null;
  }


}
