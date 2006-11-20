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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FilePatch {
  private String myBeforeName;
  private String myAfterName;
  private List<PatchHunk> myHunks = new ArrayList<PatchHunk>();

  public String getBeforeName() {
    return myBeforeName;
  }

  public String getAfterName() {
    return myAfterName;
  }

  public String getBeforeFileName() {
    String[] pathNameComponents = myBeforeName.split("/");
    return pathNameComponents [pathNameComponents.length-1];
  }

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
    VirtualFile fileToPatch = findFileToPatch(patchedDir, skipTopDirs);

    if (fileToPatch == null) {
      throw new ApplyPatchException("Cannot find file to patch: " + myBeforeName);
    }

    if (isNewFile()) {
      VirtualFile newFile = fileToPatch.createChildData(this, getBeforeFileName());
      final Document document = FileDocumentManager.getInstance().getDocument(newFile);
      document.setText(myHunks.get(0).getText());
      FileDocumentManager.getInstance().saveDocument(document);
    }
    else if (isDeletedFile()) {
      fileToPatch.delete(this);
    }
    else {
      byte[] fileContents = fileToPatch.contentsToByteArray();
      CharSequence text = LoadTextUtil.getTextByBinaryPresentation(fileContents, fileToPatch);
      List<String> lines = new ArrayList<String>();
      Collections.addAll(lines, LineTokenizer.tokenize(text, false));
      for(PatchHunk hunk: myHunks) {
        hunk.apply(lines);
      }
      final Document document = FileDocumentManager.getInstance().getDocument(fileToPatch);
      StringBuilder docText = new StringBuilder();
      for(String line: lines) {
        docText.append(line).append("\n");
      }
      document.setText(docText.toString());
      FileDocumentManager.getInstance().saveDocument(document);
    }
  }

  @Nullable
  public VirtualFile findFileToPatch(final VirtualFile patchedDir, final int skipTopDirs) {
    VirtualFile file = findFileToPatchByName(patchedDir, skipTopDirs, myBeforeName);
    if (file == null) {
      file = findFileToPatchByName(patchedDir, skipTopDirs, myAfterName);
    }
    return file;
  }

  @Nullable
  private VirtualFile findFileToPatchByName(final VirtualFile patchedDir, final int skipTopDirs, final String fileName) {
    String[] pathNameComponents = fileName.split("/");
    VirtualFile fileToPatch = patchedDir;
    int lastComponentToFind = isNewFile() ? pathNameComponents.length-1 : pathNameComponents.length;
    for(int i=skipTopDirs; i<lastComponentToFind; i++) {
      fileToPatch = fileToPatch.findChild(pathNameComponents [i]);
      if (fileToPatch == null) {
        break;
      }
    }
    return fileToPatch;
  }

  private boolean isNewFile() {
    return myHunks.size() == 1 && myHunks.get(0).isNewContent();
  }

  private boolean isDeletedFile() {
    return myHunks.size() == 1 && myHunks.get(0).isDeletedContent();
  }
}