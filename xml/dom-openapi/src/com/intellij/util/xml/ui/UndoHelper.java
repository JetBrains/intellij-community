// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.ui;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class UndoHelper {
  private boolean myShowing;
  private final Set<Document> myCurrentDocuments = new HashSet<>();
  private boolean myDirty;
  private final DocumentListener myDocumentAdapter = new DocumentListener() {
    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (myShowing) {
        myDirty = true;
      }
    }
  };

  public UndoHelper(@NotNull Project project, @NotNull Committable committable) {
    project.getMessageBus().connect(committable).subscribe(CommandListener.TOPIC, new CommandListener() {
      PsiDocumentManager psiDocumentManager;

      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        undoTransparentActionStarted();
      }

      @Override
      public void undoTransparentActionStarted() {
        myDirty = false;
      }

      @Override
      public void undoTransparentActionFinished() {
        if (myDirty) {
          if (psiDocumentManager == null) {
            psiDocumentManager = PsiDocumentManager.getInstance(project);
          }
          psiDocumentManager.commitAllDocuments();
          committable.reset();
        }
      }

      @Override
      public void commandFinished(@NotNull CommandEvent event) {
        undoTransparentActionFinished();
      }
    });
  }

  final void startListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.addDocumentListener(myDocumentAdapter);
    }
  }

  final void stopListeningDocuments() {
    for (final Document document : myCurrentDocuments) {
      document.removeDocumentListener(myDocumentAdapter);
    }
  }

  public final void setShowing(final boolean showing) {
    myShowing = showing;
  }

  public boolean isShowing() {
    return myShowing;
  }

  public final void addWatchedDocument(final Document document) {
    stopListeningDocuments();
    myCurrentDocuments.add(document);
    startListeningDocuments();
  }

  final void removeWatchedDocument(@NotNull Document document) {
    stopListeningDocuments();
    myCurrentDocuments.remove(document);
    startListeningDocuments();
  }

  final @NotNull Document @NotNull [] getDocuments() {
    return myCurrentDocuments.toArray(Document.EMPTY_ARRAY);
  }


}
