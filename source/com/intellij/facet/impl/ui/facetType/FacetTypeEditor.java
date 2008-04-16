package com.intellij.facet.impl.ui.facetType;

import com.intellij.facet.FacetType;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class FacetTypeEditor extends UnnamedConfigurableGroup {
  private List<Configurable> myConfigurables = new ArrayList<Configurable>();

  public FacetTypeEditor(@NotNull Project project, final StructureConfigurableContext context, @NotNull FacetType<?, ?> facetType) {
    if (FacetAutodetectingManager.getInstance(project).hasDetectors(facetType)) {
      myConfigurables.add(new FacetAutodetectionConfigurable(project, context, facetType));
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
