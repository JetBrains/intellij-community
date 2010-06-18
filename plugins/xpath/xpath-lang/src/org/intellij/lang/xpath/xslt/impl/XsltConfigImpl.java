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
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class XsltConfigImpl extends XsltConfig implements JDOMExternalizable, ApplicationComponent {

    public boolean SHOW_LINKED_FILES = true;

    public void readExternal(Element element) throws InvalidDataException {
        DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        DefaultJDOMExternalizer.writeExternal(this, element);
    }

    @SuppressWarnings({ "StringEquality" })
    public void initComponent() {
      final Language xmlLang = StdFileTypes.XML.getLanguage();

//            intentionManager.addAction(new DeleteUnusedParameterFix());
//            intentionManager.addAction(new DeleteUnusedVariableFix());

      final XsltFormattingModelBuilder builder = new XsltFormattingModelBuilder(LanguageFormatting.INSTANCE.forLanguage(xmlLang));
      LanguageFormatting.INSTANCE.addExplicitExtension(xmlLang, builder);

      final ExternalResourceManagerEx erm = ExternalResourceManagerEx.getInstanceEx();
      erm.addIgnoredResource(XsltSupport.PLUGIN_EXTENSIONS_NS);
    }

    public void disposeComponent() {
    }

    @NotNull
    @NonNls
    public String getComponentName() {
        return "XSLT-Support.Configuration";
    }

  public boolean isShowLinkedFiles() {
        return SHOW_LINKED_FILES;
    }

  public static class UIImpl extends JPanel implements UI {
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

        @Nls
        public String getDisplayName() {
            return "XSLT";
        }

        @Nullable
        public Icon getIcon() {
            return null;
        }

        @Nullable
        @NonNls
        public String getHelpTopic() {
            return "settings.xslt";
        }

        public void disposeUIResources() {
        }

        public JComponent createComponent() {
            return this;
        }

        public boolean isModified() {
            return myConfig.SHOW_LINKED_FILES != myShowLinkedFiles.isSelected();
        }

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

        public void reset() {
            myShowLinkedFiles.setSelected(myConfig.SHOW_LINKED_FILES);
        }

        public String getId() {
          return getHelpTopic();
        }

        public Runnable enableSearch(String option) {
          return null;
        }
    }

}