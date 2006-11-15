/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 17:53:24
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilePatch {
  private String myBeforeName;
  private String myAfterName;
  private List<PatchHunk> myHunks = new ArrayList<PatchHunk>();

  public void setBeforeName(final String fileName) {
    myBeforeName = fileName;  
  }

  public void setAfterName(final String fileName) {
    myAfterName = fileName;
  }

  public void addHunk(final PatchHunk hunk) {
    myHunks.add(hunk);
  }

  public void apply(final VirtualFile patchedDir, final int skipTopDirs) throws ApplyPatchException {
    String[] pathNameComponents = myBeforeName.split("/");
    VirtualFile fileToPatch = patchedDir;
    for(int i=skipTopDirs; i<pathNameComponents.length; i++) {
      fileToPatch = fileToPatch.findChild(pathNameComponents [i]);
      if (fileToPatch == null) {
        break;
      }
    }
    if (fileToPatch == null) {
      throw new ApplyPatchException("Cannot find file to patch: " + myBeforeName);
    }

    CharSequence text = LoadTextUtil.loadText(fileToPatch);
    List<String> lines = new ArrayList<String>();
    Collections.addAll(lines, LineTokenizer.tokenize(text, false));
    for(PatchHunk hunk: myHunks) {
      hunk.apply(lines);
    }
    final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
    document.setText(StringUtil.join(lines, "\n") + "\n");
    FileDocumentManager.getInstance().saveDocument(document);
  }
}