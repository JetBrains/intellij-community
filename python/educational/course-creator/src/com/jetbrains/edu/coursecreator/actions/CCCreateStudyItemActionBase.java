package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.util.Function;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.StudyItem;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.coursecreator.ui.CCCreateStudyItemDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class CCCreateStudyItemActionBase extends DumbAwareAction {

  public CCCreateStudyItemActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }


  @Override
  public void actionPerformed(AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
    if (directory == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null) {
      return;
    }
    createItem(view, project, directory, course);
  }


  @Override
  public void update(@NotNull AnActionEvent event) {
    if (!CCProjectService.setCCActionAvailable(event)) {
      return;
    }
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (project == null || view == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    final PsiDirectory[] directories = view.getDirectories();
    if (directories.length == 0) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    final PsiDirectory sourceDirectory = DirectoryChooserUtil.getOrChooseDirectory(view);
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null || sourceDirectory == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    if (!isAddedAsLast(sourceDirectory, project, course) &&
        getThresholdItem(course, sourceDirectory) == null) {
      presentation.setEnabledAndVisible(false);
    }
    if (CommonDataKeys.PSI_FILE.getData(event.getDataContext()) != null) {
      presentation.setEnabledAndVisible(false);
    }
  }


  @Nullable
  protected abstract PsiDirectory getParentDir(@NotNull final Project project,
                                          @NotNull final Course course,
                                          @NotNull final PsiDirectory directory);


  @Nullable
  public PsiDirectory createItem(@Nullable final IdeView view, @NotNull final Project project,
                                             @NotNull final PsiDirectory sourceDirectory, @NotNull final Course course) {
    StudyItem parentItem = getParentItem(course, sourceDirectory);
    final StudyItem item = getItem(sourceDirectory, project, course, view, parentItem);
    if (item == null) {
      return null;
    }
    final PsiDirectory parentDir = getParentDir(project, course, sourceDirectory);
    if (parentDir == null) {
      return null;
    }
    CCUtils.updateHigherElements(parentDir.getVirtualFile().getChildren(), getStudyOrderable(item),
                                 item.getIndex() - 1, getItemName(), 1);
    addItem(course, item);
    Collections.sort(getSiblings(course, parentItem), EduUtils.INDEX_COMPARATOR);
    return createItemDir(project, item, view, parentDir, course);
  }

  protected abstract void addItem(@NotNull final Course course, @NotNull final StudyItem item);


  protected abstract Function<VirtualFile, ? extends StudyItem> getStudyOrderable(@NotNull final StudyItem item);

  protected abstract PsiDirectory createItemDir(@NotNull final Project project, @NotNull final StudyItem item,
                                                @Nullable final IdeView view, @NotNull final PsiDirectory parentDirectory,
                                                @NotNull final Course course);

  @Nullable
  protected StudyItem getItem(@NotNull final PsiDirectory sourceDirectory,
                              @NotNull final Project project,
                              @NotNull final Course course,
                              @Nullable IdeView view,
                              @Nullable StudyItem parentItem) {

    String itemName;
    int itemIndex;
    if (isAddedAsLast(sourceDirectory, project, course)) {
      itemIndex = getSiblingsSize(course, parentItem) + 1;
      String suggestedName = getItemName() + itemIndex;
      itemName = view == null ? suggestedName : Messages.showInputDialog("Name:", getTitle(),
                                                                         null, suggestedName, null);
    }
    else {
      StudyItem thresholdItem = getThresholdItem(course, sourceDirectory);
      if (thresholdItem == null) {
        return null;
      }
      final int index = thresholdItem.getIndex();
      CCCreateStudyItemDialog dialog = new CCCreateStudyItemDialog(project, getItemName(), thresholdItem.getName(), index);
      dialog.show();
      if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
        return null;
      }
      itemName = dialog.getName();
      itemIndex = index + dialog.getIndexDelta();
    }
    if (itemName == null) {
      return null;
    }
    return createAndInitItem(course, parentItem, itemName, itemIndex);
  }

  protected abstract int getSiblingsSize(@NotNull final Course course, @Nullable final StudyItem parentItem);

  @NotNull
  protected String getTitle() {
    return "Create New " + StringUtil.toTitleCase(getItemName());
  }

  @Nullable
  protected abstract StudyItem getParentItem(@NotNull final Course course, @NotNull final PsiDirectory directory);

  @Nullable
  protected abstract StudyItem getThresholdItem(@NotNull final Course course, @NotNull final PsiDirectory sourceDirectory);

  protected abstract boolean isAddedAsLast(@NotNull final PsiDirectory sourceDirectory,
                                           @NotNull final Project project,
                                           @NotNull final Course course);

  protected abstract List<? extends StudyItem> getSiblings(@NotNull final Course course, @Nullable final StudyItem parentItem);

  protected abstract String getItemName();

  protected abstract StudyItem createAndInitItem(@NotNull final Course course,
                                                 @Nullable final StudyItem parentItem,
                                                 String name,
                                                 int index);
}
