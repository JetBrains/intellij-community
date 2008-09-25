/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet;
import com.intellij.facet.pointers.FacetPointersManager;
import com.intellij.ide.impl.convert.ProjectFileVersion;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author nik
 */
@State(
  name = FacetAutodetectingManagerImpl.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class FacetAutodetectingManagerImpl extends FacetAutodetectingManager implements AutodetectionFilter, ProjectComponent, PersistentStateComponent<DisabledAutodetectionInfo> {
  @NonNls public static final String COMPONENT_NAME = "FacetAutodetectingManager";
  private final MultiValuesMap<FileType, FacetDetectorWrapper> myDetectors = new MultiValuesMap<FileType, FacetDetectorWrapper>();
  private final Map<String, FacetDetector<?,?>> myId2Detector = new HashMap<String, FacetDetector<?,?>>();
  private final Project myProject;
  private final PsiManager myPsiManager;
  private final FacetPointersManager myFacetPointersManager;
  private FacetDetectionIndex myFileIndex;
  private MyPsiTreeChangeListener myPsiTreeChangeListener;
  private MergingUpdateQueue myMergingUpdateQueue;
  private ProjectFacetInfoSet myDetectedFacetSet;
  private DetectedFacetManager myDetectedFacetManager;
  private DisabledAutodetectionInfo myDisabledAutodetectionInfo = new DisabledAutodetectionInfo();
  private boolean myDetectionInProgress;
  private final Set<FacetType<?,?>> myFacetTypesWithDetectors = new THashSet<FacetType<?,?>>();
  private final EnableAutodetectionWorker myEnableAutodetectionWorker;

  public FacetAutodetectingManagerImpl(final Project project, PsiManager psiManager, FacetPointersManager facetPointersManager) {
    myProject = project;
    myPsiManager = psiManager;
    myFacetPointersManager = facetPointersManager;
    myDetectedFacetSet = new ProjectFacetInfoSet(project, project);
    myEnableAutodetectionWorker = new EnableAutodetectionWorker(project, this);
  }

  public void projectOpened() {
    if (ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myDetectedFacetManager.initUI();
      }
    });
  }

  public void projectClosed() {
    if (myDetectedFacetManager != null) {
      myDetectedFacetManager.disposeUI();
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !myProject.isDefault()) {
      initialize();
    }
  }

  public void initialize() {
    myDetectedFacetManager = new DetectedFacetManager(myProject, this, myDetectedFacetSet);
    FacetType[] types = FacetTypeRegistry.getInstance().getFacetTypes();
    for (FacetType<?,?> type : types) {
      registerDetectors(type);
    }
    myDetectedFacetSet.loadDetectedFacets(FacetDetectionIndex.getDetectedFacetsFile(myProject));
    myFileIndex = new FacetDetectionIndex(myProject, this, myDetectors.keySet());
    myFileIndex.initialize();
    myPsiTreeChangeListener = new MyPsiTreeChangeListener();
    myPsiManager.addPsiTreeChangeListener(myPsiTreeChangeListener, myProject);
    myMergingUpdateQueue = new MergingUpdateQueue("FacetAutodetectionQueue", 500, true, null, myProject);
  }

  private <F extends Facet<C>, C extends FacetConfiguration> void registerDetectors(final FacetType<F, C> type) {
    FacetOnTheFlyDetectorRegistryImpl<C, F> detectorRegistry = new FacetOnTheFlyDetectorRegistryImpl<C, F>(type);
    type.registerDetectors(new FacetDetectorRegistryEx<C>(null, detectorRegistry));
    if (detectorRegistry.hasDetectors()) {
      myFacetTypesWithDetectors.add(type);
      myDetectedFacetManager.registerListeners(type);
    }
  }

  @Nullable
  public DisabledAutodetectionByTypeElement getDisabledAutodetectionState(@NotNull FacetType<?,?> type) {
    return myDisabledAutodetectionInfo.findElement(type.getStringId());
  }

  public DisabledAutodetectionInfo getState() {
    return myDisabledAutodetectionInfo;
  }

  public void loadState(final DisabledAutodetectionInfo state) {
    myDisabledAutodetectionInfo = state;
  }

  public void processFile(VirtualFile virtualFile) {
    if (!virtualFile.isValid() || virtualFile.isDirectory() || myProject.isDisposed()
        || !virtualFile.exists() || !myFileIndex.getProjectFileIndex().isInContent(virtualFile)) return;
    
    FileType fileType = virtualFile.getFileType();
    Collection<FacetDetectorWrapper> detectors = myDetectors.get(fileType);
    if (detectors == null) return;

    List<FacetInfo2<Module>> facets = null;
    for (FacetDetectorWrapper<?,?,?,?> detector : detectors) {
      facets = process(virtualFile, detector, facets);
    }

    String url = virtualFile.getUrl();
    FacetDetectionIndexEntry indexEntry = myFileIndex.getIndexEntry(url);
    if (indexEntry == null) {
      indexEntry = new FacetDetectionIndexEntry(virtualFile.getTimeStamp());
    }

    Collection<Integer> removed = indexEntry.update(myFacetPointersManager, facets);
    myFileIndex.putIndexEntry(url, indexEntry);

    if (removed != null) {
      removeObsoleteFacets(removed);
    }
  }

  public void removeObsoleteFacets(final Collection<Integer> ids) {
    for (Integer id : ids) {
      Set<String> urls = myFileIndex.getFiles(id);
      if (urls == null || urls.isEmpty()) {
        myDetectedFacetSet.removeDetectedFacetWithSubFacets(id);
      }
    }
  }

  public ProjectFacetInfoSet getDetectedFacetSet() {
    return myDetectedFacetSet;
  }

  private List<FacetInfo2<Module>> process(final VirtualFile virtualFile, final FacetDetectorWrapper<?, ?, ?, ?> detector,
                                                         List<FacetInfo2<Module>> facets) {
    if (!myDetectionInProgress && detector.getVirtualFileFilter().accept(virtualFile)) {
      try {
        myDetectionInProgress = true;
        if (!ProjectFileVersion.getInstance(myProject).isFacetAdditionEnabled(detector.getFacetType().getId(), false)) {
          return facets;
        }
        FacetInfo2<Module> facet = detector.detectFacet(virtualFile, myPsiManager);

        if (facet != null) {
          if (facets == null) {
            facets = new SmartList<FacetInfo2<Module>>();
          }
          facets.add(facet);
        }
      }
      finally {
        myDetectionInProgress = false;
      }
    }
    return facets;
  }

  public void disposeComponent() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !myProject.isDefault()) {
      dispose();
    }
  }

  public void dispose() {
    if (myFileIndex != null) {
      myDetectedFacetSet.saveDetectedFacets(FacetDetectionIndex.getDetectedFacetsFile(myProject));
      myFileIndex.dispose();
    }
  }

  public boolean hasDetectors(@NotNull FacetType<?, ?> facetType) {
    return myFacetTypesWithDetectors.contains(facetType);
  }

  public void redetectFacets() {
    myEnableAutodetectionWorker.redetectFacets();
  }

  @TestOnly
  public EnableAutodetectionWorker getEnableAutodetectionWorker() {
    return myEnableAutodetectionWorker;
  }

  public boolean isAutodetectionEnabled(final Module module, final FacetType facetType, final String url) {
    return !myDisabledAutodetectionInfo.isDisabled(facetType.getStringId(), module.getName(), url);
  }

  private void queueUpdate(final PsiFile psiFile) {
    if (!myDetectors.keySet().contains(psiFile.getFileType())) return;

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile != null) {
      queueUpdate(virtualFile);
    }
  }

  public void queueUpdate(final VirtualFile file) {
    Update update = new Update("file:" + file.getUrl()) {
      public void run() {
        processFile(file);
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
    }
    else {
      myMergingUpdateQueue.queue(update);
    }
  }

  @Nullable
  public Set<String> getFiles(final Facet facet) {
    return myFileIndex.getFiles(facet);
  }

  public void removeFacetFromCache(final Facet facet) {
    myFileIndex.removeFacetFromCache(myFacetPointersManager.create(facet));
  }

  public Set<FileType> getFileTypes(final Set<FacetType> facetTypes) {
    THashSet<FileType> fileTypes = new THashSet<FileType>();
    for (FileType type : myDetectors.keySet()) {
      Collection<FacetDetectorWrapper> detectorWrappers = myDetectors.get(type);
      if (detectorWrappers != null) {
        for (FacetDetectorWrapper detectorWrapper : detectorWrappers) {
          if (facetTypes.contains(detectorWrapper.getFacetType())) {
            fileTypes.add(type);
            break;
          }
        }
      }
    }
    return fileTypes;
  }

  public void disableAutodetectionInModule(final FacetType type, final Module module) {
    getState().addDisabled(type.getStringId(), module.getName());
  }

  public void disableAutodetectionInProject() {
    for (FacetType facetType : FacetTypeRegistry.getInstance().getFacetTypes()) {
      disableAutodetectionInProject(facetType);
    }
  }

  public void disableAutodetectionInProject(final FacetType type) {
    getState().addDisabled(type.getStringId());
  }

  public void disableAutodetectionInDirs(@NotNull Module module, @NotNull String... dirUrls) {
    for (FacetType<?, ?> facetType : myFacetTypesWithDetectors) {
      for (String dirUrl : dirUrls) {
        getState().addDisabled(facetType.getStringId(), module.getName(), dirUrl, true);
      }
    }
  }

  public void disableAutodetectionInFiles(@NotNull final FacetType type, @NotNull final Module module, @NotNull final String... fileUrls) {
    getState().addDisabled(type.getStringId(), module.getName(), fileUrls);
  }

  public void setDisabledAutodetectionState(final @NotNull FacetType<?, ?> facetType, final @Nullable DisabledAutodetectionByTypeElement element) {
    String id = facetType.getStringId();
    DisabledAutodetectionByTypeElement oldElement = myDisabledAutodetectionInfo.findElement(id);
    myEnableAutodetectionWorker.queueChanges(facetType, oldElement, element);
    myDisabledAutodetectionInfo.replaceElement(id, element);
  }

  public DetectedFacetManager getDetectedFacetManager() {
    return myDetectedFacetManager;
  }

  @Nullable 
  public FacetDetector<?,?> findDetector(final String detectorId) {
    return myId2Detector.get(detectorId);
  }

  public FacetDetectionIndex getFileIndex() {
    return myFileIndex;
  }

  private class FacetOnTheFlyDetectorRegistryImpl<C extends FacetConfiguration, F extends Facet<C>> implements FacetOnTheFlyDetectorRegistry<C> {
    private final FacetType<F, C> myType;
    private boolean myHasDetectors;

    public FacetOnTheFlyDetectorRegistryImpl(final FacetType<F, C> type) {
      myType = type;
    }

    public void register(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
      myHasDetectors = true;
      myId2Detector.put(facetDetector.getId(), facetDetector);
      myDetectors.put(fileType, new FacetByVirtualFileDetectorWrapper<C, F, FacetConfiguration>(myDetectedFacetSet, myType, FacetAutodetectingManagerImpl.this, virtualFileFilter, facetDetector));
    }

    public <U extends FacetConfiguration> void register(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter,
                                                        @NotNull final Condition<PsiFile> psiFileFilter, @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                                        final UnderlyingFacetSelector<VirtualFile, U> selector) {
      myHasDetectors = true;
      myId2Detector.put(facetDetector.getId(), facetDetector);
      myDetectors.put(fileType, new FacetByPsiFileDetectorWrapper<C, F, U>(myDetectedFacetSet, myType, FacetAutodetectingManagerImpl.this, virtualFileFilter, facetDetector, psiFileFilter, selector));
    }

    public boolean hasDetectors() {
      return myHasDetectors;
    }
  }

  private class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childAdded(final PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      if (child instanceof PsiFile) {
        queueUpdate((PsiFile)child);
      }
      else {
        processChangedElement(event);
      }
    }

    private void processChangedElement(final PsiTreeChangeEvent event) {
      PsiFile psiFile = event.getFile();
      if (psiFile != null) {
        queueUpdate(psiFile);
      }
    }

    public void childRemoved(final PsiTreeChangeEvent event) {
      PsiElement child = event.getChild();
      if (child instanceof PsiFile) {
        VirtualFile virtualFile = ((PsiFile)child).getVirtualFile();
        if (virtualFile != null) {
          myFileIndex.removeIndexEntry(virtualFile);
        }
      }
      else {
        processChangedElement(event);
      }
    }

    public void childReplaced(final PsiTreeChangeEvent event) {
      processChangedElement(event);
    }

    public void childMoved(final PsiTreeChangeEvent event) {
      processChangedElement(event);
    }
  }
}
