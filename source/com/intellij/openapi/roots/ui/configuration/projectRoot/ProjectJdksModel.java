/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.ui.NotifiableSdkModel;
import com.intellij.openapi.projectRoots.ui.SdkEditor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ProjectJdksModel implements NotifiableSdkModel {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel");

  private HashMap<Sdk, Sdk> myProjectJdks = new HashMap<Sdk, Sdk>();
  private EventDispatcher<Listener> mySdkEventsDispatcher = EventDispatcher.create(SdkModel.Listener.class);

  private boolean myModified = false;

  private Sdk myProjectJdk;
  private boolean myInitialized = false;

  public static ProjectJdksModel getInstance(Project project){
    return ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
  }


  public Listener getMulticaster() {
    return mySdkEventsDispatcher.getMulticaster();
  }

  public Sdk[] getSdks() {
    return myProjectJdks.values().toArray(new Sdk[myProjectJdks.size()]);
  }

  @Nullable
  public Sdk findSdk(String sdkName) {
    for (Sdk projectJdk : myProjectJdks.values()) {
      if (Comparing.strEqual(projectJdk.getName(), sdkName)) return projectJdk;
    }
    return null;
  }

  public void addListener(Listener listener) {
    mySdkEventsDispatcher.addListener(listener);
  }

  public void removeListener(Listener listener) {
    mySdkEventsDispatcher.removeListener(listener);
  }

  public void reset(Project project) {
    myProjectJdks.clear();
    final Sdk[] projectJdks = ProjectJdkTable.getInstance().getAllJdks();
    for (Sdk jdk : projectJdks) {
      try {
        myProjectJdks.put(jdk, (Sdk)jdk.clone());
      }
      catch (CloneNotSupportedException e) {
        //can't be
      }
    }
    myProjectJdk = findSdk(ProjectRootManager.getInstance(project).getProjectJdkName());
    myModified = false;
    myInitialized = true;
  }

  public void disposeUIResources() {
    myProjectJdks.clear();
    myInitialized = false;
  }

  public HashMap<Sdk, Sdk> getProjectJdks() {
    return myProjectJdks;
  }

  public boolean isModified(){
    return myModified;
  }

  public void apply(MasterDetailsComponent configurable) throws ConfigurationException {
    String[] errorString = new String[1];
    if (!canApply(errorString, configurable)) {
      throw new ConfigurationException(errorString[0]);
    }
    final Sdk[] allFromTable = ProjectJdkTable.getInstance().getAllJdks();
    final ArrayList<Sdk> itemsInTable = new ArrayList<Sdk>();
    // Delete removed and fill itemsInTable
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        for (final Sdk tableItem : allFromTable) {
          if (myProjectJdks.containsKey(tableItem)) {
            itemsInTable.add(tableItem);
          }
          else {
            jdkTable.removeJdk(tableItem);
          }
        }
      }
    });
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // Now all removed items are deleted from table, itemsInTable contains all items in table
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        for (Sdk originalJdk : itemsInTable) {
          final Sdk modifiedJdk = myProjectJdks.get(originalJdk);
          LOG.assertTrue(modifiedJdk != null);
          jdkTable.updateJdk(originalJdk, modifiedJdk);
        }
        // Add new items to table
        final Sdk[] allJdks = jdkTable.getAllJdks();
        for (final Sdk projectJdk : myProjectJdks.keySet()) {
          LOG.assertTrue(projectJdk != null);
          if (ArrayUtil.find(allJdks, projectJdk) == -1) {
            jdkTable.addJdk(projectJdk);
          }
        }
      }
    });
    myModified = false;
  }

  private boolean canApply(String[] errorString, MasterDetailsComponent rootConfigurable) throws ConfigurationException {
    ArrayList<String> allNames = new ArrayList<String>();
    Sdk itemWithError = null;
    for (Sdk currItem : myProjectJdks.values()) {
      String currName = currItem.getName();
      if (currName.length() == 0) {
        itemWithError = currItem;
        errorString[0] = ProjectBundle.message("sdk.list.name.required.error");
        break;
      }
      if (allNames.contains(currName)) {
        itemWithError = currItem;
        errorString[0] = ProjectBundle.message("sdk.list.unique.name.required.error");
        break;
      }
      final SdkAdditionalData sdkAdditionalData = currItem.getSdkAdditionalData();
      if (sdkAdditionalData != null) {
        try {
          sdkAdditionalData.checkValid(this);
        }
        catch (ConfigurationException e) {
          final Object projectJdk = rootConfigurable.getSelectedObject();
          if (!(projectJdk instanceof Sdk) ||
              !Comparing.strEqual(((Sdk)projectJdk).getName(), currName)){ //do not leave current item with current name
            rootConfigurable.selectNodeInTree(currName);
          }
          throw new ConfigurationException(ProjectBundle.message("sdk.configuration.exception", currName) + " " + e.getMessage());
        }
      }
      allNames.add(currName);
    }
    if (itemWithError == null) return true;
    rootConfigurable.selectNodeInTree(itemWithError.getName());
    return false;
  }

  public void removeJdk(final Sdk editableObject) {
    Sdk projectJdk = null;
    for (Sdk jdk : myProjectJdks.keySet()) {
      if (myProjectJdks.get(jdk) == editableObject) {
        projectJdk = jdk;
        break;
      }
    }
    if (projectJdk != null) {
      myProjectJdks.remove(projectJdk);
      mySdkEventsDispatcher.getMulticaster().beforeSdkRemove(projectJdk);
      myModified = true;
    }
  }

  public void createAddActions(DefaultActionGroup group, final JComponent parent, final Consumer<Sdk> updateTree) {
    final SdkType[] types = ApplicationManager.getApplication().getComponents(SdkType.class);
    for (final SdkType type : types) {
      final AnAction addAction = new AnAction(type.getPresentableName(),
                                              null,
                                              type.getIconForAddAction()) {
          public void actionPerformed(AnActionEvent e) {
            doAdd(type, parent, updateTree);
          }
        };
      group.add(addAction);
    }
  }

  public void doAdd(final SdkType type, JComponent parent, final Consumer<Sdk> updateTree) {
    myModified = true;
    final String home = SdkEditor.selectSdkHome(parent, type);
    if (home == null) {
      return;
    }
    final Set<String> names = new HashSet<String>();
    for (Sdk jdk : myProjectJdks.values()) {
      names.add(jdk.getName());
    }
    final String suggestedName = type.suggestSdkName(null, home);
    String newSdkName = suggestedName;
    int i = 0;
    while (names.contains(newSdkName)) {
      newSdkName = suggestedName + " (" + (++i) + ")";
    }
    final ProjectJdkImpl newJdk = new ProjectJdkImpl(newSdkName, type);
    newJdk.setHomePath(home);

    if (!type.setupSdkPaths(newJdk, this)) return;

    if (newJdk.getVersionString() == null) {
       Messages.showMessageDialog(ProjectBundle.message("sdk.java.corrupt.error", home),
                                  ProjectBundle.message("sdk.java.corrupt.title"), Messages.getErrorIcon());
    }

    myProjectJdks.put(newJdk, newJdk);
    updateTree.consume(newJdk);
    mySdkEventsDispatcher.getMulticaster().sdkAdded(newJdk);
  }

  @Nullable
  public Sdk findSdk(@Nullable final Sdk modelJdk) {
    for (Sdk jdk : myProjectJdks.keySet()) {
      if (Comparing.equal(myProjectJdks.get(jdk), modelJdk)) return jdk;
    }
    return null;
  }

  @Nullable
  public Sdk getProjectJdk() {
    if (!myProjectJdks.containsValue(myProjectJdk)) return null;
    return myProjectJdk;
  }

  public void setProjectJdk(final Sdk projectJdk) {
    myProjectJdk = projectJdk;
  }

  public boolean isInitialized() {
    return myInitialized;
  }
}
