package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, XLineBreakpointImpl.LineBreakpointState<P>> implements XLineBreakpoint<P> {
  private @Nullable RangeHighlighter myHighlighter;
  private final XLineBreakpointType<P> myType;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type, Project project, String url, int line, final @Nullable P properties) {
    super(type, project, properties, new LineBreakpointState<P>(true, type.getId(), url, line));
    myType = type;
    init();
  }

  private XLineBreakpointImpl(final XLineBreakpointType<P> type, Project project, final LineBreakpointState<P> breakpointState) {
    super(type, project, breakpointState);
    myType = type;
    init();
  }

  private void init() {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
    if (file == null) return;
    Document document = FileDocumentManager.getInstance().getDocument(file);

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    myHighlighter = ((MarkupModelEx)document.getMarkupModel(myProject)).addPersistentLineHighlighter(getLine(), HighlighterLayer.CARET_ROW + 1, attributes);
  }

  @NotNull
  public XLineBreakpointType<P> getType() {
    return myType;
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
  public static class LineBreakpointState<P extends XBreakpointProperties> extends XBreakpointBase.BreakpointState<XLineBreakpoint<P>, P, XLineBreakpointType<P>> {
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

    public XBreakpointBase<XLineBreakpoint<P>,P, ?> createBreakpoint(@NotNull final XLineBreakpointType<P> type, @NotNull final Project project) {
      return new XLineBreakpointImpl<P>(type, project, this);
    }
  }
}
