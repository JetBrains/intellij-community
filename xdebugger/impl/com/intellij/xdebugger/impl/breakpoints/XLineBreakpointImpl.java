package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, XLineBreakpointImpl.LineBreakpointState<P>> implements XLineBreakpoint<P> {
  private @Nullable RangeHighlighter myHighlighter;
  private final XLineBreakpointType<P> myType;
  private Icon myIcon;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type, XBreakpointManagerImpl breakpointManager, String url, int line, final @Nullable P properties) {
    super(type, breakpointManager, properties, new LineBreakpointState<P>(true, type.getId(), url, line));
    myType = type;
  }

  private XLineBreakpointImpl(final XLineBreakpointType<P> type, XBreakpointManagerImpl breakpointManager, final LineBreakpointState<P> breakpointState) {
    super(type, breakpointManager, breakpointState);
    myType = type;
  }

  public void updateUI() {
    Document document = getDocument();
    if (document == null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    removeHighlighter();
    MarkupModelEx markupModel = (MarkupModelEx)document.getMarkupModel(getProject());
    RangeHighlighter highlighter = markupModel.addPersistentLineHighlighter(getLine(), HighlighterLayer.CARET_ROW + 1, attributes);
    updateIcon();
    setupGutterRenderer(highlighter);
    myHighlighter = highlighter;
  }

  @Nullable
  public Document getDocument() {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
    if (file == null) return null;
    return FileDocumentManager.getInstance().getDocument(file);
  }

  private void setupGutterRenderer(final RangeHighlighter highlighter) {
    highlighter.setGutterIconRenderer(new BreakpointGutterIconRenderer());
  }

  @NotNull
  public XLineBreakpointType<P> getType() {
    return myType;
  }

  private void updateIcon() {
    myIcon = isEnabled() ? myType.getEnabledIcon() : myType.getDisabledIcon();
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

  public void dispose() {
    removeHighlighter();
  }

  private void removeHighlighter() {
    if (myHighlighter != null) {
      myHighlighter.getDocument().getMarkupModel(getProject()).removeHighlighter(myHighlighter);
      myHighlighter = null;
    }
  }

  private Icon getIcon() {
    if (myIcon == null) {
      updateIcon();
    }
    return myIcon;
  }

  public String getDescription() {
    return XDebuggerBundle.message("xdebugger.line.breakpoint.tooltip", myType.getDisplayText(this));
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

    public XBreakpointBase<XLineBreakpoint<P>,P, ?> createBreakpoint(@NotNull final XLineBreakpointType<P> type, @NotNull XBreakpointManagerImpl breakpointManager) {
      return new XLineBreakpointImpl<P>(type, breakpointManager, this);
    }
  }

  private class BreakpointGutterIconRenderer extends GutterIconRenderer {
    @NotNull
    public Icon getIcon() {
      return XLineBreakpointImpl.this.getIcon();
    }

    @Nullable
    public AnAction getClickAction() {
      return new MyRemoveBreakpointAction();
    }

    @Nullable
    public AnAction getMiddleButtonClickAction() {
      return new MyToggleBreakpointAction();
    }

    @Nullable
  public ActionGroup getPopupMenuActions() {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new MyRemoveBreakpointAction());
      group.add(new MyToggleBreakpointAction());
      group.add(new Separator());
      group.add(new MyViewBreakpointPropertiesAction());
      return group;
    }

    @Nullable
    public String getTooltipText() {
      return getDescription();
    }
  }

  private class MyRemoveBreakpointAction extends AnAction {
    private MyRemoveBreakpointAction() {
      super(XDebuggerBundle.message("xdebugger.remove.line.breakpoint.action.text"));
    }

    public void actionPerformed(final AnActionEvent e) {
      XDebuggerUtil.getInstance().removeBreakpoint(getProject(), XLineBreakpointImpl.this);
    }
  }

  private class MyToggleBreakpointAction extends AnAction {
    private MyToggleBreakpointAction() {
      super(isEnabled() ? XDebuggerBundle.message("xdebugger.disable.breakpoint.action.text") : XDebuggerBundle.message("xdebugger.enable.breakpoint.action.text"));
    }

    public void actionPerformed(final AnActionEvent e) {
      setEnabled(!isEnabled());
    }
  }

  private class MyViewBreakpointPropertiesAction extends AnAction {
    private MyViewBreakpointPropertiesAction() {
      super(XDebuggerBundle.message("xdebugger.view.breakpoint.properties.action"));
    }

    public void actionPerformed(final AnActionEvent e) {
    }
  }
}
