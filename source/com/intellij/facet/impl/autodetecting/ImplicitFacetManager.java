/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.*;
import com.intellij.facet.impl.FacetUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.popup.NotificationPopup;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class ImplicitFacetManager implements Disposable {
  public static final Icon FACET_DETECTED_ICON = IconLoader.getIcon("/ide/facetDetected.png");
  private List<Facet> myImplicitFacets = new ArrayList<Facet>();
  private Project myProject;
  private final FacetAutodetectingManagerImpl myAutodetectingManager;
  private final ProjectWideFacetListenersRegistry myProjectWideFacetListenersRegistry;
  private AttentionComponent myAttentionComponent;
  private StatusBar myStatusBar;
  private boolean myUIInitialized;

  public ImplicitFacetManager(final Project project, final FacetAutodetectingManagerImpl autodetectingManager) {
    myProjectWideFacetListenersRegistry = ProjectWideFacetListenersRegistry.getInstance(project);
    myProject = project;
    myAutodetectingManager = autodetectingManager;
  }

  public <F extends Facet<C>, C extends FacetConfiguration> void registerListeners(final FacetType<F, C> type) {
    myProjectWideFacetListenersRegistry.registerListener(type.getId(), new MyProjectWideFacetListener<F, C>(), this);
  }

  public void onImplicitFacetChanged() {
    if (!myUIInitialized) return;

    List<Facet> implicitFacets = getImplicitFacets();

    if (!Comparing.haveEqualElements(myImplicitFacets, implicitFacets)) {
      Set<Facet> newFacets = new HashSet<Facet>(implicitFacets);
      newFacets.removeAll(myImplicitFacets);
      if (!newFacets.isEmpty()) {
        fireNotificationPopup(newFacets);
      }

      if (!myImplicitFacets.isEmpty() && implicitFacets.isEmpty()) {
        myAttentionComponent.stopBlinking();
      }
      if (myImplicitFacets.isEmpty() && !implicitFacets.isEmpty()) {
        myAttentionComponent.startBlinking();
      }
      myImplicitFacets = implicitFacets;
    }
  }

  private void showPopup(final MouseEvent e) {
    ImplicitFacetsComponent facetsComponent = createImplicitFacetsComponent(myImplicitFacets);
    JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(facetsComponent.getMainPanel(), null)
      .setForceHeavyweight(true)
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(true)
      .setLocateWithinScreenBounds(true)
      .createPopup();
    popup.showUnderneathOf(e.getComponent());
    facetsComponent.setContainingPopup(popup);
  }

  private void fireNotificationPopup(final Collection<Facet> facets) {
    ImplicitFacetsComponent implicitFacetsComponent = createImplicitFacetsComponent(facets);
    NotificationPopup popup = new NotificationPopup((JComponent)myStatusBar, implicitFacetsComponent.getMainPanel(), ImplicitFacetsComponent.BACKGROUND_COLOR);
    implicitFacetsComponent.setContainingPopup(popup.getPopup());
    //todo[nik] return popup instance from fireNotificationPopup
    //myStatusBar.fireNotificationPopup(implicitFacetsComponent.getMainPanel(), ImplicitFacetsComponent.BACKGROUND_COLOR);
  }

  private ImplicitFacetsComponent createImplicitFacetsComponent(final Collection<Facet> newFacets) {
    ImplicitFacetsComponent implicitFacetsComponent = new ImplicitFacetsComponent(this);
    for (Facet newFacet : newFacets) {
      Set<String> urls = myAutodetectingManager.getFiles(newFacet);
      if (urls != null) {
        implicitFacetsComponent.addFacetInfo(newFacet, urls);
      }
    }
    return implicitFacetsComponent;
  }

  private List<Facet> getImplicitFacets() {
    List<Facet> implicitFacets = new ArrayList<Facet>();
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      Facet[] facets = FacetManager.getInstance(module).getAllFacets();
      for (Facet facet : facets) {
        if (facet.isImplicit()) {
          implicitFacets.add(facet);
        }
      }
    }
    return implicitFacets;
  }

  public void dispose() {
  }

  public void initUI() {
    myAttentionComponent = new AttentionComponent();
    myStatusBar = WindowManager.getInstance().getStatusBar(myProject);
    myStatusBar.addCustomIndicationComponent(myAttentionComponent);
    myUIInitialized = true;
    onImplicitFacetChanged();
  }

  public void disposeUI() {
    myStatusBar.removeCustomIndicationComponent(myAttentionComponent);
  }

  public void configureFacet(final Facet facet) {
    facet.setImplicit(false);
    onImplicitFacetChanged();
    ModulesConfigurator.showFacetSettingsDialog(facet, null);
    onImplicitFacetChanged();
  }

  public void disableDetectionInModule(final Facet facet) {
    myAutodetectingManager.getState().addDisabled(facet.getType().getStringId(), facet.getModule().getName());
    FacetUtil.deleteImplicitFacets(facet.getModule(), facet.getTypeId());
  }

  public void disableDetectionInProject(final Facet facet) {
    myAutodetectingManager.getState().addDisabled(facet.getType().getStringId());
    FacetUtil.deleteImplicitFacets(facet.getModule().getProject(), facet.getTypeId());
  }

  private class MyProjectWideFacetListener<F extends Facet<C>, C extends FacetConfiguration> extends ProjectWideFacetAdapter<F> {
    public void facetConfigurationChanged(final F facet) {
      if (facet.isImplicit()) {
        facet.setImplicit(false);
        onImplicitFacetChanged();
      }
    }

    public void beforeFacetRemoved(final F facet) {
      Set<String> files = myAutodetectingManager.getFiles(facet);
      if (files != null) {
        myAutodetectingManager.getState().addDisabled(facet.getType().getStringId(), facet.getModule().getName(), files);
      }
      myAutodetectingManager.removeFacetFromCache(facet);
    }

    public void facetRemoved(final F facet) {
      onImplicitFacetChanged();
    }
  }

  private class AttentionComponent extends SimpleColoredComponent {
    private static final int BLINKING_DELAY = 300;
    private Alarm myBlinkingAlarm = new Alarm();
    private boolean myIconVisible;
    private boolean myActive;

    public AttentionComponent() {
      addMouseListener(new MouseAdapter() {
        public void mouseClicked(final MouseEvent e) {
          if (myActive && !e.isPopupTrigger()) {
            showPopup(e);
          }
        }
      });
    }

    public void startBlinking() {
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

    public Dimension getPreferredSize() {
      return new Dimension(18, 18);
    }
  }

}
