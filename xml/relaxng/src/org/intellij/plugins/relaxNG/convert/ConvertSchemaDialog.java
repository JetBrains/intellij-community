/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.convert;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 16.11.2007
*/
public class ConvertSchemaDialog extends DialogWrapper implements PropertyChangeListener {
  private final ConvertSchemaSettingsImpl mySettings;
  private final AbstractAction myAdvancedAction;

  protected ConvertSchemaDialog(Project project, SchemaType input, VirtualFile firstFile) {
    super(project, false);
    setTitle("Convert Schema File");

    mySettings = new ConvertSchemaSettingsImpl(project, input, firstFile);
    mySettings.addPropertyChangeListener(ConvertSchemaSettingsImpl.OUTPUT_TYPE, this);
    mySettings.addPropertyChangeListener(ConvertSchemaSettingsImpl.OUTPUT_PATH, this);

    myAdvancedAction = new AbstractAction("Advanced...") {
      public void actionPerformed(ActionEvent e) {
        mySettings.showAdvancedSettings();
      }
    };
    myAdvancedAction.setEnabled(mySettings.hasAdvancedSettings());

    init();

    getOKAction().setEnabled(mySettings.getOutputDestination().trim().length() > 0);
  }

  @Override
  protected Action[] createLeftSideActions() {
    return new Action[]{
            myAdvancedAction
    };
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySettings.getPreferredFocusedComponent();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return mySettings.getRoot();
  }

  public ConvertSchemaSettings getSettings() {
    return mySettings;
  }

  public void propertyChange(PropertyChangeEvent evt) {
    if (ConvertSchemaSettingsImpl.OUTPUT_TYPE.equals(evt.getPropertyName())) {
      myAdvancedAction.setEnabled(mySettings.hasAdvancedSettings());
    } else if (ConvertSchemaSettingsImpl.OUTPUT_PATH.equals(evt.getPropertyName())) {
      getOKAction().setEnabled(((String)evt.getNewValue()).trim().length() > 0);
    }
  }
}