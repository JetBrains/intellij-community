package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XLineBreakpointImpl<T extends XBreakpointProperties> extends XBreakpointBase<T, XLineBreakpointImpl.LineBreakpointState> implements XLineBreakpoint<T> {
  private @Nullable RangeHighlighter myHighlighter;

  public XLineBreakpointImpl(final XBreakpointType<T> type, Project project, String url, int line, final @Nullable T properties) {
    super(type, project, properties, new LineBreakpointState(true, type.getId(), url, line));
    init();
  }

  private XLineBreakpointImpl(final XBreakpointType<T> type, Project project, final LineBreakpointState breakpointState) {
    super(type, project, breakpointState);
    init();
  }

  private void init() {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
    if (file == null) return;
    Document document = FileDocumentManager.getInstance().getDocument(file);

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(/*DebuggerColors.BREAKPOINT_ATTRIBUTES*/null);

    myHighlighter = ((MarkupModelEx)document.getMarkupModel(myProject)).addPersistentLineHighlighter(getLine(), HighlighterLayer.CARET_ROW + 1, attributes);
  }

  public int getLine() {
    return getState().getLine();
  }

  public String getFileUrl() {
    return getState().getFileUrl();
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }

  @NotNull
  public XSourcePosition getSourcePosition() {
    return new XSourcePositionImpl();
  }

  public boolean isValid() {
    return myHighlighter != null && myHighlighter.isValid();
  }

  public void updatePosition() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      Document document = myHighlighter.getDocument();
      getState().setLine(document.getLineNumber(myHighlighter.getStartOffset()));
    }
  }

  @Tag("line-breakpoint")
  public static class LineBreakpointState extends XBreakpointBase.BreakpointState {
    private String myFileUrl;
    private int myLine;

    public LineBreakpointState() {
    }

    public LineBreakpointState(final boolean enabled, final String typeId, final String fileUrl, final int line) {
      super(enabled, typeId);
      myFileUrl = fileUrl;
      myLine = line;
    }

    @Tag("url")
    public String getFileUrl() {
      return myFileUrl;
    }

    public void setFileUrl(final String fileUrl) {
      myFileUrl = fileUrl;
    }

    @Tag("line")
    public int getLine() {
      return myLine;
    }

    public void setLine(final int line) {
      myLine = line;
    }

    public <T extends XBreakpointProperties> XBreakpointBase<T, ?> createBreakpoint(@NotNull final XBreakpointType<T> type,
                                                                                    final Project project) {
      return new XLineBreakpointImpl<T>(type, project, this);
    }
  }
}
