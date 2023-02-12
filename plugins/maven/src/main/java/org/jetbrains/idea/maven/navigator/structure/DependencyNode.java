// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

class DependencyNode extends BaseDependenciesNode implements ArtifactNode {
  private final MavenArtifact myArtifact;
  private final MavenArtifactNode myArtifactNode;
  private final boolean myUnresolved;

  DependencyNode(MavenProjectsStructure structure,
                        MavenSimpleNode parent,
                        MavenArtifactNode artifactNode,
                        MavenProject mavenProject,
                        boolean unresolved) {
    super(structure, parent, mavenProject);
    myArtifactNode = artifactNode;
    myArtifact = artifactNode.getArtifact();
    myUnresolved = unresolved;
    getTemplatePresentation().setIcon(AllIcons.Nodes.PpLib);
  }

  @Override
  public MavenArtifact getArtifact() {
    return myArtifact;
  }

  public boolean isUnresolved() {
    return myUnresolved;
  }

  @Override
  public String getName() {
    return myArtifact.getDisplayStringForLibraryName();
  }

  private String getToolTip() {
    final StringBuilder myToolTip = new StringBuilder();
    String scope = myArtifactNode.getOriginalScope();

    if (StringUtil.isNotEmpty(scope) && !MavenConstants.SCOPE_COMPILE.equals(scope)) {
      myToolTip.append(scope).append(" ");
    }
    if (myArtifactNode.getState() == MavenArtifactState.CONFLICT) {
      myToolTip.append("omitted for conflict");
      if (myArtifactNode.getRelatedArtifact() != null) {
        myToolTip.append(" with ").append(myArtifactNode.getRelatedArtifact().getVersion());
      }
    }
    if (myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
      myToolTip.append("omitted for duplicate");
    }
    return myToolTip.toString().trim();
  }

  @Override
  protected void doUpdate(@NotNull PresentationData presentation) {
    setNameAndTooltip(presentation, getName(), null, getToolTip());
  }

  @Override
  protected void setNameAndTooltip(@NotNull PresentationData presentation,
                                   String name,
                                   @Nullable String tooltip,
                                   SimpleTextAttributes attributes) {
    final SimpleTextAttributes mergedAttributes;
    if (myArtifactNode.getState() == MavenArtifactState.CONFLICT || myArtifactNode.getState() == MavenArtifactState.DUPLICATE) {
      mergedAttributes = SimpleTextAttributes.merge(attributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else {
      mergedAttributes = attributes;
    }
    super.setNameAndTooltip(presentation, name, tooltip, mergedAttributes);
  }

  void updateDependency() {
    setErrorLevel(myUnresolved ? MavenProjectsStructure.ErrorLevel.ERROR : MavenProjectsStructure.ErrorLevel.NONE);
  }

  @Override
  public Navigatable getNavigatable() {
    final MavenArtifactNode parent = myArtifactNode.getParent();
    final VirtualFile file;
    if (parent == null) {
      file = getMavenProject().getFile();
    }
    else {
      final MavenId id = parent.getArtifact().getMavenId();
      final MavenProject pr = MavenProjectsManager.getInstance(myProject).findProject(id);
      file = pr == null ? MavenNavigationUtil.getArtifactFile(getProject(), id) : pr.getFile();
    }
    return file == null ? null : MavenNavigationUtil.createNavigatableForDependency(getProject(), file, getArtifact());
  }

  @Override
  public boolean isVisible() {
    // show regardless absence of children
    return getDisplayKind() != MavenProjectsStructure.DisplayKind.NEVER;
  }
}
