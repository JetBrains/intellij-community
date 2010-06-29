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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dmitry Avdeev
*/
public class ImportNSAction implements QuestionAction {
  private final Set<String> myNamespaces;
  private final XmlFile myFile;
  @Nullable private final PsiElement myElement;
  private final Editor myEditor;
  private final String myTitle;

  public ImportNSAction(final Set<String> namespaces, XmlFile file, @NotNull PsiElement element, Editor editor, final String title) {

    myNamespaces = namespaces;
    myFile = file;
    myElement = element;
    myEditor = editor;
    myTitle = title;
  }

  public boolean execute() {
    final Object[] objects = myNamespaces.toArray();
    Arrays.sort(objects);
    final JList list = new JBList(objects);
    list.setCellRenderer(XmlNSRenderer.INSTANCE);
    list.setSelectedIndex(0);
    final Runnable runnable = new Runnable() {

      public void run() {
        final String namespace = (String)list.getSelectedValue();
        if (namespace != null) {
            final Project project = myFile.getProject();
            new WriteCommandAction.Simple(project, myFile) {

              protected void run() throws Throwable {
                final XmlExtension extension = XmlExtension.getExtension(myFile);
                final String prefix = extension.getNamespacePrefix(myElement);
                extension.insertNamespaceDeclaration(myFile,
                                                     myEditor,
                                                     Collections.singleton(namespace),
                                                     prefix,
                                                     new XmlExtension.Runner<String, IncorrectOperationException>() {
                    public void run(final String s) throws IncorrectOperationException {
                      PsiDocumentManager.getInstance(myFile.getProject()).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
                      extension.qualifyWithPrefix(s, myElement, myEditor.getDocument());
                    }
                  }
                );
              }
            }.execute();
        }
      }
    };
    if (list.getModel().getSize() == 1) {
      runnable.run();
    } else {
      new PopupChooserBuilder(list).
        setTitle(myTitle).
        setItemChoosenCallback(runnable).
        createPopup().
        showInBestPositionFor(myEditor);
    }

    return true;
  }
}
