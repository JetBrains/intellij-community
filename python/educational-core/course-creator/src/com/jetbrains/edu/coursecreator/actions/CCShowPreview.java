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
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.coursecreator.CCProjectService;
import com.jetbrains.edu.coursecreator.CCUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class CCShowPreview extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CCShowPreview.class.getName());

  public CCShowPreview() {
    super("Show Preview", "Show Preview", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!CCProjectService.setCCActionAvailable(e)) {
      return;
    }
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file != null && CCProjectService.getInstance(project).getTaskFile(file.getVirtualFile()) != null) {
      presentation.setEnabledAndVisible(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    //TODO: need to rewrite this action using new GENERATED_ROOT_FOLDER
    final Project project = e.getProject();
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    if (project == null || module == null) {
      return;
    }
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(e.getDataContext());
    if (file == null) {
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    Course course = service.getCourse();
    if (course == null) {
      return;
    }
    VirtualFile virtualFile = file.getVirtualFile();
    TaskFile taskFile = service.getTaskFile(virtualFile);
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
    final TaskFile taskFileCopy = new TaskFile();
    TaskFile.copy(taskFile, taskFileCopy);


    VirtualFile generatedFilesFolder = CCUtils.getGeneratedFilesFolder(project, module);

    if (generatedFilesFolder == null) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        EduUtils.createStudentFileFromAnswer(project, generatedFilesFolder, taskDir.getVirtualFile(), virtualFile.getName(), taskFileCopy);
      }
    });

    VirtualFile userFile = generatedFilesFolder.findChild(virtualFile.getName());
    if (userFile == null) {
      LOG.info("Generated file " + virtualFile.getName() + "was not found");
      return;
    }
    final FrameWrapper showPreviewFrame = new FrameWrapper(project);
    showPreviewFrame.setTitle(virtualFile.getName());
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
    for (AnswerPlaceholder answerPlaceholder : taskFileCopy.getAnswerPlaceholders()) {
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(createdEditor, answerPlaceholder, true, JBColor.BLUE);
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
    showPreviewFrame.show();
  }
}