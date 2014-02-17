package com.jetbrains.python.refactoring.classes.membersManager.vp;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.refactoring.classes.membersManager.PyMemberInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * View to display dialog with members.
 * First, configure it with {@link #configure(MembersViewInitializationInfo)}.
 * Then, display with {@link #initAndShow()}
 *
 * @param <C> initialization info for this view. See {@link com.jetbrains.python.refactoring.classes.membersManager.vp.MembersViewInitializationInfo}
 *            for more info
 * @author Ilya.Kazakevich
 */
public interface MembersBasedView<C extends MembersViewInitializationInfo> {
  /**
   * Display conflict dialogs.
   *
   * @param conflicts conflicts.
   * @return true if user's choice is "continue". False if "cancel"
   */
  boolean showConflictsDialog(@NotNull MultiMap<PsiElement, String> conflicts);

  /**
   * Displays error message
   *
   * @param message message to display
   */
  void showError(@NotNull String message);

  /**
   * Configures view and <strong>must</strong> be called once, before {@link #initAndShow()}
   * It accepts configuration info class
   * Children may rewrite method to do additional configuration, but they should <strong>always</strong> call "super" first!
   *
   * @param configInfo configuration info
   */
  void configure(@NotNull C configInfo);

  /**
   * @return collection of member infos user selected
   */
  @NotNull
  Collection<PyMemberInfo<PyElement>> getSelectedMemberInfos();

  /**
   * Runs refactoring based on {@link com.intellij.refactoring.BaseRefactoringProcessor}.
   * It may display "preview" first.
   *
   * @param processor refactoring processor
   */
  void invokeRefactoring(@NotNull BaseRefactoringProcessor processor);

  /**
   * Displays dialog. Be sure to run {@link #configure(MembersViewInitializationInfo)} first
   */
  void initAndShow();

  /**
   * Closes dialog
   */
  void close();
}
