// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TooltipWithClickableLinks;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;


@ApiStatus.Internal
public final class PyExceptionBreakpointType
  extends XBreakpointType<XBreakpoint<PyExceptionBreakpointProperties>, PyExceptionBreakpointProperties> {

  private static final String BASE_EXCEPTION = "BaseException";

  public PyExceptionBreakpointType() {
    super("python-exception", PyBundle.message("debugger.exception.breakpoint.type"));
  }

  @Override
  public @NotNull Icon getEnabledIcon() {
    return AllIcons.Debugger.Db_exception_breakpoint;
  }

  @Override
  public @NotNull Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_exception_breakpoint;
  }

  @Override
  public PyExceptionBreakpointProperties createProperties() {
    return new PyExceptionBreakpointProperties(BASE_EXCEPTION);
  }

  @Override
  public boolean isAddBreakpointButtonVisible() {
    return PyDebuggerFacade.getInstance().canChooseExceptionClass();
  }

  @Override
  public XBreakpoint<PyExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent) {
    String qualifiedName = PyDebuggerFacade.getInstance().chooseExceptionClass(project);
    if (qualifiedName != null) {
      return XDebuggerManager.getInstance(project).getBreakpointManager()
        .addBreakpoint(this, new PyExceptionBreakpointProperties(qualifiedName));
    }
    return null;
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
        return PyBundle.message("debugger.exception.breakpoint.any.exception");
      }
      return exception;
    }
    return "";
  }

  @Override
  public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull XBreakpoint<PyExceptionBreakpointProperties> breakpoint,
                                                               @NotNull Project project) {
    return PyDebuggerFacade.getInstance().createEditorsProvider();
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
  public XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> createCustomPropertiesPanel(@NotNull Project project) {
    return new PyExceptionBreakpointPropertiesPanel();
  }


  private static class PyExceptionBreakpointPropertiesPanel
    extends XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> {
    private JCheckBox myNotifyOnTerminateCheckBox;
    private JCheckBox myNotifyOnRaiseCheckBox;
    private JCheckBox myIgnoreLibrariesCheckBox;
    private JBLabel myWarningIcon;

    @Override
    public @NotNull JComponent getComponent() {
      myNotifyOnTerminateCheckBox = new JCheckBox(PyBundle.message("debugger.exception.breakpoint.on.termination"));
      myNotifyOnRaiseCheckBox = new JCheckBox(PyBundle.message("debugger.exception.breakpoint.on.raise"));
      myIgnoreLibrariesCheckBox = new JCheckBox(PyBundle.message("debugger.exception.breakpoint.ignore.library.files"));

      JBBox notificationsBox = JBBox.createVerticalBox();
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(myNotifyOnTerminateCheckBox, BorderLayout.NORTH);
      notificationsBox.add(panel);
      panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      panel.setBorder(JBUI.Borders.empty());
      panel.add(myNotifyOnRaiseCheckBox, Integer.valueOf(FlowLayout.LEFT));
      myWarningIcon = new JBLabel(AllIcons.General.BalloonWarning);
      IdeTooltipManager.getInstance().setCustomTooltip(
        myWarningIcon,
        new TooltipWithClickableLinks.ForBrowser(myWarningIcon,
                                                 PyBundle.message("debugger.warning.message")));
      myWarningIcon.setBorder(JBUI.Borders.emptyLeft(5));
      panel.add(myWarningIcon);
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
      panel.setBorder(IdeBorderFactory.createTitledBorder(PyBundle.message("debugger.exception.breakpoint.activation.policy")));

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


