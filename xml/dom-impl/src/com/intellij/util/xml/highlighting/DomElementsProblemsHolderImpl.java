// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class DomElementsProblemsHolderImpl implements DomElementsProblemsHolder {
  private final Map<DomElement, Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>>> myCachedErrors =
    new ConcurrentHashMap<>();
  private final Map<DomElement, Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>>> myCachedChildrenErrors =
    new ConcurrentHashMap<>();
  private final List<Annotation> myAnnotations = new ArrayList<>();

  private final Function<DomElement, List<DomElementProblemDescriptor>> myDomProblemsGetter =
    s -> {
      Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>> map = myCachedErrors.get(s);
      return map == null ? Collections.emptyList() : ContainerUtil.concat(map.values());
    };

  private final DomFileElement myElement;

  private final Set<Class<? extends DomElementsInspection>> myPassedInspections = new HashSet<>();

  public DomElementsProblemsHolderImpl(final DomFileElement element) {
    myElement = element;
  }

  public void appendProblems(final DomElementAnnotationHolderImpl holder, final Class<? extends DomElementsInspection<?>> inspectionClass) {
    if (isInspectionCompleted(inspectionClass)) return;

    for (final DomElementProblemDescriptor descriptor : holder) {
      addProblem(descriptor, inspectionClass);
    }
    myAnnotations.addAll(holder.getAnnotations());
    myPassedInspections.add(inspectionClass);
  }

  @Override
  public boolean isInspectionCompleted(final @NotNull DomElementsInspection inspection) {
    return isInspectionCompleted(inspection.getClass());
  }

  public boolean isInspectionCompleted(final Class<? extends DomElementsInspection> inspectionClass) {
    synchronized (DomElementAnnotationsManagerImpl.LOCK) {
      return myPassedInspections.contains(inspectionClass);
    }
  }

  public List<Annotation> getAnnotations() {
    return myAnnotations;
  }

  public void addProblem(DomElementProblemDescriptor descriptor, Class<? extends DomElementsInspection<?>> inspection) {
    myCachedErrors
      .computeIfAbsent(descriptor.getDomElement(), __ -> new ConcurrentHashMap<>())
      .computeIfAbsent(inspection, __ -> new SmartList<>())
      .add(descriptor);
    myCachedChildrenErrors.clear();
  }

  @Override
  public synchronized @NotNull List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
    if (domElement == null || !domElement.isValid()) {
      return Collections.emptyList();
    }
    return myDomProblemsGetter.apply(domElement);
  }

  @Override
  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       boolean includeXmlProblems,
                                                       boolean withChildren) {
    if (!withChildren || domElement == null || !domElement.isValid()) {
      return getProblems(domElement);
    }

    return ContainerUtil.concat(getProblemsMap(domElement).values());
  }

  @Unmodifiable
  public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                       final boolean includeXmlProblems,
                                                       final boolean withChildren,
                                                       final HighlightSeverity minSeverity) {
    return getProblems(domElement, withChildren, minSeverity);
  }

  @Override
  public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, final boolean withChildren, final HighlightSeverity minSeverity) {
    return ContainerUtil.findAll(getProblems(domElement, true, withChildren),
                                 object -> SeverityRegistrar.getSeverityRegistrar(domElement.getManager().getProject()).compare(object.getHighlightSeverity(), minSeverity) >= 0);

  }

  private @NotNull Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>> getProblemsMap(final DomElement domElement) {
    final Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>> map = myCachedChildrenErrors.get(domElement);
    if (map != null) {
      return map;
    }

    final Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>> problems = new HashMap<>();
    if (domElement == myElement) {
      for (Map<Class<? extends DomElementsInspection<?>>, List<DomElementProblemDescriptor>> listMap : myCachedErrors.values()) {
        mergeMaps(problems, listMap);
      }
    }
    else {
      mergeMaps(problems, myCachedErrors.get(domElement));
      if (DomUtil.hasXml(domElement)) {
        domElement.acceptChildren(new DomElementVisitor() {
          @Override
          public void visitDomElement(DomElement element) {
            mergeMaps(problems, getProblemsMap(element));
          }
        });
      }
    }

    myCachedChildrenErrors.put(domElement, problems);
    return problems;
  }

  private static <T> void mergeMaps(@NotNull Map<T, List<DomElementProblemDescriptor>> accumulator, @Nullable Map<T, List<DomElementProblemDescriptor>> toAdd) {
    if (toAdd == null) {
      return;
    }

    for (Map.Entry<T, List<DomElementProblemDescriptor>> entry : toAdd.entrySet()) {
      accumulator.computeIfAbsent(entry.getKey(), __ -> new SmartList<>()).addAll(entry.getValue());
    }
  }

  @Override
  public List<DomElementProblemDescriptor> getAllProblems() {
    return getProblems(myElement, false, true);
  }

  @Override
  public List<DomElementProblemDescriptor> getAllProblems(@NotNull DomElementsInspection inspection) {
    if (!myElement.isValid()) {
      return Collections.emptyList();
    }
    final List<DomElementProblemDescriptor> list = getProblemsMap(myElement).get(inspection.getClass());
    return list != null ? new ArrayList<>(list) : Collections.emptyList();
  }
}
