package com.intellij.ide;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocatorEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Alarm;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public class SaveAndSyncHandler implements ApplicationComponent,PropertyChangeListener{
  private static final Logger LOG=Logger.getInstance("#com.intellij.ide.SaveAndSyncHandler");

  private boolean myShouldSynchronize;
  private final Alarm mySyncAlarm;
  private Runnable myIdleListener;
  private PropertyChangeListener myGeneralSettingsListener;
  private Set<Runnable> myActivationActions = new HashSet<Runnable>();
  private Set<Runnable> myDeactivationActions = new HashSet<Runnable>();

  public static SaveAndSyncHandler getInstance() {
    return ApplicationManager.getApplication().getComponent(SaveAndSyncHandler.class);
  }

  public SaveAndSyncHandler(final FileDocumentManager fileDocumentManager, final GeneralSettings generalSettings){
    myShouldSynchronize = false;
    mySyncAlarm = new Alarm();

    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    focusManager.addPropertyChangeListener("activeWindow",this);
    focusManager.addPropertyChangeListener("focusedWindow",this);
    focusManager.addPropertyChangeListener("focusOwner",this);

    myIdleListener=new Runnable(){
      public void run(){
        if (generalSettings.isAutoSaveIfInactive() && canSyncOrSave()) {
          fileDocumentManager.saveAllDocuments();
        }
      }
    };

    IdeEventQueue.getInstance().addIdleListener(
      myIdleListener,
      generalSettings.getInactiveTimeout()*1000
    );

    myGeneralSettingsListener=new PropertyChangeListener(){
      public void propertyChange(PropertyChangeEvent e){
        if(GeneralSettings.PROP_INACTIVE_TIMEOUT.equals(e.getPropertyName())){
          IdeEventQueue eventQueue=IdeEventQueue.getInstance();
          eventQueue.removeIdleListener(myIdleListener);
          Integer timeout=(Integer)e.getNewValue();
          eventQueue.addIdleListener(myIdleListener,timeout.intValue()*1000);
        }
      }
    };
    generalSettings.addPropertyChangeListener(myGeneralSettingsListener);

    registerActivationAction(new Runnable() {
      public void run() {
        refreshFiles();
      }
    }, true);
    registerActivationAction(new Runnable() {
      public void run() {
        saveProjectsAndDocuments();
      }
    }, false);
  }

  public String getComponentName() {
    return "SaveAndSyncHandler";
  }

  public void initComponent() { }

  public void disposeComponent() {
    GeneralSettings.getInstance().removePropertyChangeListener(myGeneralSettingsListener);
    IdeEventQueue.getInstance().removeIdleListener(myIdleListener);
  }

  private boolean canSyncOrSave(){
    if (LaterInvocatorEx.isInModalContext()) return false;
    if (ProgressManager.getInstance().hasModalProgressIndicator()) return false;
    /*
    if (ProjectManagerEx.getInstanceEx().isOpeningProject()) return false;
    */
    return true;
  }

  public void propertyChange(PropertyChangeEvent e) {
    KeyboardFocusManager focusManager = (KeyboardFocusManager)e.getSource();
    Window activeWindow = focusManager.getActiveWindow();

    if (activeWindow != null) {
      mySyncAlarm.cancelAllRequests();
      if (myShouldSynchronize) {
        myShouldSynchronize = false;
        if (canSyncOrSave()) {
          synchronizeOnActivation();
        }
      }
      return;
    }
    else{
      mySyncAlarm.cancelAllRequests();
      mySyncAlarm.addRequest(
        new Runnable() {
          public void run() {
            myShouldSynchronize=true;
            if (canSyncOrSave()) {
              synchronizeOnDeactivation();
            }
          }
        },
        200
      );
    }
  }

  public synchronized void registerActivationAction(Runnable action, boolean onActivation) {
    final HashSet<Runnable> newContainer = new HashSet<Runnable>(Math.max(myActivationActions.size(), myDeactivationActions.size()));
    newContainer.add(action);
    if (onActivation) {
      newContainer.addAll(myActivationActions);
      myActivationActions = newContainer;
    }
    else {
      newContainer.addAll(myDeactivationActions);
      myDeactivationActions = newContainer;
    }
  }
  public synchronized void unregisterActivationAction(Runnable action, boolean onActivation) {
    if (onActivation) {
      final HashSet<Runnable> newContainer = new HashSet<Runnable>(myActivationActions);
      newContainer.remove(action);
      myActivationActions = newContainer;
    }
    else {
      final HashSet<Runnable> newContainer = new HashSet<Runnable>(myDeactivationActions);
      newContainer.remove(action);
      myDeactivationActions = newContainer;
    }
  }

  private void synchronizeOnDeactivation(){
    for (Iterator iterator = myDeactivationActions.iterator(); iterator.hasNext();) {
      Runnable action = (Runnable)iterator.next();
      action.run();
    }
  }

  private void saveProjectsAndDocuments() {
    if(LOG.isDebugEnabled()){
      LOG.debug("enter: save()");
    }
    if(GeneralSettings.getInstance().isSaveOnFrameDeactivation()){
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      if(LOG.isDebugEnabled()){
        LOG.debug("save project: "+project);
      }
      project.save();
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("save application settings");
    }
    ApplicationManagerEx.getApplicationEx().saveSettings();
    if(LOG.isDebugEnabled()){
      LOG.debug("exit: save()");
    }
  }

  private void synchronizeOnActivation(){
    for (Iterator iterator = myActivationActions.iterator(); iterator.hasNext();) {
      Runnable action = (Runnable)iterator.next();
      action.run();
    }
  }

  private void refreshFiles() {
    if(LOG.isDebugEnabled()){
      LOG.debug("enter: synchronize()");
    }
    if (GeneralSettings.getInstance().isSyncOnFrameActivation()) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("refresh VFS");
      }
      VirtualFileManager.getInstance().refresh(true);
    }else{ // referesh only opened files
      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      for (int i = 0; i < openProjects.length; i++) {
        Project project = openProjects[i];
        VirtualFile[] file=FileEditorManager.getInstance(project).getSelectedFiles();
        for (int j = 0; j < file.length; j++) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("refresh file: "+file);
          }
          file[j].refresh(true, false);
        }
      }
    }
    if(LOG.isDebugEnabled()){
      LOG.debug("exit: synchronize()");
    }
  }
}