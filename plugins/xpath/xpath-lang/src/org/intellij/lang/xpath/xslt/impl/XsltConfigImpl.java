/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

@State(name = "XSLT-Support.Configuration", storages = {@Storage(StoragePathMacros.NON_ROAMABLE_FILE)})
class XsltConfigImpl extends XsltConfig implements PersistentStateComponent<XsltConfigImpl> {
  public boolean SHOW_LINKED_FILES = true;

  @Override
  public @Nullable XsltConfigImpl getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull XsltConfigImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public boolean isShowLinkedFiles() {
    return SHOW_LINKED_FILES;
  }

  public static class UIImpl implements SearchableConfigurable {
    private final XsltConfigImpl myConfig;

    private JPanel myPanel;
    private JCheckBox myShowLinkedFiles;

    public UIImpl() {
      myConfig = (XsltConfigImpl)XsltConfig.getInstance();
    }

    @Override
  public String getDisplayName() {
    return XPathBundle.message("configurable.xslt.display.name");
  }

    @Override
    public @NotNull @NonNls String getHelpTopic() {
      return "settings.xslt";
    }

    @Override
    public JComponent createComponent() {
      myPanel = new JPanel();
      myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

      myShowLinkedFiles = new JBCheckBox(XPathBundle.message("checkbox.show.associated.files.in.project.view"));
      myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);

      myPanel.add(myShowLinkedFiles);

      final JPanel jPanel = new JPanel(new BorderLayout());
      jPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER);

      final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      jPanel.add(panel, BorderLayout.SOUTH);
      jPanel.setAlignmentX(0);
      myPanel.add(jPanel);
      return myPanel;
    }

    @Override
    public boolean isModified() {
      return myShowLinkedFiles != null && myConfig.SHOW_LINKED_FILES != myShowLinkedFiles.isSelected();
    }

    @Override
    public void apply() {
      if (myShowLinkedFiles == null) return;

      boolean oldValue = myConfig.SHOW_LINKED_FILES;

      myConfig.SHOW_LINKED_FILES = myShowLinkedFiles.isSelected();

      // TODO: make this a ConfigListener
      if (oldValue != myConfig.SHOW_LINKED_FILES) {
        final Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
          ProjectView.getInstance(project).refresh();
        }
      }
    }

    @Override
    public void reset() {
      if (myShowLinkedFiles == null) return;

      myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);
    }

    @Override
    public void disposeUIResources() {
      myPanel = null;
      myShowLinkedFiles = null;
    }

    @Override
    public @NotNull String getId() {
      return getHelpTopic();
    }
  }
}
