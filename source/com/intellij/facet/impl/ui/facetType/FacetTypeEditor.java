package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.impl.ProjectFacetsConfigurator;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.ui.DefaultFacetSettingsEditor;
import com.intellij.facet.ui.FacetEditor;
import com.intellij.facet.ui.MultipleFacetSettingsEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FacetTypeEditor extends UnnamedConfigurableGroup {
  private final List<Configurable> myConfigurables = new ArrayList<Configurable>();
  private final Project myProject;
  private final StructureConfigurableContext myContext;
  private final FacetType<?, ?> myFacetType;
  private MultipleFacetSettingsEditor myAllFacetsEditor;

  public <C extends FacetConfiguration> FacetTypeEditor(@NotNull Project project, final StructureConfigurableContext context, @NotNull FacetType<?, C> facetType) {
    myProject = project;
    myContext = context;
    myFacetType = facetType;
    if (FacetAutodetectingManager.getInstance(project).hasDetectors(facetType)) {
      myConfigurables.add(new FacetAutodetectionConfigurable(project, context, facetType));
    }

    C configuration = ProjectFacetManager.getInstance(project).createDefaultConfiguration(facetType);
    DefaultFacetSettingsEditor defaultSettingsEditor = facetType.createDefaultConfigurationEditor(project, configuration);
    if (defaultSettingsEditor != null) {
      myConfigurables.add(new DefaultFacetSettingsConfigurable<C>(facetType, project, defaultSettingsEditor, configuration));
    }

    for (Configurable configurable : myConfigurables) {
      add(configurable);
    }
  }

  @Nullable
  private MultipleFacetSettingsEditor createAllFacetsEditor() {
    ProjectFacetsConfigurator facetsConfigurator = myContext.myModulesConfigurator.getFacetsConfigurator();
    List<Facet> facets = new ArrayList<Facet>();
    for (Module module : myContext.getModules()) {
      facets.addAll(facetsConfigurator.getFacetsByType(module, myFacetType.getId()));
    }
    if (!facets.isEmpty()) {
      final FacetEditor[] editors = new FacetEditor[facets.size()];
      for (int i = 0; i < facets.size(); i++) {
        editors[i] = facetsConfigurator.getOrCreateEditor(facets.get(i));
      }
      return myFacetType.createMultipleConfigurationsEditor(myProject, editors);
    }
    return null;
  }

  public JComponent createComponent() {
    MultipleFacetSettingsEditor allFacetsEditor = createAllFacetsEditor();
    if (myAllFacetsEditor != null) {
      myAllFacetsEditor.disposeUIResources();
    }
    myAllFacetsEditor = allFacetsEditor;

    List<Configurable> configurables = new ArrayList<Configurable>(myConfigurables);
    if (myAllFacetsEditor != null) {
      configurables.add(new AllFacetsConfigurable(myAllFacetsEditor));
    }

    if (configurables.isEmpty()) {
      return new JPanel();
    }
    if (configurables.size() == 1) {
      return configurables.get(0).createComponent();
    }

    TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
    for (Configurable configurable : configurables) {
      tabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent(), null);
    }
    return tabbedPane.getComponent();
  }

  private static class AllFacetsConfigurable implements Configurable {
    private MultipleFacetSettingsEditor myEditor;

    public AllFacetsConfigurable(final MultipleFacetSettingsEditor editor) {
      myEditor = editor;
    }

    public String getDisplayName() {
      return ProjectBundle.message("tab.name.all.facets");
    }

    public Icon getIcon() {
      return null;
    }

    public String getHelpTopic() {
      return null;
    }

    public JComponent createComponent() {
      return myEditor.createComponent();
    }

    public boolean isModified() {
      return false;
    }

    public void apply() throws ConfigurationException {
    }

    public void reset() {
    }

    public void disposeUIResources() {
      myEditor.disposeUIResources();
    }
  }
}
