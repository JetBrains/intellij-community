package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
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
    if (document == null) {
      return null;
    }
    int line = offset < document.getTextLength() ? document.getLineNumber(offset) : -1;
    return new XSourcePositionImpl(file, line, offset);
  }

  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, final int line) {
    if (file == null) return null;

    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) {
      return null;
    }
    int offset = line < document.getLineCount() ? document.getLineStartOffset(line) : -1;
    return new XSourcePositionImpl(file, line, offset);
  }

  @NotNull
  public Navigatable createNavigatable(final @NotNull Project project) {
    return myOffset != -1 ? new OpenFileDescriptor(project, myFile, myOffset) : new OpenFileDescriptor(project, myFile, getLine(), 0);
  }
}
