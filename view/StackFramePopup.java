package org.jetbrains.debugger.memory.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import java.util.List;

public class StackFramePopup {
  private final Project myProject;
  private final List<StackFrameDescriptor> myStackFrame;
  private final GlobalSearchScope myScope;

  public StackFramePopup(@NotNull Project project,
                         @NotNull List<StackFrameDescriptor> stack,
                         @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    myStackFrame = stack;
    myScope = searchScope;
  }

  public void show() {
    StackFrameList list = new StackFrameList(myProject, myStackFrame, myScope);
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        list.navigateToSelectedValue(false);
      }
    });

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Select stack frame")
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .setItemChoosenCallback(() -> list.navigateToSelectedValue(true))
        .createPopup();

    list.setSelectedIndex(1);
    popup.showInFocusCenter();
  }
}
