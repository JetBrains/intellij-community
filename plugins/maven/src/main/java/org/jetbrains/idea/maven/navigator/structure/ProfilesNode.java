// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.openapi.util.Pair;
import icons.MavenIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenProfileKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.jetbrains.idea.maven.project.MavenProjectBundle.message;

class ProfilesNode extends GroupNode {
  private final List<ProfileNode> myProfileNodes = new CopyOnWriteArrayList<>();

  ProfilesNode(MavenProjectsStructure structure, MavenSimpleNode parent) {
    super(structure, parent);
    getTemplatePresentation().setIcon(MavenIcons.ProfilesClosed);
  }

  @Override
  protected List<? extends MavenSimpleNode> doGetChildren() {
    return myProfileNodes;
  }

  @Override
  public String getName() {
    return message("view.node.profiles");
  }

  public void updateProfiles() {
    Collection<Pair<String, MavenProfileKind>> profiles = myMavenProjectsStructure.getProjectsManager().getProfilesWithStates();

    List<ProfileNode> newNodes = new ArrayList<>(profiles.size());
    for (Pair<String, MavenProfileKind> each : profiles) {
      ProfileNode node = findOrCreateNodeFor(each.first);
      node.setState(each.second);
      newNodes.add(node);
    }

    myProfileNodes.clear();
    myProfileNodes.addAll(newNodes);
    sort(myProfileNodes);
    childrenChanged();
  }

  @Override
  @Nullable
  @NonNls
  String getMenuId() {
    return "Maven.ProfilesMenu";
  }

  private ProfileNode findOrCreateNodeFor(String profileName) {
    for (ProfileNode each : myProfileNodes) {
      if (each.getProfileName().equals(profileName)) return each;
    }
    return new ProfileNode(myMavenProjectsStructure, this, profileName);
  }
}
