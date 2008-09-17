package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.xdebugger.impl.actions.ViewBreakpointsAction;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author nik
 */
public class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, XLineBreakpointImpl.LineBreakpointState<P>> implements XLineBreakpoint<P> {
  private @Nullable RangeHighlighter myHighlighter;
  private final XLineBreakpointType<P> myType;
  private Icon myIcon;
  private XSourcePosition mySourcePosition;
  @NonNls private static final String BR_NBSP = "<br>&nbsp;";

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
    RangeHighlighter highlighter = markupModel.addPersistentLineHighlighter(getLine(), DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
                                                                            attributes);
    if (highlighter != null) {
      updateIcon();
      setupGutterRenderer(highlighter);
    }
    myHighlighter = highlighter;
  }

  @Nullable
  public Document getDocument() {
    VirtualFile file = getFile();
    if (file == null) return null;
    return FileDocumentManager.getInstance().getDocument(file);
  }

  @Nullable
  private VirtualFile getFile() {
    return VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
  }

  private void setupGutterRenderer(final RangeHighlighter highlighter) {
    highlighter.setGutterIconRenderer(new BreakpointGutterIconRenderer());
  }

  @NotNull
  public XLineBreakpointType<P> getType() {
    return myType;
  }

  private void updateIcon() {
    myIcon = calculateIcon();
  }

  @NotNull
  private Icon calculateIcon() {
    if (!isEnabled()) {
      return myType.getDisabledIcon();
    }

    XDebugSessionImpl session = getBreakpointManager().getDebuggerManager().getCurrentSession();
    if (session == null) {
      if (getBreakpointManager().getDependentBreakpointManager().getMasterBreakpoint(this) != null) {
        return myType.getDisabledDependentIcon();
      }
    }
    else {
      if (session.areBreakpointsMuted()) {
        return myType.getDisabledIcon();
      }
      if (session.isDisabledSlaveBreakpoint(this)) {
        return myType.getDisabledDependentIcon();
      }
      CustomizedBreakpointPresentation presentation = session.getBreakpointPresentation(this);
      if (presentation != null) {
        Icon icon = presentation.getIcon();
        if (icon != null) {
          return icon;                  
        }
      }
    }
    return myType.getEnabledIcon();
  }

  @Nullable
  private CustomizedBreakpointPresentation getCustomizedPresentation() {
    final XDebugSessionImpl currentSession = getBreakpointManager().getDebuggerManager().getCurrentSession();
    return currentSession != null ? currentSession.getBreakpointPresentation(this) : null;
  }

  public int getLine() {
    return myState.getLine();
  }

  public String getFileUrl() {
    return myState.getFileUrl();
  }

  public String getPresentableFilePath() {
    String url = getFileUrl();
    if (url != null && LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
      return FileUtil.toSystemDependentName(VfsUtil.urlToPath(url));
    }
    return url != null ? url : "";
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }

  public XSourcePosition getSourcePosition() {
    if (mySourcePosition == null) {
      mySourcePosition = XSourcePositionImpl.create(getFile(), getLine());
    }
    return mySourcePosition;
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
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("<html><body>");
      builder.append(myType.getDisplayText(this));

      String errorMessage = getErrorMessage();
      if (errorMessage != null) {
        builder.append(BR_NBSP);
        builder.append("<font color=\"red\">");
        builder.append(errorMessage);
        builder.append("</font>");
      }

      SuspendPolicy suspendPolicy = getSuspendPolicy();
      if (suspendPolicy == SuspendPolicy.THREAD) {
        builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.thread"));
      }
      else if (suspendPolicy == SuspendPolicy.NONE) {
        builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.suspend.policy.none"));
      }

      String condition = getCondition();
      if (condition != null) {
        builder.append(BR_NBSP);
        builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.condition"));
        builder.append("&nbsp;");
        builder.append(condition);
      }

      if (isLogMessage()) {
        builder.append(BR_NBSP).append(XDebuggerBundle.message("xbreakpoint.tooltip.log.message"));
      }
      String logExpression = getLogExpression();
      if (logExpression != null) {
        builder.append(BR_NBSP);
        builder.append(XDebuggerBundle.message("xbreakpoint.tooltip.log.expression"));
        builder.append("&nbsp;");
        builder.append(logExpression);
      }

      XBreakpoint<?> masterBreakpoint = getBreakpointManager().getDependentBreakpointManager().getMasterBreakpoint(this);
      if (masterBreakpoint != null) {
        builder.append(BR_NBSP);
        String str = XDebuggerBundle.message("xbreakpoint.tooltip.depends.on");
        builder.append(str);
        builder.append("&nbsp;");
        builder.append(XBreakpointUtil.getDisplayText(masterBreakpoint));
      }

      builder.append("</body><html");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @Nullable
  private String getErrorMessage() {
    CustomizedBreakpointPresentation presentation = getCustomizedPresentation();
    return presentation != null ? presentation.getErrorMessage() : null;
  }

  public void updatePosition() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      Document document = myHighlighter.getDocument();
      setLine(document.getLineNumber(myHighlighter.getStartOffset()));
    }
  }

  private void setLine(final int line) {
    if (getLine() != line) {
      myState.setLine(line);
      fireBreakpointChanged();
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
      group.add(new ViewBreakpointsAction(XDebuggerBundle.message("xdebugger.view.breakpoint.properties.action"), XLineBreakpointImpl.this));
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

}
