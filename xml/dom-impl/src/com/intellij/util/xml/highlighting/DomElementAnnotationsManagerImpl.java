/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileChangeAdapter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.impl.DomApplicationComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class DomElementAnnotationsManagerImpl extends DomElementAnnotationsManager {
  public static final Object LOCK = new Object();

  private static final Key<DomElementsProblemsHolderImpl> DOM_PROBLEM_HOLDER_KEY = Key.create("DomProblemHolder");
  private static final Key<CachedValue<Boolean>> CACHED_VALUE_KEY = Key.create("DomProblemHolderCachedValue");
  private final EventDispatcher<DomHighlightingListener> myDispatcher = EventDispatcher.create(DomHighlightingListener.class);

  private static final DomElementsProblemsHolder EMPTY_PROBLEMS_HOLDER = new DomElementsProblemsHolder() {
    @Override
    @NotNull
    public List<DomElementProblemDescriptor> getProblems(DomElement domElement) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getProblems(final DomElement domElement, boolean includeXmlProblems) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getProblems(final DomElement domElement,
                                                         final boolean includeXmlProblems,
                                                         final boolean withChildren) {
      return Collections.emptyList();
    }

    @Override
    public List<DomElementProblemDescriptor> getProblems(DomElement domElement,
                                                         final boolean includeXmlProblems,
                                                         final boolean withChildren,
                                                         HighlightSeverity minSeverity) {
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
  private final Project myProject;

  public DomElementAnnotationsManagerImpl(Project project) {
    myProject = project;
    final ProfileChangeAdapter profileChangeAdapter = new ProfileChangeAdapter() {
      @Override
      public void profileActivated(Profile oldProfile, @Nullable Profile profile) {
        dropAnnotationsCache();
      }

      @Override
      public void profileChanged(Profile profile) {
        dropAnnotationsCache();
      }
    };

    InspectionProfileManager.getInstance().addProfileChangeListener(profileChangeAdapter, project);
  }

  @Override
  public void dropAnnotationsCache() {
    incModificationCount();
  }

  public final List<DomElementProblemDescriptor> appendProblems(@NotNull DomFileElement element, @NotNull DomElementAnnotationHolder annotationHolder, Class<? extends DomElementsInspection> inspectionClass) {
    final DomElementAnnotationHolderImpl holderImpl = (DomElementAnnotationHolderImpl)annotationHolder;
    synchronized (LOCK) {
      final DomElementsProblemsHolderImpl holder = _getOrCreateProblemsHolder(element);
      holder.appendProblems(holderImpl, inspectionClass);
    }
    myDispatcher.getMulticaster().highlightingFinished(element);
    return Collections.unmodifiableList(holderImpl);
  }

  private DomElementsProblemsHolderImpl _getOrCreateProblemsHolder(final DomFileElement element) {
    DomElementsProblemsHolderImpl holder;
    final DomElement rootElement = element.getRootElement();
    final XmlTag rootTag = rootElement.getXmlTag();
    if (rootTag == null) return new DomElementsProblemsHolderImpl(element);

    holder = rootTag.getUserData(DOM_PROBLEM_HOLDER_KEY);
    if (isHolderOutdated(element.getFile()) || holder == null) {
      holder = new DomElementsProblemsHolderImpl(element);
      rootTag.putUserData(DOM_PROBLEM_HOLDER_KEY, holder);
      final CachedValue<Boolean> cachedValue = CachedValuesManager.getManager(myProject).createCachedValue(
        () -> new CachedValueProvider.Result<>(Boolean.FALSE, element, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT,
                                               this, ProjectRootManager.getInstance(myProject)), false);
      cachedValue.getValue();
      element.getFile().putUserData(CACHED_VALUE_KEY, cachedValue);
    }
    return holder;
  }

  public static boolean isHolderUpToDate(DomElement element) {
    synchronized (LOCK) {
      return !isHolderOutdated(DomUtil.getFile(element));
    }
  }

  public static void outdateProblemHolder(final DomElement element) {
    synchronized (LOCK) {
      DomUtil.getFile(element).putUserData(CACHED_VALUE_KEY, null);
    }
  }

  private static boolean isHolderOutdated(final XmlFile file) {
    final CachedValue<Boolean> cachedValue = file.getUserData(CACHED_VALUE_KEY);
    return cachedValue == null || !cachedValue.hasUpToDateValue();
  }

  @Override
  @NotNull
  public DomElementsProblemsHolder getProblemHolder(DomElement element) {
    if (element == null || !element.isValid()) return EMPTY_PROBLEMS_HOLDER;
    final DomFileElement<DomElement> fileElement = DomUtil.getFileElement(element);

    synchronized (LOCK) {
      final XmlTag tag = fileElement.getRootElement().getXmlTag();
      if (tag != null) {
        final DomElementsProblemsHolder readyHolder = tag.getUserData(DOM_PROBLEM_HOLDER_KEY);
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

  public static void annotate(final DomElement element, final DomElementAnnotationHolder holder, final Class rootClass) {
    final DomElementsAnnotator annotator = DomApplicationComponent.getInstance().getAnnotator(rootClass);
    if (annotator != null) {
      annotator.annotate(element, holder);
    }
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
  public <T extends DomElement> List<DomElementProblemDescriptor> checkFileElement(@NotNull final DomFileElement<T> domFileElement,
                                                                                   @NotNull final DomElementsInspection<T> inspection,
                                                                                   boolean onTheFly) {
    final DomElementsProblemsHolder problemHolder = getProblemHolder(domFileElement);
    if (isHolderUpToDate(domFileElement) && problemHolder.isInspectionCompleted(inspection)) {
      return problemHolder.getAllProblems(inspection);
    }

    final DomElementAnnotationHolder holder = new DomElementAnnotationHolderImpl(onTheFly);
    inspection.checkFileElement(domFileElement, holder);
    return appendProblems(domFileElement, holder, inspection.getClass());
  }

  public List<DomElementsInspection> getSuitableDomInspections(final DomFileElement fileElement, boolean enabledOnly) {
    Class rootType = fileElement.getRootElementClass();
    final InspectionProfile profile = getInspectionProfile(fileElement);
    final List<DomElementsInspection> inspections = new SmartList<>();
    for (final InspectionToolWrapper toolWrapper : profile.getInspectionTools(fileElement.getFile())) {
      if (!enabledOnly || profile.isToolEnabled(HighlightDisplayKey.find(toolWrapper.getShortName()), fileElement.getFile())) {
        ContainerUtil.addIfNotNull(inspections, getSuitableInspection(toolWrapper.getTool(), rootType));
      }
    }
    return inspections;
  }

  protected InspectionProfile getInspectionProfile(final DomFileElement fileElement) {
    return InspectionProjectProfileManager.getInstance(fileElement.getManager().getProject()).getCurrentProfile();
  }

  @Nullable
  private static DomElementsInspection getSuitableInspection(InspectionProfileEntry entry, Class rootType) {
    if (entry instanceof DomElementsInspection) {
      if (((DomElementsInspection)entry).getDomClasses().contains(rootType)) {
        return (DomElementsInspection) entry;
      }
    }
    return null;
  }

  @Nullable public <T extends DomElement>  DomElementsInspection<T> getMockInspection(DomFileElement<T> root) {
    if (root.getFileDescription().isAutomaticHighlightingEnabled()) {
      return new MockAnnotatingDomInspection<>(root.getRootElementClass());
    }
    if (getSuitableDomInspections(root, false).isEmpty()) {
      return new MockDomInspection<>(root.getRootElementClass());
    }

    return null;
  }

  private static boolean areInspectionsFinished(DomElementsProblemsHolderImpl holder, final List<DomElementsInspection> suitableInspections) {
    for (final DomElementsInspection inspection : suitableInspections) {
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
          final List<DomElementsInspection> suitableInspections = getSuitableDomInspections(root, true);
          final DomElementsInspection mockInspection = getMockInspection(root);
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
