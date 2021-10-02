// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager {
  static final Object LOCK = new Object();

  private final EventDispatcher<DomHighlightingListener> myDispatcher = EventDispatcher.create(DomHighlightingListener.class);

  private static final DomElementsProblemsHolder EMPTY_PROBLEMS_HOLDER = new DomElementsProblemsHolder() {
    @Override
    @NotNull
    public List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                         final boolean includeXmlProblems,
                                                         final boolean withChildren) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getProblems(DomElement domElement, final boolean withChildren, HighlightSeverity minSeverity) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getAllProblems() {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getAllProblems(@NotNull DomElementsInspection inspection) {
      return Collections.emptyList();
    }

    @Override
    public boolean isInspectionCompleted(@NotNull final DomElementsInspection inspectionClass) {
      return false;
    }

  };

  private final Map<XmlTag, DomElementsProblemsHolderImpl> myHolders = CollectionFactory.createWeakMap();

  public DomElementAnnotationsManagerImpl(@NotNull Project project) {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProfileChangeAdapter.TOPIC, new ProfileChangeAdapter() {
      @Override
      public void profileActivated(InspectionProfile oldProfile, @Nullable InspectionProfile profile) {
        dropAnnotationsCache();
      }

      @Override
      public void profileChanged(@NotNull InspectionProfile profile) {
        dropAnnotationsCache();
      }
    });
    connection.subscribe(PsiModificationTracker.TOPIC, this::dropAnnotationsCache);
  }

  @Override
  public void dropAnnotationsCache() {
    synchronized (LOCK) {
      myHolders.clear();
    }
  }

  public final <T extends DomElement> List<DomElementProblemDescriptor> appendProblems(@NotNull DomFileElement<T> element,
                                                                                       @NotNull DomElementAnnotationHolder annotationHolder,
                                                                                       Class<? extends DomElementsInspection<?>> inspectionClass) {
    final DomElementAnnotationHolderImpl holderImpl = (DomElementAnnotationHolderImpl)annotationHolder;
    synchronized (LOCK) {
      final DomElementsProblemsHolderImpl holder = _getOrCreateProblemsHolder(element);
      holder.appendProblems(holderImpl, inspectionClass);
    }
    myDispatcher.getMulticaster().highlightingFinished(element);
    return Collections.unmodifiableList(holderImpl);
  }

  @NotNull
  private DomElementsProblemsHolderImpl _getOrCreateProblemsHolder(DomFileElement<?> element) {
    XmlTag rootTag = element.getRootElement().getXmlTag();
    if (rootTag == null) return new DomElementsProblemsHolderImpl(element);

    return myHolders.computeIfAbsent(rootTag, __ -> new DomElementsProblemsHolderImpl(element));
  }

  public boolean isHolderUpToDate(DomElement element) {
    return !isHolderOutdated(DomUtil.getFile(element));
  }

  public void outdateProblemHolder(DomElement element) {
    XmlTag rootTag = getRootTagIfParsed(DomUtil.getFile(element));
    synchronized (LOCK) {
      if (rootTag != null) {
        myHolders.remove(rootTag);
      }
    }
  }

  private boolean isHolderOutdated(XmlFile file) {
    synchronized (LOCK) {
      XmlTag rootTag = getRootTagIfParsed(file);
      return rootTag == null || !myHolders.containsKey(rootTag);
    }
  }

  @Nullable
  private static XmlTag getRootTagIfParsed(@NotNull XmlFile file) {
    return ((XmlFileImpl)file).isContentsLoaded() ? file.getRootTag() : null;
  }

  @Override
  @NotNull
  public DomElementsProblemsHolder getProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(element);

    synchronized (LOCK) {
      final XmlTag tag = fileElement.getRootElement().getXmlTag();
      if (tag != null) {
        DomElementsProblemsHolder readyHolder = myHolders.get(tag);
        if (readyHolder != null) {
          return readyHolder;
        }
      }
      return EMPTY_PROBLEMS_HOLDER;
    }
  }

  @Override
  @NotNull
  public DomElementsProblemsHolder getCachedProblemHolder(DomElement element) {
    return getProblemHolder(element);
  }

  @Override
  public List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, DomElementProblemDescriptor problemDescriptor) {
    return ContainerUtil.createMaybeSingletonList(DomElementsHighlightingUtil.createProblemDescriptors(manager, problemDescriptor));
  }

  @Override
  public boolean isHighlightingFinished(final DomElement[] domElements) {
    for (final DomElement domElement : domElements) {
      if (getHighlightStatus(domElement) != DomHighlightStatus.INSPECTIONS_FINISHED) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void addHighlightingListener(DomHighlightingListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public DomHighlightingHelper getHighlightingHelper() {
    return DomHighlightingHelperImpl.INSTANCE;
  }

  @Override
  @NotNull
  public <T extends DomElement> List<DomElementProblemDescriptor> checkFileElement(@NotNull DomFileElement<T> domFileElement,
                                                                                   @NotNull DomElementsInspection<T> inspection,
                                                                                   boolean onTheFly) {
    final DomElementsProblemsHolder problemHolder = getProblemHolder(domFileElement);
    if (isHolderUpToDate(domFileElement) && problemHolder.isInspectionCompleted(inspection)) {
      return problemHolder.getAllProblems(inspection);
    }

    DomElementAnnotationHolder holder = new DomElementAnnotationHolderImpl(onTheFly, domFileElement);
    inspection.checkFileElement(domFileElement, holder);
    //noinspection unchecked
    return appendProblems(domFileElement, holder, (Class<? extends DomElementsInspection<?>>)inspection.getClass());
  }

  public List<DomElementsInspection<?>> getSuitableDomInspections(final DomFileElement<?> fileElement, boolean enabledOnly) {
    Class<?> rootType = fileElement.getRootElementClass();
    final InspectionProfile profile = getInspectionProfile(fileElement);
    final List<DomElementsInspection<?>> inspections = new SmartList<>();
    for (final InspectionToolWrapper<?, ?> toolWrapper : profile.getInspectionTools(fileElement.getFile())) {
      if (!enabledOnly || profile.isToolEnabled(HighlightDisplayKey.find(toolWrapper.getShortName()), fileElement.getFile())) {
        InspectionProfileEntry entry = toolWrapper.getTool();
        if (entry instanceof DomElementsInspection &&
            ContainerUtil.exists(((DomElementsInspection<?>)entry).getDomClasses(), cls -> cls.isAssignableFrom(rootType))) {
          inspections.add((DomElementsInspection<?>)entry);
        }
      }
    }
    return inspections;
  }

  protected InspectionProfile getInspectionProfile(final DomFileElement<?> fileElement) {
    return InspectionProjectProfileManager.getInstance(fileElement.getManager().getProject()).getCurrentProfile();
  }

  @Nullable public <T extends DomElement>  DomElementsInspection<T> getMockInspection(DomFileElement<? extends T> root) {
    if (root.getFileDescription().isAutomaticHighlightingEnabled()) {
      return new MockAnnotatingDomInspection<>(root.getRootElementClass());
    }
    if (getSuitableDomInspections(root, false).isEmpty()) {
      return new MockDomInspection<>(root.getRootElementClass());
    }

    return null;
  }

  private static boolean areInspectionsFinished(DomElementsProblemsHolderImpl holder, final List<? extends DomElementsInspection<?>> suitableInspections) {
    for (final DomElementsInspection<?> inspection : suitableInspections) {
      if (!holder.isInspectionCompleted(inspection)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public DomHighlightStatus getHighlightStatus(final DomElement element) {
    synchronized (LOCK) {
      final DomFileElement<DomElement> root = DomUtil.getFileElement(element);
      if (!isHolderOutdated(root.getFile())) {
        final DomElementsProblemsHolder holder = getProblemHolder(element);
        if (holder instanceof DomElementsProblemsHolderImpl) {
          DomElementsProblemsHolderImpl holderImpl = (DomElementsProblemsHolderImpl)holder;
          final List<DomElementsInspection<?>> suitableInspections = getSuitableDomInspections(root, true);
          final DomElementsInspection<?> mockInspection = getMockInspection(root);
          final boolean annotatorsFinished = mockInspection == null || holderImpl.isInspectionCompleted(mockInspection);
          final boolean inspectionsFinished = areInspectionsFinished(holderImpl, suitableInspections);
          if (annotatorsFinished) {
            if (suitableInspections.isEmpty() || inspectionsFinished) return DomHighlightStatus.INSPECTIONS_FINISHED;
            return DomHighlightStatus.ANNOTATORS_FINISHED;
          }
        }
      }
      return DomHighlightStatus.NONE;
    }

  }
}
