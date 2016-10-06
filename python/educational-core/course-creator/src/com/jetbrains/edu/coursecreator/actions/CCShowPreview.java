/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.util.LabeledEditor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FrameWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class CCShowPreview extends DumbAwareAction {
  public static final String SHOW_PREVIEW = "Show Preview";
  public static final String NO_PREVIEW_MESSAGE = "Preview is available for task files with answer placeholders only";

  public CCShowPreview() {
    super(SHOW_PREVIEW, SHOW_PREVIEW, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file != null && StudyUtils.getTaskFile(project, file.getVirtualFile()) != null) {
      presentation.setEnabledAndVisible(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (project == null || module == null) {
      return;
    }
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    TaskFile taskFile = StudyUtils.getTaskFile(project, virtualFile);
    if (taskFile == null) {
      return;
    }
    final PsiDirectory taskDir = file.getContainingDirectory();
    if (taskDir == null) {
      return;
    }
    PsiDirectory lessonDir = taskDir.getParentDirectory();
    if (lessonDir == null) {
      return;
    }


    if (taskFile.getAnswerPlaceholders().isEmpty()) {
      Messages.showInfoMessage("Preview is available for task files with answer placeholders only", "No Preview for This File");
      return;
    }

    VirtualFile generatedFilesFolder = CCUtils.getGeneratedFilesFolder(project, module);

    if (generatedFilesFolder == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Pair<VirtualFile, TaskFile> pair =
          EduUtils.createStudentFile(this, project, virtualFile, generatedFilesFolder, null);
        if (pair != null) {
          showPreviewDialog(project, pair.getFirst(), pair.getSecond());
        }
      }
    });
  }

  private static void showPreviewDialog(@NotNull Project project, @NotNull VirtualFile userFile, @NotNull TaskFile taskFile) {
    final FrameWrapper showPreviewFrame = new FrameWrapper(project);
    showPreviewFrame.setTitle(userFile.getName());
    LabeledEditor labeledEditor = new LabeledEditor(null);
    final EditorFactory factory = EditorFactory.getInstance();
    Document document = FileDocumentManager.getInstance().getDocument(userFile);
    if (document == null) {
      return;
    }
    final EditorEx createdEditor = (EditorEx)factory.createEditor(document, project, userFile, true);
    Disposer.register(project, new Disposable() {
      public void dispose() {
        factory.releaseEditor(createdEditor);
      }
    });
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      answerPlaceholder.setUseLength(true);
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(createdEditor, answerPlaceholder, JBColor.BLUE);
    }
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
    header.setBorder(new EmptyBorder(10, 10, 10, 10));
    header.add(new JLabel("Read-only preview."));
    String timeStamp = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(Calendar.getInstance().getTime());
    header.add(new JLabel(String.format("Created %s.", timeStamp)));
    JComponent editorComponent = createdEditor.getComponent();
    labeledEditor.setComponent(editorComponent, header);
    createdEditor.setCaretVisible(false);
    createdEditor.setCaretEnabled(false);
    showPreviewFrame.setComponent(labeledEditor);
    showPreviewFrame.setSize(new Dimension(500, 500));
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      showPreviewFrame.show();
    }
  }
}