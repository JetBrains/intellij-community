/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.tasks.timeTracking;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskRepository;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: evgeny.zakrevsky
 * Date: 12/26/12
 */
public class SendTimeTrackingInformationDialog extends DialogWrapper {
  private final static Logger LOG = Logger.getInstance("#com.intellij.tasks.timeTracking.TasksToolWindowPanel");
  public static final Pattern PATTERN = Pattern.compile("([0-9]+)d ([0-9]+)h ([0-9]+)m");

  @Nullable private final Project myProject;
  private final LocalTask myTask;
  private JRadioButton myFromPreviousPostRadioButton;
  private JRadioButton myTotallyRadioButton;
  private JRadioButton myCustomRadioButton;
  private JTextField myFromPreviousPostTextField;
  private JTextField myTotallyTextField;
  private JTextField myCustomTextField;
  private JTextArea myCommentTextArea;
  private JPanel myPanel;
  private JLabel myTaskNameLabel;

  protected SendTimeTrackingInformationDialog(@Nullable final Project project, final LocalTask localTask) {
    super(project);
    myProject = project;
    myTask = localTask;
    setTitle("Time Tracking");

    myTaskNameLabel.setText(myTask.getPresentableName());
    myFromPreviousPostRadioButton.setSelected(true);
    if (myTask.getLastPost() == null) {
      myFromPreviousPostRadioButton.setVisible(false);
      myFromPreviousPostTextField.setVisible(false);
      myTotallyRadioButton.setSelected(true);
    }
    myFromPreviousPostTextField.setText(formatDuration(myTask.getTimeSpentFromLastPost()));
    myTotallyTextField.setText(formatDuration(myTask.getTotalTimeSpent()));

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  private static String formatDuration(final long milliseconds) {
    final int second = 1000;
    final int minute = 60 * second;
    final int hour = 60 * minute;
    final int day = 24 * hour;

    final int days = (int)(milliseconds / day);
    final int hours = (int)(milliseconds % day / hour);
    final int minutes = (int)(milliseconds % hour / minute);

    String daysString = days + "d ";
    String hoursString = hours + "h ";
    String minutesString = minutes + "m";

    return daysString + hoursString + minutesString;
  }

  @Override
  protected void doOKAction() {
    String timeSpentText = myFromPreviousPostRadioButton.isSelected() ? myFromPreviousPostTextField.getText()
                           : myTotallyRadioButton.isSelected() ? myTotallyTextField.getText() : myCustomTextField.getText();
    final Matcher matcher = PATTERN.matcher(timeSpentText);
    if (matcher.matches()) {
      final int timeSpent = Integer.valueOf(matcher.group(1)) * 24 * 60 + Integer.valueOf(matcher.group(2)) * 60 + Integer.valueOf(
        matcher.group(3));

      final TaskRepository repository = myTask.getRepository();
      if (repository != null &&
          repository.isSupported(TaskRepository.TIME_MANAGEMENT)) {
        try {
          repository.updateTimeSpent(myTask, timeSpentText, myCommentTextArea.getText());
          myTask.setLastPost(new Date());
        }
        catch (Exception e1) {
          Messages
            .showErrorDialog(myProject, "<html>Could not send information for " + myTask.getPresentableName() + "<br/>" + e1.getMessage(),
                             "Error");
          LOG.warn(e1);
        }
      }
    }


    super.doOKAction();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    String timeSpentText = myFromPreviousPostRadioButton.isSelected() ? myFromPreviousPostTextField.getText()
                                                                      : myTotallyRadioButton.isSelected() ? myTotallyTextField.getText() : myCustomTextField.getText();
    if (!PATTERN.matcher(timeSpentText).matches()) return new ValidationInfo("Time Spent has broken format");
    return null;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return "com.intellij.tasks.timeTracking.TasksToolWindowPanel";
  }
}
