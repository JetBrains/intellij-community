/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.javaee;

import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class HtmlLanguageLevelForm {
  private JRadioButton myHtml4RadioButton;
  private JRadioButton myHtml5RadioButton;
  private JRadioButton myOtherRadioButton;
  private JPanel myContentPanel;
  private JPanel myOtherDoctypeWrapper;
  private TextFieldWithAutoCompletion myDoctypeTextField;
  private List<MyListener> myListeners = new ArrayList<MyListener>();

  public HtmlLanguageLevelForm(Project project) {
    myDoctypeTextField = new TextFieldWithAutoCompletion(project);
    myOtherDoctypeWrapper.add(myDoctypeTextField);
    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDoctypeTextField.setEnabled(myOtherRadioButton.isSelected());
        fireDoctypeChanged();
      }
    };
    myHtml4RadioButton.addActionListener(listener);
    myHtml5RadioButton.addActionListener(listener);
    myOtherRadioButton.addActionListener(listener);
    myDoctypeTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        fireDoctypeChanged();
      }
    });
    myDoctypeTextField.setVariants(ExternalResourceManager.getInstance().getResourceUrls(null, true));
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }

  @NotNull
  public String getDoctype() {
    if (myHtml4RadioButton.isSelected()) {
      return XmlUtil.XHTML_URI;
    }
    else if (myHtml5RadioButton.isSelected()) {
      return XmlUtil.HTML5_SCHEMA_LOCATION;
    }
    return myDoctypeTextField.getText();
  }

  public void resetFromDoctype(String doctype) {
    if (doctype == null || doctype.length() == 0 || doctype.equals(XmlUtil.XHTML_URI)) {
      myHtml4RadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(false);
    }
    else if (doctype.equals(XmlUtil.HTML5_SCHEMA_LOCATION)) {
      myHtml5RadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(false);
    }
    else {
      myOtherRadioButton.setSelected(true);
      myDoctypeTextField.setEnabled(true);
      myDoctypeTextField.setText(doctype);
    }
  }

  public void addListener(@NotNull MyListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull MyListener listener) {
    myListeners.remove(listener);
  }

  private void fireDoctypeChanged() {
    for (MyListener listener : myListeners) {
      listener.doctypeChanged();
    }
  }

  public interface MyListener {
    void doctypeChanged();
  }
}
