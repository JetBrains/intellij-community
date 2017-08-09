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
package org.jetbrains.settingsRepository;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * We do not use DialogWrapper validation because UI is awful (located below the field with the same font).
 * To fix https://youtrack.jetbrains.com/issue/IDEA-173109 "Typing settings repository URL triggers path alert",
 * we simply do validation on button action (and never disable button).
 */
public class IcsSettingsPanel extends DialogWrapper {
  private JPanel panel;
  private TextFieldWithBrowseButton urlTextField;
  private final Action[] syncActions;

  public IcsSettingsPanel(@Nullable Project project) {
    super(project, true);

    urlTextField.setText(IcsManagerKt.getIcsManager().getRepositoryManager().getUpstream());
    urlTextField.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));

    syncActions = UpstreamEditorKt.createMergeActions(project, urlTextField, getRootPane(), new Function0<Unit>() {
      @Override
      public Unit invoke() {
        doOKAction();
        return null;
      }
    });

    setTitle(IcsBundleKt.icsMessage("settings.panel.title"));
    setResizable(false);
    init();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return urlTextField;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return panel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return syncActions;
  }
}