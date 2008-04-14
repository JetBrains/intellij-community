/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.ui.FacetEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.NamedConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class FacetConfigurable extends NamedConfigurable<Facet> {
  private final Facet myFacet;
  private final ProjectFacetsConfigurator myFacetsConfigurator;
  private String myFacetName;

  public FacetConfigurable(final Facet facet, final ProjectFacetsConfigurator facetsConfigurator, final Runnable updateTree) {
    super(!facet.getType().isOnlyOneFacetAllowed(), updateTree);
    myFacet = facet;
    myFacetsConfigurator = facetsConfigurator;
    myFacetName = myFacet.getName();
  }


  public void setDisplayName(String name) {
    name = name.trim();
    if (!name.equals(myFacetName)) {
      myFacetsConfigurator.getOrCreateModifiableModel(myFacet.getModule()).rename(myFacet, name);
      myFacetName = name;
    }
  }

  public Facet getEditableObject() {
    return myFacet;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("facet.banner.text", myFacetName);
  }

  public JComponent createOptionsPanel() {
    return getEditor().getComponent();
  }

  public FacetEditor getEditor() {
    return myFacetsConfigurator.getOrCreateEditor(myFacet);
  }

  @Nls
  public String getDisplayName() {
    return myFacetName;
  }

  @Nullable
  public Icon getIcon() {
    return myFacet.getType().getIcon();
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
  }

  public void disposeUIResources() {
  }
}
