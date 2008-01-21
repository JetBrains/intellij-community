package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XSourcePositionImpl implements XSourcePosition {
  private VirtualFile myFile;
  private int myLine;
  private int myOffset;

  private XSourcePositionImpl(final VirtualFile file, final int line, final int offset) {
    myFile = file;
    myLine = line;
    myOffset = offset;
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

  @Nullable
  public static XSourcePositionImpl create(final VirtualFile file, final int line) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null || line >= document.getLineCount()) {
      return null;
    }
    return new XSourcePositionImpl(file, line, document.getLineStartOffset(line));
  }
}
