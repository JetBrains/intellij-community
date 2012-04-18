/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;

/**
 * @author yole
 */
public class RelocateDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myFromURLTextField;
  private JTextField myToURLTextField;

  public RelocateDialog(Project project, final SVNURL url) {
    super(project, false);
    init();
    setTitle("Relocate Working Copy");
    myFromURLTextField.setText(url.toString());
    myToURLTextField.setText(url.toString());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getBeforeURL() {
    return myFromURLTextField.getText();
  }

  public String getAfterURL() {
    return myToURLTextField.getText();
  }
}
