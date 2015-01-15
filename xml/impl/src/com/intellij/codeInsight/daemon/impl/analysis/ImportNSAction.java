/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNamespaceHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
public class ImportNSAction implements QuestionAction {
  private final List<String> myNamespaces;
  private final XmlFile myFile;
  private final PsiElement myElement;
  private final Editor myEditor;
  private final String myTitle;

  public ImportNSAction(final List<String> namespaces, XmlFile file, @NotNull PsiElement element, Editor editor, final String title) {
    myNamespaces = namespaces;
    myFile = file;
    myElement = element;
    myEditor = editor;
    myTitle = title;
  }

  @Override
  public boolean execute() {
    final String[] strings = ArrayUtil.toStringArray(myNamespaces);
    final JList list = new JBList(strings);
    list.setCellRenderer(XmlNSRenderer.INSTANCE);
    list.setSelectedIndex(0);
    final int offset = myElement.getTextOffset();
    final RangeMarker marker = myEditor.getDocument().createRangeMarker(offset, offset);
    final Runnable runnable = new Runnable() {

      @Override
      public void run() {
        final String namespace = (String)list.getSelectedValue();
        if (namespace != null) {
            final Project project = myFile.getProject();
            new WriteCommandAction.Simple(project, myFile) {

              @Override
              protected void run() throws Throwable {
                final XmlNamespaceHelper extension = XmlNamespaceHelper.getHelper(myFile);
                final String prefix = extension.getNamespacePrefix(myElement);
                extension.insertNamespaceDeclaration(myFile,
                                                     myEditor,
                                                     Collections.singleton(namespace),
                                                     prefix,
                                                     new XmlNamespaceHelper.Runner<String, IncorrectOperationException>() {
                    @Override
                    public void run(final String s) throws IncorrectOperationException {
                      PsiDocumentManager.getInstance(myFile.getProject()).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
                      PsiElement element = myFile.findElementAt(marker.getStartOffset());
                      if (element != null) {
                        extension.qualifyWithPrefix(s, element, myEditor.getDocument());
                      }
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
