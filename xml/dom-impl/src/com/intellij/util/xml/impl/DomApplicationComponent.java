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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.ReflectionAssignabilityCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.TypeChooserManager;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.createConcurrentSoftValueMap;
import static com.intellij.util.containers.ContainerUtil.newArrayList;

/**
 * @author peter
 */
public class DomApplicationComponent {
  private final FactoryMap<String,Set<DomFileDescription>> myRootTagName2FileDescription = new FactoryMap<String, Set<DomFileDescription>>() {
    @Override
    protected Set<DomFileDescription> create(final String key) {
      return new THashSet<>();
    }
  };
  private final Set<DomFileDescription> myAcceptingOtherRootTagNamesDescriptions = new THashSet<>();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache(DomImplementationClassEP.EP_NAME);
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  final ReflectionAssignabilityCache assignabilityCache = new ReflectionAssignabilityCache();
  private final FactoryMap<Class, DomElementsAnnotator> myClass2Annotator = new ConcurrentFactoryMap<Class, DomElementsAnnotator>() {

    @Override
    protected DomElementsAnnotator create(Class key) {
      final DomFileDescription desc = findFileDescription(key);
      return desc == null ? null : desc.createAnnotator();
    }
  };

  private final FactoryMap<Class, StaticGenericInfo> myGenericInfos = new FactoryMap<Class, StaticGenericInfo>() {
    @Override
    protected Map<Class, StaticGenericInfo> createMap() {
      return createConcurrentSoftValueMap();
    }
    @Nullable
    @Override
    protected StaticGenericInfo create(Class type) {
      return new StaticGenericInfo(type);
    }
  };
  private final FactoryMap<Class, InvocationCache> myInvocationCaches = new FactoryMap<Class, InvocationCache>() {
    @Override
    protected Map<Class, InvocationCache> createMap() {
      return createConcurrentSoftValueMap();
    }

    @Nullable
    @Override
    protected InvocationCache create(Class key) {
      return new InvocationCache(key);
    }
  };
  private final ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    new ConcurrentFactoryMap<Class<? extends DomElementVisitor>, VisitorDescription>() {
      @Override
      @NotNull
      protected VisitorDescription create(final Class<? extends DomElementVisitor> key) {
        return new VisitorDescription(key);
      }
    };


  public DomApplicationComponent() {
    for (final DomFileDescription description : Extensions.getExtensions(DomFileDescription.EP_NAME)) {
      registerFileDescription(description);
    }
  }

  public static DomApplicationComponent getInstance() {
    return ServiceManager.getService(DomApplicationComponent.class);
  }

  public int getCumulativeVersion(boolean forStubs) {
    int result = 0;
    for (DomFileDescription description : getAllFileDescriptions()) {
      if (forStubs) {
        if (description.hasStubs()) {
          result += description.getStubVersion();
          result += description.getRootTagName().hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
        }
      }
      else {
        result += description.getVersion();
        result += description.getRootTagName().hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
      }
    }
    return result;
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

  public synchronized List<DomFileDescription> getAllFileDescriptions() {
    final List<DomFileDescription> result = newArrayList();
    for (Set<DomFileDescription> descriptions : myRootTagName2FileDescription.values()) {
      result.addAll(descriptions);
    }
    result.addAll(myAcceptingOtherRootTagNamesDescriptions);
    return result;
  }

  @Nullable
  private synchronized DomFileDescription findFileDescription(Class rootElementClass) {
    for (Set<DomFileDescription> descriptions : myRootTagName2FileDescription.values()) {
      for (DomFileDescription description : descriptions) {
        if (description.getRootElementClass() == rootElementClass) {
          return description;
        }
      }
    }

    for (DomFileDescription description : myAcceptingOtherRootTagNamesDescriptions) {
      if (description.getRootElementClass() == rootElementClass) {
        return description;
      }
    }
    return null;
  }

  public DomElementsAnnotator getAnnotator(Class rootElementClass) {
    return myClass2Annotator.get(rootElementClass);
  }

  @Nullable
  final Class<? extends DomElement> getImplementation(final Class concreteInterface) {
    //noinspection unchecked
    return myCachedImplementationClasses.get(concreteInterface);
  }

  public final void registerImplementation(Class<? extends DomElement> domElementClass, Class<? extends DomElement> implementationClass,
                                           @Nullable final Disposable parentDisposable) {
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass, parentDisposable);
  }

  public TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  public final StaticGenericInfo getStaticGenericInfo(final Type type) {
    return myGenericInfos.get(ReflectionUtil.getRawType(type));
  }

  final InvocationCache getInvocationCache(final Class type) {
    return myInvocationCaches.get(type);
  }

  public final VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.get(aClass);
  }

}
