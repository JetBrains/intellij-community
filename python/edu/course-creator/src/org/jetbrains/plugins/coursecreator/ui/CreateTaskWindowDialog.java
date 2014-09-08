package org.jetbrains.plugins.coursecreator.ui;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import javax.swing.*;
import java.io.*;

public class CreateTaskWindowDialog extends DialogWrapper {

  public static final String TITLE = "New Task Window";
  private static final Logger LOG = Logger.getInstance(CreateTaskWindowDialog.class.getName());
  private final TaskWindow myTaskWindow;
  private final CreateTaskWindowPanel myPanel;
  private final Project myProject;

  public Project getProject() {
    return myProject;
  }

  public CreateTaskWindowDialog(@NotNull final Project project, @NotNull final TaskWindow taskWindow, int lessonIndex,
                                int taskIndex, String taskFileName, int taskWindowIndex) {
    super(project, true);
    setTitle(TITLE);
    myTaskWindow = taskWindow;
    myPanel = new CreateTaskWindowPanel(this);
    String generatedHintName = "lesson" + lessonIndex + "task" + taskIndex + taskFileName + "_" + taskWindowIndex;
    myPanel.setGeneratedHintName(generatedHintName);
    if (taskWindow.getHintName() != null) {
      setHintText(project, taskWindow);
    }
    myProject = project;
    String taskWindowTaskText = taskWindow.getTaskText();
    myPanel.setTaskWindowText(taskWindowTaskText != null ? taskWindowTaskText : "");
    String hintName = taskWindow.getHintName();
    myPanel.setHintName(hintName != null ? hintName : "");
    init();
    initValidation();
  }

  private void setHintText(Project project, TaskWindow taskWindow) {
    VirtualFile hints = project.getBaseDir().findChild("hints");
    if (hints != null) {
      File file = new File(hints.getPath(), taskWindow.getHintName());
      StringBuilder hintText = new StringBuilder();
      if (file.exists()) {
        try {
          BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
          String line;
          while ((line = bufferedReader.readLine()) != null) {
            hintText.append(line).append("\n");
          }
          myPanel.doClick();
          //myPanel.enableHint(true);
          myPanel.setHintText(hintText.toString());
        }
        catch (FileNotFoundException e) {
          LOG.error("created hint was not found", e);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  protected void doOKAction() {
    String taskWindowText = myPanel.getTaskWindowText();
    myTaskWindow.setTaskText(StringUtil.notNullize(taskWindowText));
    if (myPanel.createHint()) {
      String hintName = myPanel.getHintName();
      myTaskWindow.setHint(hintName);
      String hintText = myPanel.getHintText();
      createHint(hintName, hintText);
    }
    super.doOKAction();
  }

  private void createHint(String hintName, String hintText) {
    VirtualFile hintsDir = myProject.getBaseDir().findChild("hints");
    if (hintsDir != null) {
      File hintFile = new File(hintsDir.getPath(), hintName);
      PrintWriter printWriter = null;
      try {
        printWriter = new PrintWriter(hintFile);
        printWriter.print(hintText);
      }
      catch (FileNotFoundException e) {
        //TODO:show error in UI
        return;
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
      }
    }
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(myProject).refresh();
  }

  public void deleteHint() {
    VirtualFile hintsDir = myProject.getBaseDir().findChild("hints");
    if (hintsDir != null) {
      String hintName = myTaskWindow.getHintName();
      if (hintName == null) {
        return;
      }
      File hintFile = new File(hintsDir.getPath(), hintName);
      if (hintFile.exists()) {
        CCProjectService.deleteProjectFile(hintFile, myProject);
        myTaskWindow.setHint(null);
        myPanel.resetHint();
      }
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  public ValidationInfo doValidate() {
    String name = myPanel.getHintName();
    VirtualFile hintsDir = myProject.getBaseDir().findChild("hints");
    if (hintsDir == null) {
      return null;
    }
    VirtualFile child = hintsDir.findChild(name);
    if (child == null) {
      return null;
    }
    return myTaskWindow.getHintName() != null ? null : new ValidationInfo("Hint file with such filename already exists");
  }

  public void validateInput() {
    super.initValidation();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
