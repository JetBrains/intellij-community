package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.PyMemberInfoStorage;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

/**
 * All presenters that use members inherits this class.
 * <strong>Warning</strong>: Do not inherit it directly.
 * Check {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterNoPreviewImpl}
 * or {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersBasedPresenterWithPreviewImpl} instead
 *
 * @param <T> view for that presenter
 * @author Ilya.Kazakevich
 */
abstract class MembersBasedPresenterImpl<T extends MembersBasedView<?>> implements MembersBasedPresenter {
  @NotNull
  protected final T myView;
  @NotNull
  protected final PyClass myClassUnderRefactoring;
  @NotNull
  protected final PyMemberInfoStorage myStorage;

  /**
   * @param view                  View for presenter
   * @param classUnderRefactoring class to be refactored
   * @param infoStorage           info storage
   */
  MembersBasedPresenterImpl(@NotNull final T view,
                            @NotNull final PyClass classUnderRefactoring,
                            @NotNull final PyMemberInfoStorage infoStorage) {
    myView = view;
    myClassUnderRefactoring = classUnderRefactoring;
    myStorage = infoStorage;
  }

  //TODO: Mark Async ?
  @Override
  public void okClicked() {
    final MultiMap<PyClass, PyMemberInfo<?>> conflicts = getConflicts();
    if (conflicts.isEmpty() || myView.showConflictsDialog(conflicts)) {
      try {
        validateView();
        doRefactor();
      }
      catch (final BadDataException e) {
        myView.showError(e.getMessage()); //Show error message if presenter says view in invalid
      }
    }
  }

  /**
   * Validates view (used by presenter to check if view is valid).
   * When overwrite, <strong>always</strong> call "super" <strong>first</strong>!
   * Throw {@link com.jetbrains.python.refactoring.classes.membersManager.vp.BadDataException} in case of error.
   * Do nothing, otherwise.
   * Method is designed to be overwritten and exception is used to simplify this process: children do not need parent's result.
   * They just call super.
   *
   * @throws BadDataException
   */
  protected void validateView() throws BadDataException {
    if (myView.getSelectedMemberInfos().isEmpty()) {
      throw new BadDataException(RefactoringBundle.message("no.members.selected"));
    }
  }

  /**
   * Does refactoring itself
   */
  abstract void doRefactor();

  /**
   * Checks if one of destination classes already has members that should be moved, so conflict would take place.
   * @return map of conflicts (if any)
   * @see #getDestClassesToCheckConflicts()
   */
  @NotNull
  protected final MultiMap<PyClass, PyMemberInfo<?>> getConflicts() {
    final MultiMap<PyClass, PyMemberInfo<?>> result = new MultiMap<PyClass, PyMemberInfo<?>>();
    for (final PyClass destinationClass : getDestClassesToCheckConflicts()) {
      for (final PyMemberInfo<PyElement> pyMemberInfo : myView.getSelectedMemberInfos()) {
        if (pyMemberInfo.hasConflict(destinationClass)) {
          result.putValue(destinationClass, pyMemberInfo);
        }
      }
    }
    return result;
  }

  /**
   * @see #getConflicts()
  * @return classes where this refactoring will move members. To be used to check for conflicts (if one of target classes already has members)
   */
  @NotNull
  protected abstract Iterable<? extends PyClass> getDestClassesToCheckConflicts();
}
