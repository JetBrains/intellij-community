// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomProfile;
import org.jetbrains.idea.maven.dom.model.MavenDomProfiles;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.model.MavenDomSettingsModel;
import org.jetbrains.idea.maven.model.MavenProfileKind;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class ProfileNode extends MavenSimpleNode {
  private final String myProfileName;
  private MavenProfileKind myState;

  ProfileNode(MavenProjectsStructure structure, ProfilesNode parent, String profileName) {
    super(structure, parent);
    myProfileName = profileName;
  }

  @Override
  public String getName() {
    return myProfileName;
  }

  public String getProfileName() {
    return myProfileName;
  }

  public MavenProfileKind getState() {
    return myState;
  }

  void setState(MavenProfileKind state) {
    myState = state;
  }

  @Override
  @Nullable
  @NonNls
  protected String getActionId() {
    return "Maven.ToggleProfile";
  }

  @Override
  @Nullable
  @NonNls
  String getMenuId() {
    return "Maven.ProfileMenu";
  }

  @Nullable
  @Override
  public Navigatable getNavigatable() {
    if (myProject == null) return null;
    final List<MavenDomProfile> profiles = new ArrayList<>();

    // search in "Per User Maven Settings" - %USER_HOME%/.m2/settings.xml
    // and in "Global Maven Settings" - %M2_HOME%/conf/settings.xml
    for (VirtualFile virtualFile : myMavenProjectsStructure.getProjectsManager().getGeneralSettings().getEffectiveSettingsFiles()) {
      if (virtualFile != null) {
        final MavenDomSettingsModel model = MavenDomUtil.getMavenDomModel(myProject, virtualFile, MavenDomSettingsModel.class);
        if (model != null) {
          addProfiles(profiles, model.getProfiles().getProfiles());
        }
      }
    }

    for (MavenProject mavenProject : myMavenProjectsStructure.getProjectsManager().getProjects()) {
      // search in "Profile descriptors" - located in project basedir (profiles.xml)
      final VirtualFile mavenProjectFile = mavenProject.getFile();
      final VirtualFile profilesXmlFile = MavenUtil.findProfilesXmlFile(mavenProjectFile);
      if (profilesXmlFile != null) {
        final MavenDomProfiles profilesModel = MavenDomUtil.getMavenDomProfilesModel(myProject, profilesXmlFile);
        if (profilesModel != null) {
          addProfiles(profiles, profilesModel.getProfiles());
        }
      }

      // search in "Per Project" - Defined in the POM itself (pom.xml)
      final MavenDomProjectModel projectModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProjectFile);
      if (projectModel != null) {
        addProfiles(profiles, projectModel.getProfiles().getProfiles());
      }
    }
    return getNavigatable(profiles);
  }

  private record ProfileWithName(MavenDomProfile profile, String name) {
  }

  private static Navigatable getNavigatable(@NotNull final List<MavenDomProfile> profiles) {
    if (profiles.size() > 1) {
      var profileUrls = new ArrayList<ProfileWithName>();
      for (var profile : profiles) {
        var element = profile.getXmlElement();
        if (null != element) {
          profileUrls.add(new ProfileWithName(profile, getPresentableUrl(element)));
        }
      }
      return new NavigatableAdapter() {
        @Override
        public void navigate(final boolean requestFocus) {
          JBPopupFactory.getInstance()
            .createPopupChooserBuilder(profileUrls)
            .setRenderer(new DefaultListCellRenderer() {
              @Override
              public Component getListCellRendererComponent(JList list,
                                                            Object value,
                                                            int index,
                                                            boolean isSelected,
                                                            boolean cellHasFocus) {
                Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(((ProfileWithName)value).name);
                return result;
              }
            })
            .setTitle(message("maven.notification.choose.file.to.open"))
            .setItemChosenCallback((value) -> {
              final Navigatable navigatable = getNavigatable(value.profile);
              if (navigatable != null) navigatable.navigate(requestFocus);
            }).createPopup().showInFocusCenter();
        }
      };
    }
    else {
      return getNavigatable(ContainerUtil.getFirstItem(profiles));
    }
  }

  @NlsSafe
  private static @NotNull String getPresentableUrl(@NotNull XmlElement xmlElement) {
    return ReadAction.nonBlocking(() -> xmlElement.getContainingFile().getVirtualFile().getPresentableUrl()).executeSynchronously();
  }

  @Nullable
  private static Navigatable getNavigatable(@Nullable final MavenDomProfile profile) {
    if (profile == null) return null;
    XmlElement xmlElement = profile.getId().getXmlElement();
    return xmlElement instanceof Navigatable ? (Navigatable)xmlElement : null;
  }

  private void addProfiles(@NotNull List<MavenDomProfile> result, @Nullable List<MavenDomProfile> profilesToAdd) {
    if (profilesToAdd == null) return;
    for (MavenDomProfile profile : profilesToAdd) {
      if (StringUtil.equals(profile.getId().getValue(), myProfileName)) {
        result.add(profile);
      }
    }
  }
}
