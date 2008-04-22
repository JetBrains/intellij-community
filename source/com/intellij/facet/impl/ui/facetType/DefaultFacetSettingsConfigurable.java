package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class DefaultFacetSettingsConfigurable<C extends FacetConfiguration> implements Configurable {
  private final FacetType<?, C> myFacetType;
  private final Project myProject;
  private final DefaultFacetSettingsEditor myDelegate;
  private final C myConfiguration;

  public DefaultFacetSettingsConfigurable(final @NotNull FacetType<?, C> facetType, final @NotNull Project project, final @NotNull DefaultFacetSettingsEditor delegate,
                                    @NotNull C configuration) {
    myFacetType = facetType;
    myProject = project;
    myDelegate = delegate;
    myConfiguration = configuration;
  }

  public String getDisplayName() {
    return ProjectBundle.message("facet.defaults.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myDelegate.createComponent();
  }

  public boolean isModified() {
    return myDelegate.isModified();
  }

  public void apply() throws ConfigurationException {
    if (myDelegate.isModified()) {
      myDelegate.apply();
      ProjectFacetManager.getInstance(myProject).setDefaultConfiguration(myFacetType, myConfiguration);
    }
  }

  public void reset() {
    myDelegate.reset();
  }

  public void disposeUIResources() {
    myDelegate.disposeUIResources();
  }
}
