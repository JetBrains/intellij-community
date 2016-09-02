package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
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
  private final GlobalSearchScope mySearchScope;

  public StackFrameSelector(@NotNull Project project,
                            @NotNull List<StackFrameDescriptor> stack,
                            @NotNull GlobalSearchScope searchScope) {
    super(project, false);
    myProject = project;
    myStackFrame = stack;
    mySearchScope = searchScope;
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
          PsiClass psiClass = DebuggerUtils.findClass(selectedValue.path(), myProject, mySearchScope);
          if(psiClass != null) {
            ApplicationManager.getApplication().runReadAction(() -> {
              OpenFileHyperlinkInfo info =
                  new OpenFileHyperlinkInfo(myProject,
                      psiClass.getContainingFile().getVirtualFile(), selectedValue.line() - 1);
              info.navigate(myProject);
            });
          }
        }
        return false;
      }
    }.installOn(frameList);

    return new JBScrollPane(frameList);
  }
}
