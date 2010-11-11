package com.jetbrains.python.debugger;

import com.google.common.collect.Maps;
import com.intellij.ide.util.AbstractTreeClassChooserDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.jetbrains.python.debugger.ui.PyClassTreeChooserDialog;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.HashMap;


public class PyExceptionBreakpointType
  extends XBreakpointType<XBreakpoint<PyExceptionBreakpointProperties>, PyExceptionBreakpointProperties> {

  public PyExceptionBreakpointType() {
    super("python-exception", "Python Exception Breakpoint", false);
  }


  @Override
  public PyExceptionBreakpointProperties createProperties() {
    return new PyExceptionBreakpointProperties("BaseException");
  }

  @Override
  public boolean isAddBreakpointButtonVisible() {
    return true;
  }

  @Override
  public XBreakpoint<PyExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    final PyClassTreeChooserDialog dialog = new PyClassTreeChooserDialog("Select Exception Class",
                                                                         project,
                                                                         GlobalSearchScope.allScope(project),
                                                                         new PyExceptionCachingFilter(), null);

    dialog.showDialog();


    // on ok
    final PyClass pyClass = dialog.getSelected();
    if (pyClass != null) {
      final String qualifiedName = pyClass.getQualifiedName();
      assert qualifiedName != null : "Qualified name of the class shouldn't be null";
      return ApplicationManager.getApplication().runWriteAction(new Computable<XBreakpoint<PyExceptionBreakpointProperties>>() {
        public XBreakpoint<PyExceptionBreakpointProperties> compute() {
          return XDebuggerManager.getInstance(project).getBreakpointManager()
            .addBreakpoint(PyExceptionBreakpointType.this, new PyExceptionBreakpointProperties(qualifiedName));
        }
      });
    }
    return null;
  }

  private static class PyExceptionCachingFilter implements AbstractTreeClassChooserDialog.Filter<PyClass> {
    private final HashMap<Integer, Pair<WeakReference<PyClass>, Boolean>> processedElements = Maps.newHashMap();

    public boolean isAccepted(@NotNull final PyClass pyClass) {
      final VirtualFile virtualFile = pyClass.getContainingFile().getVirtualFile();
      if (virtualFile == null) {
        return false;
      }

      final int key = pyClass.hashCode();
      final Pair<WeakReference<PyClass>, Boolean> pair = processedElements.get(key);
      boolean isException;
      if (pair == null || pair.first.get() != pyClass) {
        isException = PyUtil.isExceptionClass(pyClass);
        processedElements.put(key, Pair.create(new WeakReference<PyClass>(pyClass), isException));
      }
      else {
        isException = pair.second;
      }
      return isException;
    }
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }

  @Override
  public String getDisplayText(XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
    PyExceptionBreakpointProperties properties = breakpoint.getProperties();
    if (properties != null) {
      return properties.getException();
    }
    return "";
  }
}
