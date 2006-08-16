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
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * User: anna
 * Date: 05-Jun-2006
 */
public class ProjectJdksModel implements NotifiableSdkModel {
  private static final Logger LOG = Logger.getInstance("com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel");

  private TreeMap<ProjectJdk, ProjectJdk> myProjectJdks = new TreeMap<ProjectJdk, ProjectJdk>(new Comparator<ProjectJdk>() {
    public int compare(final ProjectJdk o1, final ProjectJdk o2) {
      if (o1 == null || o2 == null) return 0;
      final SdkType type1 = o1.getSdkType();
      final SdkType type2 = o2.getSdkType();
      final int typeComp = type1.getName().compareTo(type2.getName());
      if (typeComp != 0) {
        return typeComp;
      }
      return o1.getName().compareTo(o2.getName());
    }
  });
  private EventDispatcher<Listener> mySdkEventsDispatcher = EventDispatcher.create(SdkModel.Listener.class);

  private boolean myModified = false;

  private ProjectJdk myProjectJdk;
  private boolean myInitialized = false;

  public static ProjectJdksModel getInstance(Project project){
    return ProjectRootConfigurable.getInstance(project).getProjectJdksModel();
  }


  public Listener getMulticaster() {
    return mySdkEventsDispatcher.getMulticaster();
  }

  public Sdk[] getSdks() {
    return myProjectJdks.values().toArray(new Sdk[myProjectJdks.size()]);
  }

  @Nullable
  public Sdk findSdk(String sdkName) {
    for (ProjectJdk projectJdk : myProjectJdks.values()) {
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
    final ProjectJdk[] projectJdks = ProjectJdkTable.getInstance().getAllJdks();
    for (ProjectJdk jdk : projectJdks) {
      try {
        myProjectJdks.put(jdk, (ProjectJdk)jdk.clone());
      }
      catch (CloneNotSupportedException e) {
        //can't be
      }
    }
    myProjectJdk = (ProjectJdk)findSdk(ProjectRootManager.getInstance(project).getProjectJdkName());
    myModified = false;
    myInitialized = true;
  }

  public void disposeUIResources() {
    myProjectJdks.clear();
    myInitialized = false;
  }

  public TreeMap<ProjectJdk, ProjectJdk> getProjectJdks() {
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
    final ProjectJdk[] allFromTable = ProjectJdkTable.getInstance().getAllJdks();
    final ArrayList<ProjectJdk> itemsInTable = new ArrayList<ProjectJdk>();
    // Delete removed and fill itemsInTable
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
        for (final ProjectJdk tableItem : allFromTable) {
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
        for (ProjectJdk originalJdk : itemsInTable) {
          final ProjectJdk modifiedJdk = myProjectJdks.get(originalJdk);
          LOG.assertTrue(modifiedJdk != null);
          jdkTable.updateJdk(originalJdk, modifiedJdk);
        }
        // Add new items to table
        final ProjectJdk[] allJdks = jdkTable.getAllJdks();
        for (final ProjectJdk projectJdk : myProjectJdks.keySet()) {
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
    ProjectJdk itemWithError = null;
    for (ProjectJdk currItem : myProjectJdks.values()) {
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
          if (!(projectJdk instanceof ProjectJdk) ||
              !Comparing.strEqual(((ProjectJdk)projectJdk).getName(), currName)){ //do not leave current item with current name
            rootConfigurable.selectNodeInTree(currName);
          }
          throw e;
        }
      }
      allNames.add(currName);
    }
    if (itemWithError == null) return true;
    rootConfigurable.selectNodeInTree(itemWithError.getName());
    return false;
  }

  public void removeJdk(final ProjectJdk editableObject) {
    ProjectJdk projectJdk = null;
    for (ProjectJdk jdk : myProjectJdks.keySet()) {
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

  public void createAddActions(DefaultActionGroup group, final JComponent parent, final Consumer<ProjectJdk> updateTree) {
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

  public void doAdd(final SdkType type, JComponent parent, final Consumer<ProjectJdk> updateTree) {
    myModified = true;
    final String home = SdkEditor.selectSdkHome(parent, type);
    if (home == null) {
      return;
    }
    final Set<String> names = new HashSet<String>();
    for (ProjectJdk jdk : myProjectJdks.values()) {
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
    type.setupSdkPaths(newJdk);
    myProjectJdks.put(newJdk, newJdk);
    updateTree.consume(newJdk);
    mySdkEventsDispatcher.getMulticaster().sdkAdded(newJdk);
  }

  public ProjectJdk findSdk(@Nullable final ProjectJdk modelJdk) {
    for (ProjectJdk jdk : myProjectJdks.keySet()) {
      if (Comparing.equal(myProjectJdks.get(jdk), modelJdk)) return jdk;
    }
    return null;
  }

  @Nullable
  public ProjectJdk getProjectJdk() {
    if (!myProjectJdks.containsValue(myProjectJdk)) return null;
    return myProjectJdk;
  }

  public void setProjectJdk(final ProjectJdk projectJdk) {
    myProjectJdk = projectJdk;
  }

  public boolean isInitialized() {
    return myInitialized;
  }
}
