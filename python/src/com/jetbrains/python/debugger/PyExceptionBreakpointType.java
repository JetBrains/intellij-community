/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.AbstractTreeClassChooserDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.util.HashMap;


public class PyExceptionBreakpointType
  extends XBreakpointType<XBreakpoint<PyExceptionBreakpointProperties>, PyExceptionBreakpointProperties> {

  private static final String BASE_EXCEPTION = "BaseException";

  public PyExceptionBreakpointType() {
    super("python-exception", "Python Exception Breakpoint");
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @Override
  public PyExceptionBreakpointProperties createProperties() {
    return new PyExceptionBreakpointProperties(BASE_EXCEPTION);
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
        @Override
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

    @Override
    public boolean isAccepted(@NotNull final PyClass pyClass) {
      final VirtualFile virtualFile = pyClass.getContainingFile().getVirtualFile();
      if (virtualFile == null) {
        return false;
      }

      final int key = pyClass.hashCode();
      final Pair<WeakReference<PyClass>, Boolean> pair = processedElements.get(key);
      boolean isException;
      if (pair == null || pair.first.get() != pyClass) {
        isException = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
          @Override
          public Boolean compute() {
            return PyUtil.isExceptionClass(pyClass);
          }
        });
        processedElements.put(key, Pair.create(new WeakReference<>(pyClass), isException));
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
      String exception = properties.getException();
      if (BASE_EXCEPTION.equals(exception)) {
        return "Any exception";
      }
      return exception;
    }
    return "";
  }

  @Override
  public XBreakpoint<PyExceptionBreakpointProperties> createDefaultBreakpoint(@NotNull XBreakpointCreator<PyExceptionBreakpointProperties> creator) {
    final XBreakpoint<PyExceptionBreakpointProperties> breakpoint = creator.createBreakpoint(createDefaultBreakpointProperties());
    breakpoint.setEnabled(true);
    return breakpoint;
  }

  private static PyExceptionBreakpointProperties createDefaultBreakpointProperties() {
    PyExceptionBreakpointProperties p = new PyExceptionBreakpointProperties(BASE_EXCEPTION);
    p.setNotifyOnTerminate(true);
    p.setNotifyOnlyOnFirst(false);
    return p;
  }

  @Override
  public XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> createCustomPropertiesPanel() {
    return new PyExceptionBreakpointPropertiesPanel();
  }


  private static class PyExceptionBreakpointPropertiesPanel
    extends XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> {
    private JCheckBox myNotifyOnTerminateCheckBox;
    private JCheckBox myNotifyOnRaiseCheckBox;
    private JCheckBox myIgnoreLibrariesCheckBox;

    @NotNull
    @Override
    public JComponent getComponent() {
      myNotifyOnTerminateCheckBox = new JCheckBox("On termination");
      myNotifyOnRaiseCheckBox = new JCheckBox("On raise");
      myIgnoreLibrariesCheckBox = new JCheckBox("Ignore library files");

      Box notificationsBox = Box.createVerticalBox();
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myNotifyOnTerminateCheckBox, BorderLayout.NORTH);
      notificationsBox.add(panel);
      panel = new JPanel(new BorderLayout());
      panel.add(myNotifyOnRaiseCheckBox, BorderLayout.NORTH);
      notificationsBox.add(panel);
      panel = new JPanel(new BorderLayout());
      panel.add(myIgnoreLibrariesCheckBox, BorderLayout.NORTH);
      notificationsBox.add(panel);

      panel = new JPanel(new BorderLayout());
      JPanel innerPanel = new JPanel(new BorderLayout());
      innerPanel.add(notificationsBox, BorderLayout.CENTER);
      innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
      innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
      panel.add(innerPanel, BorderLayout.NORTH);
      panel.setBorder(IdeBorderFactory.createTitledBorder("Activation policy", true));

      return panel;
    }

    @Override
    public void saveTo(@NotNull XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
      breakpoint.getProperties().setNotifyOnTerminate(myNotifyOnTerminateCheckBox.isSelected());
      breakpoint.getProperties().setNotifyOnlyOnFirst(myNotifyOnRaiseCheckBox.isSelected());
      breakpoint.getProperties().setIgnoreLibraries(myIgnoreLibrariesCheckBox.isSelected());
    }

    @Override
    public void loadFrom(@NotNull XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
      myIgnoreLibrariesCheckBox.setSelected(breakpoint.getProperties().isIgnoreLibraries());
      myNotifyOnTerminateCheckBox.setSelected(breakpoint.getProperties().isNotifyOnTerminate());
      myNotifyOnRaiseCheckBox.setSelected(breakpoint.getProperties().isNotifyOnlyOnFirst());
    }
  }
}


