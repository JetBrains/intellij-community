package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;

/**
 * @author nik
 */
public class XSourcePositionImpl implements XSourcePosition {
  private VirtualFile myFile;
  private int myLine;
  private int myOffset;

  public XSourcePositionImpl(final VirtualFile file, final int line) {
    myFile = file;
    myLine = line;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    myOffset = document.getLineStartOffset(line);
  }

  public int getLine() {
    return myLine;
  }

  public int getOffset() {
    return myOffset;
  }

  public VirtualFile getFile() {
    return myFile;
  }
}
