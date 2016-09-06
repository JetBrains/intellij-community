package org.jetbrains.debugger.memory.view;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.debugger.memory.utils.KeyboardUtils;
import org.jetbrains.debugger.memory.utils.StackFrameDescriptor;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
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
    JBList list = createStackList();
    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Select stack frame")
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .createPopup();
    popup.showInFocusCenter();
  }

  private JBList createStackList() {
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

    frameList.setCellRenderer(new ColoredListCellRenderer<StackFrameDescriptor>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends StackFrameDescriptor> list,
                                           StackFrameDescriptor value, int index, boolean isSelected, boolean hasFocus) {
        append(String.format("%s:%d, %s", value.methodName(), value.line(), value.className()));
        append(String.format(" (%s)", value.packageName()), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
      }
    });

    frameList.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (KeyboardUtils.isEnterKey(e.getKeyCode())) {
          navigateToSelectedFrame(frameList, true);
        }
      }
    });

    frameList.addListSelectionListener(e -> navigateToSelectedFrame(frameList, false));

    return frameList;
  }

  private void navigateToSelectedFrame(@NotNull JBList frameList, boolean focusEditor) {
    StackFrameDescriptor frame = (StackFrameDescriptor) frameList.getSelectedValue();
    if (frame != null) {
      PsiClass psiClass = DebuggerUtils.findClass(frame.path(), myProject, myScope);
      if (psiClass != null) {
        ApplicationManager.getApplication().runReadAction(() -> {
          OpenFileHyperlinkInfo info =
              new OpenFileHyperlinkInfo(myProject, psiClass.getContainingFile().getVirtualFile(),
                  frame.line() - 1);
          OpenFileDescriptor descriptor = info.getDescriptor();
          if (descriptor != null) {
            FileEditorManager.getInstance(myProject).openTextEditor(descriptor, focusEditor);
          }
        });
      }
    }
  }
}
