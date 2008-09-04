package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XSourcePositionImpl implements XSourcePosition {
  private VirtualFile myFile;
  private int myLine;
  private int myOffset;

  private XSourcePositionImpl(@NotNull VirtualFile file, final int line, final int offset) {
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

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public static XSourcePositionImpl createByOffset(@Nullable VirtualFile file, final int offset) {
    if (file == null) return null;
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null || offset >= document.getTextLength()) {
      return null;
    }
    int line = document.getLineNumber(offset);
    return new XSourcePositionImpl(file, line, offset);
  }

  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, final int line) {
    if (file == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null || line >= document.getLineCount()) {
      return null;
    }
    
    return new XSourcePositionImpl(file, line, document.getLineStartOffset(line));
  }
}
