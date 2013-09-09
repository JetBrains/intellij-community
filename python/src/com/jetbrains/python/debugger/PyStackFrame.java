package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;


public class PyStackFrame extends XStackFrame {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyStackFrame");

  private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();

  private Project myProject;
  private final PyFrameAccessor myDebugProcess;
  private final PyStackFrameInfo myFrameInfo;
  private final XSourcePosition myPosition;

  public PyStackFrame(@NotNull Project project,
                      @NotNull final PyFrameAccessor debugProcess,
                      @NotNull final PyStackFrameInfo frameInfo, XSourcePosition position) {
    myProject = project;
    myDebugProcess = debugProcess;
    myFrameInfo = frameInfo;
    myPosition = position;
  }

  @Override
  public Object getEqualityObject() {
    return STACK_FRAME_EQUALITY_OBJECT;
  }

  @Override
  public XSourcePosition getSourcePosition() {
    return myPosition;
  }

  @Override
  public XDebuggerEvaluator getEvaluator() {
    return new PyDebuggerEvaluator(myProject, myDebugProcess);
  }

  @Override
  public void customizePresentation(ColoredTextContainer component) {
    component.setIcon(AllIcons.Debugger.StackFrame);

    if (myPosition == null) {
      component.append("<frame not available>", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return;
    }

    boolean isExternal = true;
    final VirtualFile file = myPosition.getFile();
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      isExternal = !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file);
    }

    component.append(myFrameInfo.getName(), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(", ", gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(myPosition.getFile().getName(), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(":", gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(Integer.toString(myPosition.getLine() + 1), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
  }

  private static SimpleTextAttributes gray(SimpleTextAttributes attributes, boolean gray) {
    if (!gray) {
      return attributes;
    }
    else {
      return (attributes.getStyle() & SimpleTextAttributes.STYLE_ITALIC) != 0
             ? SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
    }
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          final XValueChildrenList values = myDebugProcess.loadFrame();
          if (!node.isObsolete()) {
            node.addChildren(values != null ? values : XValueChildrenList.EMPTY, true);
          }
        }
        catch (PyDebuggerException e) {
          if (!node.isObsolete()) {
            node.setErrorMessage("Unable to display frame variables");
          }
          LOG.warn(e);
        }
      }
    });
  }

  public String getThreadId() {
    return myFrameInfo.getThreadId();
  }

  public String getFrameId() {
    return myFrameInfo.getId();
  }

  public String getThreadFrameId() {
    return myFrameInfo.getThreadId() + ":" + myFrameInfo.getId();
  }

  protected XSourcePosition getPosition() {
    return myPosition;
  }
}
