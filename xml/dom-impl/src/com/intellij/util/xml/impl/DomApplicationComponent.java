// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionAssignabilityCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.TypeChooserManager;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class DomApplicationComponent {
  private final MultiMap<String, DomFileMetaData> myRootTagName2FileDescription = MultiMap.createSet();
  private final Set<DomFileMetaData> myAcceptingOtherRootTagNamesDescriptions = new THashSet<>();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache(DomImplementationClassEP.EP_NAME);
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  final ReflectionAssignabilityCache assignabilityCache = new ReflectionAssignabilityCache();
  private final Map<Class, DomElementsAnnotator> myClass2Annotator = ConcurrentFactoryMap.createMap(key-> {
      final DomFileDescription desc = findFileDescription(key);
      return desc == null ? null : desc.createAnnotator();
    }
  );

  private final Map<Class, StaticGenericInfo> myGenericInfos = ConcurrentFactoryMap.createMap(StaticGenericInfo::new,
                                                                                              ContainerUtil::createConcurrentSoftValueMap);
  private final Map<Class, InvocationCache> myInvocationCaches = ConcurrentFactoryMap.createMap(InvocationCache::new,
                                                                                                ContainerUtil::createConcurrentSoftValueMap);
  private final Map<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    ConcurrentFactoryMap.createMap(VisitorDescription::new);


  public DomApplicationComponent() {
    //noinspection deprecation
    for (final DomFileDescription description : DomFileDescription.EP_NAME.getExtensionList()) {
      registerFileDescription(description);
    }
    for (DomFileMetaData meta : DomFileMetaData.EP_NAME.getExtensionList()) {
      registerFileDescription(meta);
    }
  }

  public static DomApplicationComponent getInstance() {
    return ServiceManager.getService(DomApplicationComponent.class);
  }

  public synchronized int getCumulativeVersion(boolean forStubs) {
    int result = 0;
    for (DomFileMetaData meta : allMetas()) {
      if (forStubs) {
        if (meta.stubVersion != null) {
          result += meta.stubVersion;
          result += StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
        }
      }
      else {
        result += meta.domVersion;
        result += StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
      }
    }
    return result;
  }

  private Iterable<DomFileMetaData> allMetas() {
    return ContainerUtil.concat(myRootTagName2FileDescription.values(), myAcceptingOtherRootTagNamesDescriptions);
  }

  @Nullable
  public synchronized DomFileMetaData findMeta(DomFileDescription description) {
    return ContainerUtil.find(allMetas(), m -> m.lazyInstance == description);
  }

  public synchronized Set<DomFileDescription> getFileDescriptions(String rootTagName) {
    return ContainerUtil.map2Set(myRootTagName2FileDescription.get(rootTagName), DomFileMetaData::getDescription);
  }

  public synchronized Set<DomFileDescription> getAcceptingOtherRootTagNameDescriptions() {
    return ContainerUtil.map2Set(myAcceptingOtherRootTagNamesDescriptions, DomFileMetaData::getDescription);
  }

  synchronized void registerFileDescription(DomFileDescription<?> description) {
    registerFileDescription(new DomFileMetaData(description));
    initDescription(description);
  }

  void registerFileDescription(DomFileMetaData meta) {
    if (StringUtil.isEmpty(meta.rootTagName)) {
      myAcceptingOtherRootTagNamesDescriptions.add(meta);
    } else {
      myRootTagName2FileDescription.putValue(meta.rootTagName, meta);
    }
  }

  void initDescription(DomFileDescription<?> description) {
    Map<Class<? extends DomElement>, Class<? extends DomElement>> implementations = description.getImplementations();
    for (final Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : implementations.entrySet()) {
      registerImplementation(entry.getKey(), entry.getValue(), null);
    }

    myTypeChooserManager.copyFrom(description.getTypeChooserManager());
  }

  synchronized void removeDescription(DomFileDescription<?> description) {
    DomFileMetaData meta = findMeta(description);
    myRootTagName2FileDescription.get(description.getRootTagName()).remove(meta);
    myAcceptingOtherRootTagNamesDescriptions.remove(meta);
  }

  @Nullable
  private synchronized DomFileDescription findFileDescription(Class rootElementClass) {
    for (DomFileMetaData meta : allMetas()) {
      DomFileDescription description = meta.lazyInstance;
      if (description != null && description.getRootElementClass() == rootElementClass) {
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

  TypeChooserManager getTypeChooserManager() {
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
