/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.*;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.impl.autodetecting.facetsTree.DetectedFacetsDialog;
import com.intellij.facet.impl.autodetecting.model.DetectedFacetInfo;
import com.intellij.facet.impl.autodetecting.model.FacetInfo2;
import com.intellij.facet.impl.autodetecting.model.ProjectFacetInfoSet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class DetectedFacetManager implements Disposable {
  public static final Icon FACET_DETECTED_ICON = IconLoader.getIcon("/ide/facetDetected.png");
  private static final int NOTIFICATION_DELAY = 200;
  private final JBPopupListener myNotificationPopupListener = new JBPopupAdapter() {
    public void onClosed(final JBPopup popup) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myNotificationPopup = null;
          firePendingNotifications();
        }
      });
    }
  };
  private final Project myProject;
  private final FacetAutodetectingManagerImpl myAutodetectingManager;
  private final ProjectWideFacetListenersRegistry myProjectWideFacetListenersRegistry;
  private AttentionComponent myAttentionComponent;
  private StatusBar myStatusBar;
  private boolean myUIInitialized;
  private final Set<DetectedFacetInfo<Module>> myPendingNewFacets = new HashSet<DetectedFacetInfo<Module>>();
  private JBPopup myNotificationPopup;
  private final Alarm myNotificationAlarm = new Alarm();
  private final ProjectFacetInfoSet myDetectedFacetSet;

  public DetectedFacetManager(final Project project, final FacetAutodetectingManagerImpl autodetectingManager,
                              final ProjectFacetInfoSet detectedFacetSet) {
    myDetectedFacetSet = detectedFacetSet;
    myProjectWideFacetListenersRegistry = ProjectWideFacetListenersRegistry.getInstance(project);
    myProject = project;
    myAutodetectingManager = autodetectingManager;
    myDetectedFacetSet.addListener(new ProjectFacetInfoSet.DetectedFacetListener() {
      public void facetDetected(final DetectedFacetInfo<Module> info) {
        onDetectedFacetChanged(Collections.singletonList(info), Collections.<DetectedFacetInfo<Module>>emptyList());
      }

      public void facetRemoved(final DetectedFacetInfo<Module> info) {
        myAutodetectingManager.getFileIndex().removeFromIndex(info);
        onDetectedFacetChanged(Collections.<DetectedFacetInfo<Module>>emptyList(), Collections.singletonList(info));
      }
    });
    Disposer.register(myProject, this);
  }

  public <F extends Facet<C>, C extends FacetConfiguration> void registerListeners(final FacetType<F, C> type) {
    myProjectWideFacetListenersRegistry.registerListener(type.getId(), new MyProjectWideFacetListener<F, C>(), this);
  }

  public void onDetectedFacetChanged(@NotNull final Collection<DetectedFacetInfo<Module>> added, @NotNull final Collection<DetectedFacetInfo<Module>> removed) {
    if (!myUIInitialized) return;

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final boolean stopBlinking = myDetectedFacetSet.getAllDetectedFacets().isEmpty();

      Runnable runnable = new Runnable() {
        public void run() {
          if (isDisposed()) return;

          if (!removed.isEmpty() && myNotificationPopup != null) {
            myNotificationPopup.cancel();
          }

          myPendingNewFacets.addAll(added);
          queueNotificationPopup();

          if (stopBlinking) {
            myAttentionComponent.stopBlinking();
          }
          if (!added.isEmpty()) {
            myAttentionComponent.startBlinking();
          }
        }
      };
      if (ApplicationManager.getApplication().isDispatchThread() && ApplicationManager.getApplication().getCurrentModalityState() == ModalityState.NON_MODAL) {
        runnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL);
      }
    }
  }

  private void queueNotificationPopup() {
    myNotificationAlarm.cancelAllRequests();
    myNotificationAlarm.addRequest(new Runnable() {
      public void run() {
        firePendingNotifications();
      }
    }, NOTIFICATION_DELAY);
  }

  public boolean isDisposed() {
    return myProject.isDisposed() || !myUIInitialized;
  }

  private void firePendingNotifications() {
    if (myPendingNewFacets.isEmpty() || myProject.isDisposed()) return;

    List<DetectedFacetInfo<Module>> newFacets = new ArrayList<DetectedFacetInfo<Module>>();
    for (DetectedFacetInfo<Module> newFacet : myPendingNewFacets) {
      newFacets.add(newFacet);
    }
    myPendingNewFacets.clear();
    fireNotification(newFacets);
  }

  private void fireNotification(final List<DetectedFacetInfo<Module>> newFacets) {
    if (newFacets.isEmpty()) {
      return;
    }

    if (myNotificationPopup == null) {
      HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> filesMap = getFilesMap(newFacets);
      myNotificationPopup = ImplicitFacetsComponent.fireNotificationPopup(newFacets, myStatusBar, myNotificationPopupListener, this, filesMap);
    }
    else {
      myPendingNewFacets.addAll(newFacets);
    }
  }

  public void dispose() {
  }

  public void initUI() {
    myAttentionComponent = new AttentionComponent(this);
    myStatusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (myStatusBar == null) return;

    myStatusBar.addCustomIndicationComponent(myAttentionComponent);
    myUIInitialized = true;
    onDetectedFacetChanged(myDetectedFacetSet.getAllDetectedFacets(), Collections.<DetectedFacetInfo<Module>>emptyList());
  }

  public void disposeUI() {
    if (!myUIInitialized) return;

    myNotificationPopup = null;
    myNotificationAlarm.cancelAllRequests();
    myStatusBar.removeCustomIndicationComponent(myAttentionComponent);
    myAttentionComponent.disposeUI();
    myAttentionComponent = null;
  }

  public void disableDetectionInFile(final DetectedFacetInfo<Module> detectedFacet) {
    Collection<String> urls = myAutodetectingManager.getFileIndex().getFiles(detectedFacet.getId());
    if (urls != null && !urls.isEmpty()) {
      myAutodetectingManager.disableAutodetectionInFiles(detectedFacet.getFacetType(), detectedFacet.getModule(), urls.toArray(new String[urls.size()]));
    }
    myAutodetectingManager.getDetectedFacetSet().removeFacetInfo(detectedFacet);
  }

  public void disableDetectionInModule(final DetectedFacetInfo<Module> detectedFacetInfo) {
    disableDetectionInModule(detectedFacetInfo.getFacetType(), detectedFacetInfo.getModule());
  }

  public void disableDetectionInModule(final FacetType type, final Module module) {
    myAutodetectingManager.disableAutodetectionInModule(type, module);
    myAutodetectingManager.getDetectedFacetSet().removeDetectedFacets(type.getId(), module);
  }

  public void disableDetectionInProject(final DetectedFacetInfo<Module> facetInfo) {
    FacetType facetType = facetInfo.getFacetType();
    myAutodetectingManager.disableAutodetectionInProject(facetType);
    myAutodetectingManager.getDetectedFacetSet().removeDetectedFacets(facetType.getId());
  }

  public void computeImplicitFacetsAndShowDialog() {
    showImplicitFacetsDialog();
  }

  public void showImplicitFacetsDialog() {
    List<DetectedFacetInfo<Module>> detectedFacets = new ArrayList<DetectedFacetInfo<Module>>(myDetectedFacetSet.getAllDetectedFacets());
    HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> filesMap = getFilesMap(detectedFacets);

    if (!detectedFacets.isEmpty()) {
      DetectedFacetsDialog dialog = new DetectedFacetsDialog(myProject, this, detectedFacets, filesMap);
      dialog.show();
    }
  }

  private HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> getFilesMap(final List<DetectedFacetInfo<Module>> detectedFacets) {
    HashMap<DetectedFacetInfo<Module>, List<VirtualFile>> filesMap = new HashMap<DetectedFacetInfo<Module>, List<VirtualFile>>();
    Iterator<DetectedFacetInfo<Module>> iterator = detectedFacets.iterator();
    while (iterator.hasNext()) {
      DetectedFacetInfo<Module> detected = iterator.next();
      Set<String> urls = myAutodetectingManager.getFileIndex().getFiles(detected.getId());
      List<VirtualFile> files = new ArrayList<VirtualFile>();
      if (urls != null) {
        for (String url : urls) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
          if (file != null) {
            files.add(file);
          }
        }
      }
      if (!files.isEmpty()) {
        filesMap.put(detected, files);
      }
      else {
        iterator.remove();
      }
    }
    return filesMap;
  }

  public Facet createFacet(final DetectedFacetInfo<Module> info, final Facet underlyingFacet) {
    final Module module = info.getModule();

    FacetType<?, ?> type = info.getFacetType();
    final Facet facet = createFacet(info, module, underlyingFacet, type);
    ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
    ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    final FacetDetector detector = myAutodetectingManager.findDetector(info.getDetectorId());
    if (detector != null) {
      detector.beforeFacetAdded(facet, model, rootModel);
    }
    model.addFacet(facet);
    if (rootModel.isChanged()) {
      rootModel.commit();
    }
    else {
      rootModel.dispose();
    }

    model.commit();
    myAutodetectingManager.getFileIndex().updateIndexEntryForCreatedFacet(info, facet);
    myAutodetectingManager.getDetectedFacetSet().removeFacetInfo(info);
    if (detector != null) {
      detector.afterFacetAdded(facet);
    }
    return facet;
  }

  private static <C extends FacetConfiguration, F extends Facet> Facet createFacet(final DetectedFacetInfo<Module> info, final Module module, final Facet underlyingFacet,
                                                                  final FacetType<F, C> facetType) {
    return FacetManager.getInstance(module).createFacet(facetType, info.getFacetName(), (C)info.getConfiguration(), underlyingFacet);
  }

  private class MyProjectWideFacetListener<F extends Facet<C>, C extends FacetConfiguration> extends ProjectWideFacetAdapter<F> {
    @Override
    public void facetAdded(final F facet) {
      Map<C, FacetInfo2<Module>> map = myDetectedFacetSet.getConfigurations((FacetTypeId<F>)facet.getTypeId(), facet.getModule());
      Collection<FacetInfo2<Module>> infos = map.values();
      Set<VirtualFile> files = new HashSet<VirtualFile>();
      for (FacetInfo2<Module> info : infos) {
        if (info instanceof DetectedFacetInfo) {
          Set<String> urls = myAutodetectingManager.getFileIndex().getFiles(((DetectedFacetInfo)info).getId());
          if (urls != null) {
            for (String url : urls) {
              VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
              if (file != null) {
                files.add(file);
              }
            }
          }
        }
      }
      for (VirtualFile file : files) {
        myAutodetectingManager.processFile(file);
      }
    }

    @Override
    public void beforeFacetRemoved(final F facet) {
      Set<String> files = myAutodetectingManager.getFiles(facet);
      if (files != null) {
        myAutodetectingManager.disableAutodetectionInFiles(facet.getType(), facet.getModule(), files.toArray(new String[files.size()]));
      }
      myAutodetectingManager.removeFacetFromCache(facet);
    }
  }

  private static class AttentionComponent extends SimpleColoredComponent {
    private static final int BLINKING_DELAY = 300;
    private Alarm myBlinkingAlarm = new Alarm();
    private boolean myIconVisible;
    private boolean myActive;
    private DetectedFacetManager myManager;

    public AttentionComponent(final DetectedFacetManager manager) {
      myManager = manager;
      addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          if (myActive && !e.isPopupTrigger() && myManager != null) {
            myManager.showImplicitFacetsDialog();
          }
        }
      });
    }

    public void startBlinking() {
      if (myActive) return;
      myBlinkingAlarm.cancelAllRequests();
      myIconVisible = true;
      myActive = true;
      showIcon();
      myBlinkingAlarm.addRequest(new Runnable() {
        public void run() {
          if (!myActive) return;

          if (myIconVisible) {
            hideIcon();
          }
          else {
            showIcon();
          }
          myIconVisible = !myIconVisible;
          myBlinkingAlarm.addRequest(this, BLINKING_DELAY);
        }
      }, BLINKING_DELAY);
    }

    public void stopBlinking() {
      if (!myActive) return;
      myActive = false;
      myBlinkingAlarm.cancelAllRequests();
      hideIcon();
    }

    private void showIcon() {
      setIcon(FACET_DETECTED_ICON);
      repaint();
    }

    private void hideIcon() {
      clear();
      repaint();
    }

    public void disposeUI() {
      myManager = null;
      myBlinkingAlarm.cancelAllRequests();
    }

    public Dimension getPreferredSize() {
      return new Dimension(18, 18);
    }
  }

}
