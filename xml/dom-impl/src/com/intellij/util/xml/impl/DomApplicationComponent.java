// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionAssignabilityCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.CollectionFactory;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service(Service.Level.APP)
public final class DomApplicationComponent {
  private final MultiMap<String, DomFileMetaData> myRootTagName2FileDescription = MultiMap.createSet();
  private final Set<DomFileMetaData> myAcceptingOtherRootTagNamesDescriptions = new HashSet<>();
  private final ImplementationClassCache myCachedImplementationClasses = new ImplementationClassCache(DomImplementationClassEP.EP_NAME);
  private final TypeChooserManager myTypeChooserManager = new TypeChooserManager();
  final ReflectionAssignabilityCache assignabilityCache = new ReflectionAssignabilityCache();
  private final Map<Class<?>, DomElementsAnnotator> classToAnnotator = new ConcurrentHashMap<>();
  private final Map<Class<?>, DomFileDescription<?>> classToDescription = new ConcurrentHashMap<>();

  private final Map<Class<?>, InvocationCache> myInvocationCaches = CollectionFactory.createConcurrentSoftValueMap();
  private final Map<Class<? extends DomElementVisitor>, VisitorDescription> myVisitorDescriptions = new ConcurrentHashMap<>();

  public DomApplicationComponent() {
    registerDescriptions();

    //noinspection deprecation
    addChangeListener(DomFileDescription.EP_NAME, this::extensionsChanged);
    addChangeListener(DomFileMetaData.EP_NAME, this::extensionsChanged);
    addChangeListener(DomImplementationClassEP.EP_NAME, this::extensionsChanged);
  }

  private static <T> void addChangeListener(ExtensionPointName<T> ep, Runnable onChange) {
    Application app = ApplicationManager.getApplication();
    if (app.isDisposed()) {
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
    classToAnnotator.clear();
    classToDescription.clear();

    myCachedImplementationClasses.clearCache();
    myTypeChooserManager.clearCache();

    myInvocationCaches.clear();
    assignabilityCache.clear();

    myVisitorDescriptions.clear();

    registerDescriptions();
  }

  public static DomApplicationComponent getInstance() {
    return ApplicationManager.getApplication().getService(DomApplicationComponent.class);
  }

  public synchronized int getCumulativeVersion(boolean forStubs) {
    return allMetas().mapToInt(meta -> {
      if (forStubs) {
        if (meta.stubVersion != null) {
          return meta.stubVersion + StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
        }
      }
      else {
        return meta.domVersion + StringUtil.notNullize(meta.rootTagName).hashCode(); // so that a plugin enabling/disabling could trigger the reindexing
      }
      return 0;
    }).sum();
  }

  private @NotNull Stream<DomFileMetaData> allMetas() {
    return Stream.concat(myRootTagName2FileDescription.values().stream(), myAcceptingOtherRootTagNamesDescriptions.stream());
  }

  public synchronized @NotNull List<DomFileMetaData> getStubBuildingMetadata() {
    return allMetas().filter(m -> m.hasStubs()).collect(Collectors.toList());
  }

  @Nullable
  public synchronized DomFileMetaData findMeta(DomFileDescription<?> description) {
    return allMetas().filter(m -> m.lazyInstance == description).findFirst().orElse(null);
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

  void initDescription(@NotNull DomFileDescription<?> description) {
    for (Map.Entry<Class<? extends DomElement>, Class<? extends DomElement>> entry : description.getImplementations().entrySet()) {
      registerImplementation(entry.getKey(), entry.getValue(), null);
    }

    myTypeChooserManager.copyFrom(description.getTypeChooserManager());
  }

  synchronized void removeDescription(DomFileDescription<?> description) {
    DomFileMetaData meta = findMeta(description);
    myRootTagName2FileDescription.get(description.getRootTagName()).remove(meta);
    myAcceptingOtherRootTagNamesDescriptions.remove(meta);
  }

  public synchronized @Nullable DomFileDescription<?> findFileDescription(@NotNull Class<?> rootElementClass) {
    return classToDescription.computeIfAbsent(rootElementClass, this::_findFileDescription);
  }

  private synchronized @Nullable DomFileDescription<?> _findFileDescription(Class<?> rootElementClass) {
    return allMetas()
      .map(meta -> meta.getDescription())
      .filter(description -> description.getRootElementClass() == rootElementClass)
      .findAny()
      .orElse(null);
  }

  public DomElementsAnnotator getAnnotator(@NotNull Class<?> rootElementClass) {
    return classToAnnotator.computeIfAbsent(rootElementClass, key -> {
      DomFileDescription<?> desc = findFileDescription(key);
      return desc == null ? null : desc.createAnnotator();
    });
  }

  @Nullable Class<? extends DomElement> getImplementation(Class<?> concreteInterface) {
    //noinspection unchecked
    return (Class<? extends DomElement>)myCachedImplementationClasses.get(concreteInterface);
  }

  public void registerImplementation(Class<? extends DomElement> domElementClass, Class<? extends DomElement> implementationClass,
                                     @Nullable final Disposable parentDisposable) {
    myCachedImplementationClasses.registerImplementation(domElementClass, implementationClass, parentDisposable);
  }

  TypeChooserManager getTypeChooserManager() {
    return myTypeChooserManager;
  }

  public StaticGenericInfo getStaticGenericInfo(final Type type) {
    return getInvocationCache(ReflectionUtil.getRawType(type)).genericInfo;
  }

  InvocationCache getInvocationCache(Class<?> type) {
    return myInvocationCaches.computeIfAbsent(type, InvocationCache::new);
  }

  public VisitorDescription getVisitorDescription(Class<? extends DomElementVisitor> aClass) {
    return myVisitorDescriptions.computeIfAbsent(aClass, VisitorDescription::new);
  }
}
