package org.jetbrains.debugger.memory.view;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class StackFrameSelector extends DialogWrapper {
  private final Project myProject;
  private final List<StackFrameDescriptor> myStackFrame;
  private final Editor myEditor;

  public StackFrameSelector(@NotNull Project project,
                            @NotNull List<StackFrameDescriptor> stack,
                            @NotNull Editor editor) {
    super(project, false);
    myStackFrame = stack;
    myProject = project;
    myEditor = editor;
    setModal(false);
    init();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createSouthPanel() {
    return null;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JBList frameList = new JBList(new AbstractListModel() {
      @Override
      public int getSize() {
        return myStackFrame.size();
      }

      @Override
      public Object getElementAt(int index) {
        return myStackFrame.get(index);
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        StackFrameDescriptor selectedValue = (StackFrameDescriptor) frameList.getSelectedValue();
        if (selectedValue != null) {
          PsiClass psiClass = DebuggerUtils.findClass(selectedValue.path(), myProject, GlobalSearchScope.allScope(myProject));
          if (psiClass != null) {
            NavigationUtil.openFileWithPsiElement(psiClass, true, false);
            myEditor.getCaretModel().removeSecondaryCarets();
            myEditor.getCaretModel().moveToLogicalPosition(new LogicalPosition(selectedValue.line() - 1, 0, false));
            myEditor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
            myEditor.getSelectionModel().removeSelection();
            IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true);
          }
        }
        return false;
      }
    }.installOn(frameList);

    return new JBScrollPane(frameList);
  }
}
