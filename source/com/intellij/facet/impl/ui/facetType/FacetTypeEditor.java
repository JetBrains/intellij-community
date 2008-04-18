package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.facet.ui.CommonFacetSettingsEditor;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FacetTypeEditor extends UnnamedConfigurableGroup {
  private final List<Configurable> myConfigurables = new ArrayList<Configurable>();

  public <C extends FacetConfiguration> FacetTypeEditor(@NotNull Project project, final StructureConfigurableContext context, @NotNull FacetType<?, C> facetType) {
    if (FacetAutodetectingManager.getInstance(project).hasDetectors(facetType)) {
      myConfigurables.add(new FacetAutodetectionConfigurable(project, context, facetType));
    }

    C configuration = ProjectFacetManager.getInstance(project).createDefaultConfiguration(facetType);
    CommonFacetSettingsEditor defaultSettingsEditor = facetType.createDefaultConfigurationEditor(project, configuration);
    if (defaultSettingsEditor != null) {
      myConfigurables.add(new DefaultFacetSettingsEditor<C>(facetType, project, defaultSettingsEditor, configuration));
    }

    for (Configurable configurable : myConfigurables) {
      add(configurable);
    }
  }

  public JComponent createComponent() {
    if (myConfigurables.isEmpty()) {
      return new JPanel();
    }
    if (myConfigurables.size() == 1) {
      return myConfigurables.get(0).createComponent();
    }

    TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();
    for (Configurable configurable : myConfigurables) {
      tabbedPane.addTab(configurable.getDisplayName(), configurable.getIcon(), configurable.createComponent(), null);
    }
    return tabbedPane.getComponent();
  }
}
