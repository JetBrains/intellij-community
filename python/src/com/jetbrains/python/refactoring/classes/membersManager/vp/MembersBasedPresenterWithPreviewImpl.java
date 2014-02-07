package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.refactoring.BaseRefactoringProcessor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import org.jetbrains.annotations.NotNull;

/**
 * Does refactoring with preview (based on {@link com.intellij.refactoring.BaseRefactoringProcessor}).
 * Child must implement {@link #createProcessor()} and return appropriate processor.
 * "Preview" button would be displayed.
 *
 * @param <T> view for this presenter
 * @author Ilya.Kazakevich
 */
public abstract class MembersBasedPresenterWithPreviewImpl<T extends MembersBasedView<?>> extends MembersBasedPresenterImpl<T> {


  /**
   * @param view                  view for this presenter
   * @param classUnderRefactoring class to refactor
   * @param infoStorage           info storage
   */
  protected MembersBasedPresenterWithPreviewImpl(@NotNull final T view,
                                                 @NotNull final PyClass classUnderRefactoring,
                                                 @NotNull final PyMemberInfoStorage infoStorage) {
    super(view, classUnderRefactoring, infoStorage);
  }

  @Override
  public boolean showPreview() {
    return true;
  }

  @Override
  protected void doRefactor() {
    myView.invokeRefactoring(createProcessor());
  }

  /**
   * @return processor for refactoring
   */
  @NotNull
  public abstract BaseRefactoringProcessor createProcessor();
}
