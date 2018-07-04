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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlNamespaceHelper;
import org.jetbrains.annotations.NotNull;

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
    final int offset = myElement.getTextOffset();
    final RangeMarker marker = myEditor.getDocument().createRangeMarker(offset, offset);
    Consumer<String> consumer = (namespace) -> {
      if (namespace != null) {
          final Project project = myFile.getProject();
           WriteCommandAction.writeCommandAction(project, myFile) . run(() -> {
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
          );
      }
    };
    if (myNamespaces.size() == 1) {
      consumer.consume(myNamespaces.get(0));
    } else {
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(myNamespaces)
        .setRenderer(XmlNSRenderer.INSTANCE)
        .setTitle(myTitle)
        .setItemChosenCallback(consumer)
        .createPopup()
        .showInBestPositionFor(myEditor);
    }

    return true;
  }
}
