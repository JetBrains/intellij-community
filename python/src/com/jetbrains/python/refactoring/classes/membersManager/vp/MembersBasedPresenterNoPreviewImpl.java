package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Presenter that has not preview. Children should implement {@link #refactorNoPreview()}.
 * To "preview" button would be displayed
 * @param <T> view for this presenter
 * @author Ilya.Kazakevich
 */

public abstract class MembersBasedPresenterNoPreviewImpl<T extends MembersBasedView<?>> extends MembersBasedPresenterImpl<T> {
  /**
   *
   * @param view view for this presenter
   * @param classUnderRefactoring class to refactor
   * @param infoStorage info storage
   */
  protected MembersBasedPresenterNoPreviewImpl(@NotNull final T view,
                                               @NotNull final PyClass classUnderRefactoring,
                                               @NotNull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage);
  }

  @Override
  public boolean showPreview() {
    return false;
  }

  @Override
  void doRefactor() {
    CommandProcessor.getInstance().executeCommand(myClassUnderRefactoring.getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            refactorNoPreview();
          }
        });
      }
    }, getCommandName(), null);
    myView.close();
  }

  /**
   * @return Command name for this preview
   */
  @NotNull
  protected abstract String getCommandName();

  /**
   * Do refactor with out of preview. Implement this method to do refactoring.
   */
  protected abstract void refactorNoPreview();
}
