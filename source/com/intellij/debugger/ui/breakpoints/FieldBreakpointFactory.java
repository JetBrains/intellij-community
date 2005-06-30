/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class FieldBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new FieldBreakpoint(project);
  }

  public Icon getIcon() {
    return FieldBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return FieldBreakpoint.DISABLED_ICON;
  }

  public BreakpointPanel createBreakpointPanel(final Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel = new BreakpointPanel(project, new FieldBreakpointPropertiesPanel(project), new BreakpointPanelAction[] {
      new SwitchViewAction(),
      new AddFieldBreakpointAction(project),
      new GotoSourceAction(project) {
        public void actionPerformed(ActionEvent e) {
          super.actionPerformed(e);
          parentDialog.close(DialogWrapper.OK_EXIT_CODE);
        }
      },
      new ViewSourceAction(project),
      new RemoveAction(project),
      new ToggleGroupByClassesAction(),
      new ToggleFlattenPackagesAction(),
    }, getBreakpointCategory(), "Field Watchpoints", HelpID.FIELD_WATCHPOINTS);
    panel.getTree().setGroupByMethods(false);
    return panel;
  }

  public String getBreakpointCategory() {
    return FieldBreakpoint.CATEGORY;
  }

  public String getComponentName() {
    return "FieldBreakpointFactory";
  }

  private class AddFieldBreakpointAction extends AddAction {
    private final Project myProject;

    public AddFieldBreakpointAction(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      AddFieldBreakpointDialog dialog = new AddFieldBreakpointDialog(myProject) {
        protected boolean validateData() {
          String className = getClassName();
          if (className.length() == 0) {
            Messages.showMessageDialog(myProject, "Cannot add watchpoint: a class name is not specified", "Add Field Watchpoint", Messages.getErrorIcon());
            return false;
          }
          String fieldName = getFieldName();
          if (fieldName.length() == 0) {
            Messages.showMessageDialog(myProject, "Cannot add watchpoint: a field name is not specified", "Add Field Watchpoint", Messages.getErrorIcon());
            return false;
          }
          PsiClass psiClass = PsiManager.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
          if (psiClass != null) {
            PsiFile  psiFile  = psiClass.getContainingFile();
            Document document = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
            if(document != null) {
              PsiField field = psiClass.findFieldByName(fieldName, true);
              if(field != null) {
                int line = document.getLineNumber(field.getTextOffset());
                FieldBreakpoint fieldBreakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addFieldBreakpoint(document, line, fieldName);
                if (fieldBreakpoint != null) {
                  getPanel().addBreakpoint(fieldBreakpoint);
                  return true;
                }
              }
              else {
                Messages.showMessageDialog(
                  myProject,
                  "Cannot create a field watchpoint for \"" + className + "." + fieldName + "\".\nField \"" + fieldName + "\" not found",
                  "Error",
                  Messages.getErrorIcon()
                );

              }
            }
          } else {
            Messages.showMessageDialog(
              myProject,
              "Cannot create a field watchpoint for \"" + className + "." + fieldName + "\".\nNo sources for class \"" + className + "\"",
              "Error",
              Messages.getErrorIcon()
            );
          }
          return false;
        }
      };
      dialog.show();
    }
  }

}
