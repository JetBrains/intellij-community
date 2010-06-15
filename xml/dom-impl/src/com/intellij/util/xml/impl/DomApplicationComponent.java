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
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.MetaDataRegistrar;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionAssignabilityCache;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
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
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache();
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  final ReflectionAssignabilityCache assignabilityCache = new ReflectionAssignabilityCache();

  private final ConcurrentFactoryMap<Type, StaticGenericInfo> myGenericInfos = new ConcurrentFactoryMap<Type, StaticGenericInfo>() {
    @NotNull
    protected StaticGenericInfo create(final Type type) {
      return new StaticGenericInfo(type);
    }
  };
  private final ConcurrentFactoryMap<Class, InvocationCache> myInvocationCaches = new ConcurrentFactoryMap<Class, InvocationCache>() {
    @NotNull
    protected InvocationCache create(final Class key) {
      return new InvocationCache();
    }
  };
  private final ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    new ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription>() {
      @NotNull
      protected VisitorDescription create(final Class<? extends DomElementVisitor> key) {
        return new VisitorDescription(key);
      }
    };


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

    //noinspection unchecked
    final Map<Class<? extends DomElement>, Class<? extends DomElement>> implementations = description.getImplementations();
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : implementations.entrySet()) {
      registerImplementation(entry.getKey(), entry.getValue(), null);
    }

    myTypeChooserManager.copyFrom(description.getTypeChooserManager());
  }

  @Nullable
  final Class<? extends DomElement> getImplementation(final Class concreteInterface) {
    //noinspection unchecked
    return myCachedImplementationClasses.get(concreteInterface);
  }

  public final void registerImplementation(Class<? extends DomElement> domElementClass, Class<? extends DomElement> implementationClass,
                                           final Disposable parentDisposable) {
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass, parentDisposable);
  }

  public final void clearImplementations() {
    myCachedImplementationClasses.clear();
  }

  public TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  public final StaticGenericInfo getStaticGenericInfo(final Type type) {
    return myGenericInfos.get(type);
  }

  final InvocationCache getInvocationCache(final Class type) {
    return myInvocationCaches.get(type);
  }

  public final VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.get(aClass);
  }

}
