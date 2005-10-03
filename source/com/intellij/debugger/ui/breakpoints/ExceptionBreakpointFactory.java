/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class ExceptionBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new ExceptionBreakpoint(project);
  }

  public Icon getIcon() {
    return ExceptionBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return ExceptionBreakpoint.DISABLED_ICON;
  }

  public BreakpointPanel createBreakpointPanel(final Project project, DialogWrapper parentDialog) {
    BreakpointPanel panel = new BreakpointPanel(project, new ExceptionBreakpointPropertiesPanel(project), createActions(project), getBreakpointCategory(), DebuggerBundle.message("exception.breakpoints.tab.title"), HelpID.EXCEPTION_BREAKPOINTS) {
      public void setBreakpoints(Breakpoint[] breakpoints) {
        super.setBreakpoints(breakpoints);
        final AnyExceptionBreakpoint anyExceptionBreakpoint = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().getAnyExceptionBreakpoint();
        boolean found = false;
        for (Breakpoint breakpoint : breakpoints) {
          if (breakpoint.equals(anyExceptionBreakpoint)) {
            found = true;
            break;
          }
        }
        if (!found) {
          insertBreakpointAt(anyExceptionBreakpoint, 0);
        }
      }
    };
    panel.getTree().setGroupByMethods(false);
    return panel;
  }

  private BreakpointPanelAction[] createActions(final Project project) {
    return new BreakpointPanelAction[]{
      new SwitchViewAction(),
      new AddExceptionBreakpointAction(project),
      new RemoveAction(project) {
        public void update() {
          super.update();
          if (getButton().isEnabled()) {
            Breakpoint[] selectedBreakpoints = getPanel().getSelectedBreakpoints();
            for (int i = 0; i < selectedBreakpoints.length; i++) {
              Breakpoint bp = selectedBreakpoints[i];
              if (bp instanceof AnyExceptionBreakpoint) {
                getButton().setEnabled(false);
              }
            }
          }
        }
      },
      new ToggleGroupByClassesAction(),
      new ToggleFlattenPackagesAction(),
    };
  }

  public String getBreakpointCategory() {
    return ExceptionBreakpoint.CATEGORY;
  }

  public String getComponentName() {
    return "ExceptionBreakpointFactory";
  }

  private class AddExceptionBreakpointAction extends AddAction {
    private final Project myProject;

    public AddExceptionBreakpointAction(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      final PsiClass throwableClass = PsiManager.getInstance(myProject).findClass("java.lang.Throwable", GlobalSearchScope.allScope(myProject));
      TreeClassChooser chooser =
        TreeClassChooserFactory.getInstance(myProject).createInheritanceClassChooser(
          DebuggerBundle.message("add.exception.breakpoint.classchooser.title"), GlobalSearchScope.allScope(myProject),
                                                                                     throwableClass, true, true, null);
      chooser.showDialog();
      PsiClass selectedClass = chooser.getSelectedClass();
      String qName = (selectedClass != null)? selectedClass.getQualifiedName() : null;

      if (qName != null && qName.length() > 0) {
        ExceptionBreakpoint breakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addExceptionBreakpoint(qName, ((PsiJavaFile)selectedClass.getContainingFile()).getPackageName());
        getPanel().addBreakpoint(breakpoint);
      }
    }
  }

}
