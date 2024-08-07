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

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTabbedPane;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class AdvancedOptionsDialog extends DialogWrapper {
  private AdvancedOptions myInputOptions;
  private AdvancedOptions myOutputOptions;

  private Map<String,?> myInputOptions_;
  private Map<String,?> myOutputOptions_;

  protected AdvancedOptionsDialog(Project project, SchemaType inputType, SchemaType outputType) {
    super(project, false);
    setTitle(RelaxngBundle.message("relaxng.convert-schema.advanced-options.dialog.title"));

    if (inputType == SchemaType.DTD) {
      myInputOptions = new AdvancedDtdOptions();
    }
    if (outputType == SchemaType.XSD) {
      myOutputOptions = new AdvancedXsdOptions();
    }

    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JComponent root;
    if (myInputOptions != null && myOutputOptions != null) {
      root = new JBTabbedPane();
      ((JTabbedPane)root).addTab(CommonBundle.message("title.input"), myInputOptions.getRoot());
      ((JTabbedPane)root).addTab(CommonBundle.message("title.output"), myOutputOptions.getRoot());
    } else if (myInputOptions != null) {
      root = myInputOptions.getRoot();
    } else {
      root = myOutputOptions.getRoot();
    }
    return root;
  }

  public Map<String, ?> getInputOptions() {
    if (myInputOptions != null) {
      return myInputOptions.getOptions();
    } else {
      return myInputOptions_;
    }
  }

  public Map<String, ?> getOutputOptions() {
    if (myOutputOptions != null) {
      return myOutputOptions.getOptions();
    } else {
      return myOutputOptions_;
    }
  }

  public void setOptions(Map<String, ?> inputOptions, Map<String, ?> outputOptions) {
    if (myInputOptions != null) {
      myInputOptions.setOptions(inputOptions);
    } else {
      myInputOptions_ = inputOptions;
    }
    if (myOutputOptions != null) {
      myOutputOptions.setOptions(outputOptions);
    } else {
      myOutputOptions_ = outputOptions;
    }
  }
}