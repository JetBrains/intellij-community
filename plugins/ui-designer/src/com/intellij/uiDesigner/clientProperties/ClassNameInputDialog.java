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

package com.intellij.uiDesigner.clientProperties;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.uiDesigner.UIDesignerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class ClassNameInputDialog extends DialogWrapper {
  private EditorTextField myEditorTextField1;
  private JPanel myRootPanel;
  private final Project myProject;

  public ClassNameInputDialog(Project project, Component parent) {
    super(parent, false);
    myProject = project;
    init();
    setTitle(UIDesignerBundle.message("client.properties.title"));
  }

  private void createUIComponents() {
    myEditorTextField1 = new EditorTextField("", myProject, StdFileTypes.JAVA);
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(myProject);
    PsiPackage defaultPackage = JavaPsiFacade.getInstance(myProject).findPackage("");
    final PsiCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    myEditorTextField1.setDocument(PsiDocumentManager.getInstance(myProject).getDocument(fragment));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myEditorTextField1;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  public String getClassName() {
    return myEditorTextField1.getDocument().getText();
  }
}
