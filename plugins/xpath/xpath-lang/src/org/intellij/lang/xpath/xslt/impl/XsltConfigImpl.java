/**
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
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@State(
  name = "XSLT-Support.Configuration",
  storages = {
    @Storage("other.xml")}
)
class XsltConfigImpl extends XsltConfig implements PersistentStateComponent<XsltConfigImpl>, ApplicationComponent {
  public boolean SHOW_LINKED_FILES = true;

  @Nullable
  @Override
  public XsltConfigImpl getState() {
    return this;
  }

  @Override
  public void loadState(XsltConfigImpl state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  @SuppressWarnings({"StringEquality"})
  public void initComponent() {
    final Language xmlLang = StdFileTypes.XML.getLanguage();

    //            intentionManager.addAction(new DeleteUnusedParameterFix());
    //            intentionManager.addAction(new DeleteUnusedVariableFix());

    final XsltFormattingModelBuilder builder = new XsltFormattingModelBuilder(LanguageFormatting.INSTANCE.forLanguage(xmlLang));
    LanguageFormatting.INSTANCE.addExplicitExtension(xmlLang, builder);

    try {
      // TODO: put this into com.intellij.refactoring.actions.IntroduceParameterAction, just like IntroduceVariableAction
      final AnAction introduceParameter = ActionManager.getInstance().getAction("IntroduceParameter");
      if (introduceParameter != null) {
        introduceParameter.setInjectedContext(true);
      }
    }
    catch (Exception e) {
      Logger.getInstance(XsltConfigImpl.class.getName()).error(e);
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "XSLT-Support.Configuration";
  }

  @Override
  public boolean isShowLinkedFiles() {
    return SHOW_LINKED_FILES;
  }

  public static class UIImpl extends JPanel implements SearchableConfigurable {
    private final JCheckBox myShowLinkedFiles;

    private final XsltConfigImpl myConfig;

    public UIImpl(XsltConfigImpl config) {
      myConfig = config;
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

      myShowLinkedFiles = new JCheckBox("Show Associated Files in Project View");
      myShowLinkedFiles.setMnemonic('A');
      myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);

      add(myShowLinkedFiles);

      final JPanel jPanel = new JPanel(new BorderLayout());
      jPanel.add(Box.createVerticalGlue(), BorderLayout.CENTER);

      final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      jPanel.add(panel, BorderLayout.SOUTH);
      jPanel.setAlignmentX(0);
      add(jPanel);
    }

    @Override
    @Nls
    public String getDisplayName() {
      return "XSLT";
    }

    @Override
    @NotNull
    @NonNls
    public String getHelpTopic() {
      return "settings.xslt";
    }

    @Override
    public void disposeUIResources() {
    }

    @Override
    public JComponent createComponent() {
      return this;
    }

    @Override
    public boolean isModified() {
      return myConfig.SHOW_LINKED_FILES != myShowLinkedFiles.isSelected();
    }

    @Override
    public void apply() {
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
      myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);
    }

    @Override
    @NotNull
    public String getId() {
      return getHelpTopic();
    }

    @Override
    public Runnable enableSearch(String option) {
      return null;
    }
  }
}
