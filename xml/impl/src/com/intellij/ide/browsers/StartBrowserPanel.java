/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.browsers;

import com.intellij.ide.DataManager;
import com.intellij.ide.browsers.impl.WebBrowserServiceImpl;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.util.Consumer;
import com.intellij.util.Url;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;

public class StartBrowserPanel {
  private JCheckBox myStartBrowserCheckBox;
  private JComponent myBrowserComboBox;

  private JCheckBox myStartJavaScriptDebuggerCheckBox;

  private TextFieldWithBrowseButton myUrlField;
  private BrowserSelector myBrowserSelector;

  private JPanel myRoot;

  public StartBrowserPanel() {
    myStartJavaScriptDebuggerCheckBox.setVisible(JavaScriptDebuggerStarter.Util.hasStarters());
    myRoot.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        myRoot.removeAncestorListener(this);

        Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myRoot));
        if (project == null) {
          DataManager.getInstance().getDataContextFromFocus().doWhenDone(new Consumer<DataContext>() {
            @Override
            public void consume(DataContext context) {
              Project project = CommonDataKeys.PROJECT.getData(context);
              if (project == null) {
                // IDEA-118202
                project = ProjectManager.getInstance().getDefaultProject();
              }
              setupUrlField(myUrlField, project);
            }
          });
        }
        else {
          setupUrlField(myUrlField, project);
        }
      }
    });
  }

  @NotNull
  public JPanel getComponent() {
    return myRoot;
  }

  @Nullable
  public String getUrl() {
    String url = StringUtil.nullize(myUrlField.getText(), true);
    if (url != null) {
      url = url.trim();
      if (!URLUtil.containsScheme(url)) {
        return VirtualFileManager.constructUrl(URLUtil.HTTP_PROTOCOL, url);
      }
    }
    return url;
  }

  public void setUrl(@Nullable String url) {
    myUrlField.setText(url);
  }

  public boolean isSelected() {
    return myStartBrowserCheckBox.isSelected();
  }

  public void setSelected(boolean value) {
    myStartBrowserCheckBox.setSelected(value);
  }

  public JCheckBox getStartJavaScriptDebuggerCheckBox() {
    return myStartJavaScriptDebuggerCheckBox;
  }

  public BrowserSelector getBrowserSelector() {
    return myBrowserSelector;
  }

  private void createUIComponents() {
    myBrowserSelector = new BrowserSelector();
    myBrowserComboBox = myBrowserSelector.getMainComponent();
    if (UIUtil.isUnderAquaLookAndFeel()) {
      myBrowserComboBox.setBorder(new EmptyBorder(3, 0, 0, 0));
    }
  }

  @Nullable
  private static Url virtualFileToUrl(@NotNull VirtualFile file, @NotNull Project project) {
    PsiFile psiFile;
    AccessToken token = ReadAction.start();
    try {
      psiFile = PsiManager.getInstance(project).findFile(file);
    }
    finally {
      token.finish();
    }
    return WebBrowserServiceImpl.getDebuggableUrl(psiFile);
  }

  @NotNull
  public StartBrowserSettings createSettings() {
    StartBrowserSettings browserSettings = new StartBrowserSettings();
    browserSettings.setSelected(isSelected());
    browserSettings.setBrowser(myBrowserSelector.getSelected());
    browserSettings.setStartJavaScriptDebugger(myStartJavaScriptDebuggerCheckBox.isSelected());
    browserSettings.setUrl(getUrl());
    return browserSettings;
  }

  public void setFromSettings(StartBrowserSettings settings) {
    setSelected(settings.isSelected());
    setUrl(settings.getUrl());
    myStartJavaScriptDebuggerCheckBox.setSelected(settings.isStartJavaScriptDebugger());
    myBrowserSelector.setSelected(settings.getBrowser());
  }

  public static void setupUrlField(@NotNull TextFieldWithBrowseButton field, @NotNull final Project project) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return HtmlUtil.isHtmlFile(file) || virtualFileToUrl(file, project) != null;
      }
    };
    descriptor.setTitle(XmlBundle.message("javascript.debugger.settings.choose.file.title"));
    descriptor.setDescription(XmlBundle.message("javascript.debugger.settings.choose.file.subtitle"));
    descriptor.setRoots(ProjectRootManager.getInstance(project).getContentRoots());

    field.addBrowseFolderListener(new TextBrowseFolderListener(descriptor, project) {
      @NotNull
      @Override
      protected String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        if (chosenFile.isDirectory()) {
          return chosenFile.getPath();
        }

        Url url = virtualFileToUrl(chosenFile, project);
        return url == null ? chosenFile.getUrl() : url.toDecodedForm();
      }
    });
  }
}