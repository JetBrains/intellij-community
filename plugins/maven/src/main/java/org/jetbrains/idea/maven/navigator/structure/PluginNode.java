// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.projectView.PresentationData;
import icons.MavenIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.utils.MavenPluginInfo;

@ApiStatus.Internal
public final class PluginNode extends GoalsGroupNode {
  private final MavenPlugin myPlugin;
  private MavenPluginInfo myPluginInfo;

  PluginNode(MavenProjectsStructure structure, PluginsNode parent, MavenPlugin plugin, MavenPluginInfo pluginInfo) {
    super(structure, parent);
    myPlugin = plugin;

    getTemplatePresentation().setIcon(MavenIcons.MavenPlugin);
    updatePlugin(pluginInfo);
  }

  public MavenPlugin getPlugin() {
    return myPlugin;
  }

  @Override
  public String getName() {
    return myPluginInfo == null ? myPlugin.getDisplayString() : myPluginInfo.getGoalPrefix();
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation, getName(), null, myPluginInfo != null ? myPlugin.getDisplayString() : null);
  }

  public void updatePlugin(@Nullable MavenPluginInfo newPluginInfo) {
    boolean hadPluginInfo = myPluginInfo != null;

    myPluginInfo = newPluginInfo;
    boolean hasPluginInfo = myPluginInfo != null;

    setErrorLevel(myPluginInfo == null ? MavenProjectsStructure.ErrorLevel.ERROR : MavenProjectsStructure.ErrorLevel.NONE);

    if (hadPluginInfo == hasPluginInfo) return;

    myGoalNodes.clear();
    if (myPluginInfo != null) {
      for (MavenPluginInfo.Mojo mojo : myPluginInfo.getMojos()) {
        myGoalNodes.add(new PluginGoalNode(myMavenProjectsStructure, this, mojo.getQualifiedGoal(), mojo.getGoal(),
                                           mojo.getDisplayName()));
      }
    }

    sort(myGoalNodes);
    myMavenProjectsStructure.updateFrom(this);
    childrenChanged();
  }

  @Override
  public boolean isVisible() {
    // show regardless absence of children
    return super.isVisible() || getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
  }
}
