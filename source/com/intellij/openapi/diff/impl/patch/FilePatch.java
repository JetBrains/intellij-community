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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

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

  public void apply(final VirtualFile patchedDir, final int skipTopDirs) throws ApplyPatchException, IOException {
    String[] pathNameComponents = myBeforeName.split("/");
    VirtualFile fileToPatch = patchedDir;
    int lastComponentToFind = isNewFile() ? pathNameComponents.length-1 : pathNameComponents.length;
    for(int i=skipTopDirs; i<lastComponentToFind; i++) {
      fileToPatch = fileToPatch.findChild(pathNameComponents [i]);
      if (fileToPatch == null) {
        break;
      }
    }

    if (fileToPatch == null) {
      throw new ApplyPatchException("Cannot find file to patch: " + myBeforeName);
    }

    if (isNewFile()) {
      VirtualFile newFile = fileToPatch.createChildData(this, pathNameComponents [pathNameComponents.length-1]);
      final Document document = FileDocumentManager.getInstance().getDocument(newFile);
      document.setText(myHunks.get(0).getText());
      FileDocumentManager.getInstance().saveDocument(document);
    }
    else if (isDeletedFile()) {
      fileToPatch.delete(this);
    }
    else {
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

  private boolean isNewFile() {
    return myHunks.size() == 1 && myHunks.get(0).isNewContent();
  }

  private boolean isDeletedFile() {
    return myHunks.size() == 1 && myHunks.get(0).isDeletedContent();
  }
}