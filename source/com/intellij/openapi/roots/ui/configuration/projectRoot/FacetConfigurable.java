/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.facet.Facet;
import com.intellij.facet.impl.ProjectFacetsConfigurator;

import javax.swing.*;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class FacetConfigurable extends NamedConfigurable<Facet> {
  private Facet myFacet;
  private ProjectFacetsConfigurator myFacetsConfigurator;

  public FacetConfigurable(final Facet facet, final ProjectFacetsConfigurator facetsConfigurator) {
    super(false, null);
    myFacet = facet;
    myFacetsConfigurator = facetsConfigurator;
  }


  public void setDisplayName(String name) {
  }

  public Facet getEditableObject() {
    return myFacet;
  }

  public String getBannerSlogan() {
    return ProjectBundle.message("facet.banner.text", myFacet.getPresentableName());
  }

  public JComponent createOptionsPanel() {
    return myFacetsConfigurator.getOrCreateEditor(myFacet).createComponent();
  }

  @Nls
  public String getDisplayName() {
    return myFacet.getPresentableName();
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
