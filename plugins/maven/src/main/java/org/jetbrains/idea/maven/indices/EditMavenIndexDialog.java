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
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFileManager;

import javax.swing.*;

public class EditMavenIndexDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myUrlField;

  public EditMavenIndexDialog() {
    this("");
  }

  public EditMavenIndexDialog(String url) {
    super(false);
    setTitle("Edit Maven Repository");
    myUrlField.setText(url.length() == 0 ? "http://" : url);
    init();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getUrl() {
    String result = myUrlField.getText();
    if (VirtualFileManager.extractProtocol(result) == null) {
      result = "http://" + result;
    }
    return result;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myUrlField;
  }
}
