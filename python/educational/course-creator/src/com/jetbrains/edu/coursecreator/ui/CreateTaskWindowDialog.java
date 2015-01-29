package com.jetbrains.edu.coursecreator.ui;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.format.AnswerPlaceholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;

public class CreateTaskWindowDialog extends DialogWrapper {

  private static final String ourTitle = "Add Answer Placeholder";
  private static final Logger LOG = Logger.getInstance(CreateTaskWindowDialog.class.getName());
  private final AnswerPlaceholder myAnswerPlaceholder;
  private final CreateTaskWindowPanel myPanel;
  private final Project myProject;

  public Project getProject() {
    return myProject;
  }

  public CreateTaskWindowDialog(@NotNull final Project project, @NotNull final AnswerPlaceholder answerPlaceholder, int lessonIndex,
                                int taskIndex, String taskFileName, int taskWindowIndex) {
    super(project, true);
    setTitle(ourTitle);
    myAnswerPlaceholder = answerPlaceholder;
    myPanel = new CreateTaskWindowPanel(this);
    String generatedHintName = "lesson" + lessonIndex + "task" + taskIndex + taskFileName + "_" + taskWindowIndex;
    myPanel.setGeneratedHintName(generatedHintName);
    if (answerPlaceholder.getHintName() != null) {
      setHintText(project, answerPlaceholder);
    }
    myProject = project;
    String taskWindowTaskText = answerPlaceholder.getTaskText();
    myPanel.setTaskWindowText(taskWindowTaskText != null ? taskWindowTaskText : "");
    String hintName = answerPlaceholder.getHintName();
    myPanel.setHintName(hintName != null ? hintName : "");
    init();
    initValidation();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void setHintText(Project project, AnswerPlaceholder answerPlaceholder) {
    VirtualFile hints = project.getBaseDir().findChild("hints");
    if (hints != null) {
      File file = new File(hints.getPath(), answerPlaceholder.getHintName());
      StringBuilder hintText = new StringBuilder();
      if (file.exists()) {
        BufferedReader bufferedReader =  null;
        try {
          bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
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
        finally {
          if (bufferedReader != null) {
            try {
              bufferedReader.close();
            }
            catch (IOException e) {
              //close silently
            }
          }
        }
      }
    }
  }

  @Override
  protected void doOKAction() {
    String taskWindowText = myPanel.getTaskWindowText();
    myAnswerPlaceholder.setTaskText(StringUtil.notNullize(taskWindowText));
    if (myPanel.createHint()) {
      String hintName = myPanel.getHintName();
      myAnswerPlaceholder.setHint(hintName);
      String hintText = myPanel.getHintText();
      createHint(hintName, hintText);
    } else {
      if (myAnswerPlaceholder.getHintName() != null) {
        deleteHint();
      }
    }
    super.doOKAction();
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private void createHint(String hintName, String hintText) {
    VirtualFile hintsDir = myProject.getBaseDir().findChild("hints");
    if (hintsDir != null) {
      File hintFile = new File(hintsDir.getPath(), hintName);
      OutputStreamWriter outputStreamWriter = null;
      try {
        outputStreamWriter = new OutputStreamWriter(new FileOutputStream(hintFile), "UTF-8");
       outputStreamWriter.write(hintText);
      }
      catch (FileNotFoundException e) {
        //TODO:show error in UI
        return;
      }
      catch (UnsupportedEncodingException e) {
        LOG.error(e);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        if (outputStreamWriter != null) {
          try {
            outputStreamWriter.close();
          }
          catch (IOException e) {
            //close silently
          }
        }
      }
    }
    VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
    ProjectView.getInstance(myProject).refresh();
  }

  private void deleteHint() {
    VirtualFile hintsDir = myProject.getBaseDir().findChild("hints");
    if (hintsDir != null) {
      String hintName = myAnswerPlaceholder.getHintName();
      if (hintName == null) {
        return;
      }
      File hintFile = new File(hintsDir.getPath(), hintName);
      if (hintFile.exists()) {
        CCProjectService.deleteProjectFile(hintFile, myProject);
        myAnswerPlaceholder.setHint(null);
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
    return myAnswerPlaceholder.getHintName() != null ? null : new ValidationInfo("Hint file with such filename already exists");
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
