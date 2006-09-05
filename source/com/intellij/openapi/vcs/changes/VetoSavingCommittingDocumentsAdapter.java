/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 05.09.2006
 * Time: 20:07:21
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.VetoDocumentSavingException;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class VetoSavingCommittingDocumentsAdapter extends FileDocumentManagerAdapter implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.VetoSavingCommittingDocumentsAdapter");

  private FileDocumentManager myFileDocumentManager;

  public VetoSavingCommittingDocumentsAdapter(final FileDocumentManager fileDocumentManager) {
    myFileDocumentManager = fileDocumentManager;
  }

  public void beforeDocumentSaving(Document document) throws VetoDocumentSavingException {
    if (document.getUserData(ChangeListManagerImpl.DOCUMENT_BEING_COMMITTED_KEY) != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Vetoing save for " + FileDocumentManager.getInstance().getFile(document).getPath());
      }
      throw new VetoDocumentSavingException();
    }
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "VetoSavingComittingDocumentsAdapter";
  }

  public void initComponent() {
    myFileDocumentManager.addFileDocumentManagerListener(this);
  }

  public void disposeComponent() {
    myFileDocumentManager.removeFileDocumentManagerListener(this);
  }
}