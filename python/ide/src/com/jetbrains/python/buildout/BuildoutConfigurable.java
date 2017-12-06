/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.buildout;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

/**
 * A silly configurable to add buildout facet configurator to PyCharm
 * User: dcheryasov
 */
public class BuildoutConfigurable implements Configurable, NonDefaultProjectConfigurable {

  private JCheckBox myEnabledCheckbox;
  private JPanel myPlaceholder;
  private JPanel myMainPanel;
  private BuildoutConfigPanel mySettingsPanel;
  private final Module myModule;

  public BuildoutConfigurable(@NotNull Module module) {
    myModule = module;
    BuildoutFacetConfiguration config = null;
    BuildoutFacet facet = BuildoutFacet.getInstance(myModule);
    if (facet != null) config = facet.getConfiguration();
    if (config == null) config = new BuildoutFacetConfiguration(null);
    mySettingsPanel = new BuildoutConfigPanel(myModule, config);
    myPlaceholder.add(mySettingsPanel, BorderLayout.CENTER);
    final boolean isEnabled = !StringUtil.isEmptyOrSpaces(config.getScriptName());
    myEnabledCheckbox.setSelected(isEnabled);
    updateFacetEnabled(isEnabled);
    myEnabledCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final boolean enabled = myEnabledCheckbox.isSelected();
        updateFacetEnabled(enabled);
      }
    });
    mySettingsPanel.addFocusListener(
      new FocusListener() {
        @Override
        public void focusGained(FocusEvent focusEvent) {
          switchNoticeText();
        }

        @Override
        public void focusLost(FocusEvent focusEvent) { }
      }
    );
  }

  private void updateFacetEnabled(boolean enabled) {
    mySettingsPanel.setFacetEnabled(enabled);
    UIUtil.setEnabled(mySettingsPanel, enabled, true);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Buildout";
  }

  @Override
  public String getHelpTopic() {
    return "reference-python-buildout";
  }

  @Override
  public JComponent createComponent() {
    switchNoticeText();
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    if (! myEnabledCheckbox.isEnabled()) return false;
    final BuildoutFacet facet = myModule != null? BuildoutFacet.getInstance(myModule) : null;
    final boolean got_facet = facet != null;
    if (myEnabledCheckbox.isSelected() != got_facet) return true;
    if (got_facet && myEnabledCheckbox.isSelected()) {
      return mySettingsPanel.isModified(facet.getConfiguration());
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    final BuildoutFacet facet = myModule != null? BuildoutFacet.getInstance(myModule) : null;
    final boolean got_facet = facet != null;
    boolean facet_is_desired = myEnabledCheckbox.isSelected();

    mySettingsPanel.apply();
    List<String> paths_from_script;
    if (facet_is_desired) {
      String script_name = mySettingsPanel.getScriptName();
      VirtualFile script_file = BuildoutConfigPanel.getScriptFile(script_name);
      paths_from_script = BuildoutFacet.extractBuildoutPaths(script_file);
      if (paths_from_script == null) {
        throw new ConfigurationException("Failed to extract paths from '" + script_file.getPresentableName() + "'");
      }
      mySettingsPanel.getConfiguration().setPaths(paths_from_script);

    }
    if (facet_is_desired && ! got_facet) addFacet(mySettingsPanel.getConfiguration());
    if (! facet_is_desired && got_facet) removeFacet(facet);
    if (facet_is_desired) BuildoutFacet.attachLibrary(myModule);
    else BuildoutFacet.detachLibrary(myModule);
  }

  private void addFacet(BuildoutFacetConfiguration config) {
    BuildoutFacetConfigurator.setupFacet(myModule, config);
  }

  private void removeFacet(BuildoutFacet facet) {
    final ModifiableFacetModel model = FacetManager.getInstance(myModule).createModifiableModel();
    model.removeFacet(facet);
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }

  @Override
  public void reset() {
    // TODO: refactor, see AppEngineConfigurable
    if (myModule == null) {
      myEnabledCheckbox.setSelected(false);
      myEnabledCheckbox.setEnabled(false);
    }
    else {
      myEnabledCheckbox.setEnabled(true);
      switchNoticeText();
      final BuildoutFacet instance = BuildoutFacet.getInstance(myModule);
      if (instance != null) {
        boolean selected = ! StringUtil.isEmptyOrSpaces(instance.getConfiguration().getScriptName());
        myEnabledCheckbox.setSelected(selected);
        mySettingsPanel.setEnabled(selected);
        mySettingsPanel.reset();
      }
      else {
        myEnabledCheckbox.setSelected(false);
        mySettingsPanel.setEnabled(false);
      }
    }
  }

  private void switchNoticeText() {
    mySettingsPanel.showNoticeText(/*DjangoFacet.getInstance(myModule) != null*/ false);
  }
}
