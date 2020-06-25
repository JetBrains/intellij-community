// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Disposer;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public final class DomApplicationComponent {
  private final MultiMap<String, DomFileMetaData> myRootTagName2FileDescription = MultiMap.createSet();
  private final Set<DomFileMetaData> myAcceptingOtherRootTagNamesDescriptions = new HashSet<>();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache(DomImplementationClassEP.EP_NAME);
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  final ReflectionAssignabilityCache assignabilityCache = new ReflectionAssignabilityCache();
  private final Map<Class<?>, DomElementsAnnotator> myClass2Annotator = ConcurrentFactoryMap.createMap(key-> {
      final DomFileDescription<?> desc = findFileDescription(key);
      return desc == null ? null : desc.createAnnotator();
    }
  );

  private final Map<Class<?>, InvocationCache> myInvocationCaches = ConcurrentFactoryMap.create(InvocationCache::new,
                                                                                                ContainerUtil::createConcurrentSoftValueMap);
  private final Map<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions =
    ConcurrentFactoryMap.createMap(VisitorDescription::new);


  public DomApplicationComponent() {
    registerDescriptions();

    //noinspection deprecation
    addChangeListener(DomFileDescription.EP_NAME, this::extensionsChanged);
    addChangeListener(DomFileMetaData.EP_NAME, this::extensionsChanged);
  }

  private static <T> void addChangeListener(ExtensionPointName<T> ep, Runnable onChange) {
    Application app = ApplicationManager.getApplication();
    if (Disposer.isDisposing(app)) {
      return;
    }
    ep.addChangeListener(onChange, app);
  }

  private void registerDescriptions() {
    //noinspection deprecation
    for (DomFileDescription<?> description : DomFileDescription.EP_NAME.getExtensionList()) {
      registerFileDescription(description);
    }
    for (DomFileMetaData meta : DomFileMetaData.EP_NAME.getExtensionList()) {
      registerFileDescription(meta);
    }
  }

  private synchronized void extensionsChanged() {
    myRootTagName2FileDescription.clear();
    myAcceptingOtherRootTagNamesDescriptions.clear();
    myClass2Annotator.clear();

    myCachedImplementationClasses.clearCache();
    myTypeChooserManager.clearCache();

    myInvocationCaches.clear();
    assignabilityCache.clear();

    myVisitorDescriptions.clear();

    registerDescriptions();
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
  public synchronized DomFileMetaData findMeta(DomFileDescription<?> description) {
    return ContainerUtil.find(allMetas(), m -> m.lazyInstance == description);
  }

  public synchronized Set<DomFileDescription<?>> getFileDescriptions(String rootTagName) {
    return ContainerUtil.map2Set(myRootTagName2FileDescription.get(rootTagName), DomFileMetaData::getDescription);
  }

  public synchronized Set<DomFileDescription<?>> getAcceptingOtherRootTagNameDescriptions() {
    return ContainerUtil.map2Set(myAcceptingOtherRootTagNamesDescriptions, DomFileMetaData::getDescription);
  }

  synchronized void registerFileDescription(DomFileDescription<?> description) {
    registerFileDescription(new DomFileMetaData(description));
    initDescription(description);
  }

  void registerFileDescription(@NotNull DomFileMetaData meta) {
    if (StringUtil.isEmpty(meta.rootTagName)) {
      myAcceptingOtherRootTagNamesDescriptions.add(meta);
    }
    else {
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
  private synchronized DomFileDescription<?> findFileDescription(Class<?> rootElementClass) {
    for (DomFileMetaData meta : allMetas()) {
      DomFileDescription<?> description = meta.lazyInstance;
      if (description != null && description.getRootElementClass() == rootElementClass) {
        return description;
      }
    }
    return null;
  }

  public DomElementsAnnotator getAnnotator(Class<?> rootElementClass) {
    return myClass2Annotator.get(rootElementClass);
  }

  @Nullable
  final Class<? extends DomElement> getImplementation(Class<?> concreteInterface) {
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
    return getInvocationCache(ReflectionUtil.getRawType(type)).genericInfo;
  }

  final InvocationCache getInvocationCache(Class<?> type) {
    return myInvocationCaches.get(type);
  }

  public final VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.get(aClass);
  }

}
