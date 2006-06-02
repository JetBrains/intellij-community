/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.profile.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.profile.ApplicationProfileManager;
import com.intellij.profile.Profile;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.ProjectProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: anna
 * Date: 30-May-2006
 */
public class ProfilesComboBox extends JComboBox {

  public static final String USE_GLOBAL_PROFILE = InspectionsBundle.message("profile.project.settings.disable.text");
  private boolean myFrozenProfilesCombo;

  private Condition<ModifiableModel> myUpdateCallback;


  public void setUpdateCallback(final Condition<ModifiableModel> updateCallback) {
    myUpdateCallback = updateCallback;
  }

  public void createProfilesCombo(final Profile selectedProfile, final ProfileManager profileManager) {
    reloadProfiles(selectedProfile, profileManager);

    setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Profile) {
          final Profile profile = (Profile)value;
          setText(profile.getName());
          setIcon(profile.isLocal() ? Profile.LOCAL_PROFILE : Profile.PROJECT_PROFILE);
        }
        else if (value instanceof String) {
          setText((String)value);
        }
        return rendererComponent;
      }
    });
    addItemListener(new ItemListener() {
      private Object myDeselectedItem = null;

      public void itemStateChanged(ItemEvent e) {
        if (myFrozenProfilesCombo) return; //do not update during reloading
        if (ItemEvent.SELECTED == e.getStateChange()) {
          final Object item = e.getItem();
          if (profileManager instanceof ProjectProfileManager && item instanceof Profile && ((Profile)item).isLocal()) {
            if (Messages.showOkCancelDialog(InspectionsBundle.message("inspection.new.profile.ide.to.project.warning.message"),
                                            InspectionsBundle.message("inspection.new.profile.ide.to.project.warning.title"),
                                            Messages.getErrorIcon()) == DialogWrapper.OK_EXIT_CODE) {
              final String newName = Messages.showInputDialog(InspectionsBundle.message("inspection.new.profile.text"),
                                                              InspectionsBundle.message("inspection.new.profile.dialog.title"),
                                                              Messages.getInformationIcon());
              final Object selectedItem = getSelectedItem();
              if (newName != null && newName.length() > 0 && selectedItem instanceof Profile) {
                if (ArrayUtil.find(profileManager.getAvailableProfileNames(), newName) == -1 &&
                    ArrayUtil.find(InspectionProfileManager.getInstance().getAvailableProfileNames(), newName) == -1) {
                  saveNewProjectProfile(newName, (Profile)selectedItem);
                  return;
                }
                else {
                  Messages.showErrorDialog(InspectionsBundle.message("inspection.unable.to.create.profile.message", newName),
                                           InspectionsBundle.message("inspection.unable.to.create.profile.dialog.title"));
                }
              }
            }
            setSelectedItem(myDeselectedItem);
          }
        }
        else {
          myDeselectedItem = e.getItem();
        }
      }
    });
  }

  private void saveNewProjectProfile(final String newName, final Profile profile) {
    InspectionProfileImpl inspectionProfile = new InspectionProfileImpl(newName, null, InspectionToolRegistrar.getInstance());
    final ModifiableModel profileModifiableModel = inspectionProfile.getModifiableModel();
    profileModifiableModel.copyFrom(profile);
    profileModifiableModel.setLocal(false);
    profileModifiableModel.setName(newName);
    ((DefaultComboBoxModel)getModel()).addElement(profileModifiableModel);
    setSelectedItem(profileModifiableModel);
    if (myUpdateCallback != null){
      myUpdateCallback.value(profileModifiableModel);
    }
  }


  private void reloadProfiles(Profile selectedProfile, ProfileManager profileManager) {
    Set<Profile> availableProfiles = new TreeSet<Profile>(profileManager.getProfiles().values());
    if (profileManager instanceof ProjectProfileManager) {
      availableProfiles.add(((InspectionProjectProfileManager)profileManager).getProjectProfileImpl());
      availableProfiles.addAll(InspectionProfileManager.getInstance().getProfiles().values());
    }
    Set<Profile> preparedProfiles = new TreeSet<Profile>();
    for (Profile profile : availableProfiles) {
      preparedProfiles.add(((InspectionProfileImpl)profile).getModifiableModel());
    }
    reloadProfiles(profileManager, preparedProfiles, selectedProfile);
  }

  public void reloadProfiles(final ProfileManager profileManager, final Set<Profile> availableProfiles, final Profile selectedProfile) {
    reloadProfiles(profileManager, true, availableProfiles, selectedProfile);
  }


  public void reloadProfiles(final ProfileManager profileManager, final boolean noneItemAppearance, final Set<Profile> availableProfiles, final Profile selectedProfile) {
    myFrozenProfilesCombo = true;
    Object oldSelection = getSelectedItem();
    final DefaultComboBoxModel model = (DefaultComboBoxModel)getModel();
    model.removeAllElements();
    if (noneItemAppearance && profileManager instanceof ProjectProfileManager) {
      model.addElement(USE_GLOBAL_PROFILE);
    }
    for (Profile profile : availableProfiles) {
      model.addElement(profile);
    }
    if (selectedProfile != null && ((selectedProfile.isLocal() && profileManager instanceof ApplicationProfileManager) ||
                                    (!selectedProfile.isLocal() && profileManager instanceof ProjectProfileManager))) {
      setSelectedItem(selectedProfile);
    }
    else {
      final int index = model.getIndexOf(oldSelection);
      if (index != -1) {
        setSelectedIndex(index);
      }
      else if (model.getSize() > 0) {
        setSelectedIndex(0);
      }
    }
    myFrozenProfilesCombo = false;
  }
}
