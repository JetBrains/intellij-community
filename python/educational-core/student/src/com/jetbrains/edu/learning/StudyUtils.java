package com.jetbrains.edu.learning;

import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.edu.EduAnswerPlaceholderDeleteHandler;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.*;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.checker.StudyExecutor;
import com.jetbrains.edu.learning.checker.StudyTestRunner;
import com.jetbrains.edu.learning.ui.StudyProgressToolWindowFactory;
import com.jetbrains.edu.learning.ui.StudyToolWindowFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.util.Collection;
import java.util.List;

public class StudyUtils {
  private StudyUtils() {
  }

  private static final Logger LOG = Logger.getInstance(StudyUtils.class.getName());

  public static void closeSilently(@Nullable final Closeable stream) {
    if (stream != null) {
      try {
        stream.close();
      }
      catch (IOException e) {
        // close silently
      }
    }
  }

  public static boolean isZip(String fileName) {
    return fileName.contains(".zip");
  }

  public static <T> T getFirst(@NotNull final Iterable<T> container) {
    return container.iterator().next();
  }

  public static boolean indexIsValid(int index, @NotNull final Collection collection) {
    int size = collection.size();
    return index >= 0 && index < size;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  public static String getFileText(@Nullable final String parentDir, @NotNull final String fileName, boolean wrapHTML,
                                   @NotNull final String encoding) {
    final File inputFile = parentDir != null ? new File(parentDir, fileName) : new File(fileName);
    if (!inputFile.exists()) return null;
    final StringBuilder taskText = new StringBuilder();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), encoding));
      String line;
      while ((line = reader.readLine()) != null) {
        taskText.append(line).append("\n");
        if (wrapHTML) {
          taskText.append("<br>");
        }
      }
      return wrapHTML ? UIUtil.toHtml(taskText.toString()) : taskText.toString();
    }
    catch (IOException e) {
      LOG.info("Failed to get file text from file " + fileName, e);
    }
    finally {
      closeSilently(reader);
    }
    return null;
  }

  public static void updateAction(@NotNull final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    final Project project = e.getProject();
    if (project != null) {
      final StudyEditor studyEditor = getSelectedStudyEditor(project);
      if (studyEditor != null) {
        presentation.setEnabledAndVisible(true);
      }
    }
  }

  public static void updateToolWindows(@NotNull final Project project) {
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    windowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW).getContentManager().removeAllContents(false);
    StudyToolWindowFactory factory = new StudyToolWindowFactory();
    factory.createToolWindowContent(project, windowManager.getToolWindow(StudyToolWindowFactory.STUDY_TOOL_WINDOW));

    windowManager.getToolWindow(StudyProgressToolWindowFactory.ID).getContentManager().removeAllContents(false);
    StudyProgressToolWindowFactory windowFactory = new StudyProgressToolWindowFactory();
    windowFactory.createToolWindowContent(project, windowManager.getToolWindow(StudyProgressToolWindowFactory.ID));
  }

  public static void deleteFile(@NotNull final VirtualFile file) {
    try {
      file.delete(StudyUtils.class);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static File copyResourceFile(@NotNull final String sourceName, @NotNull final String copyName, @NotNull final Project project,
                                      @NotNull final Task task)
    throws IOException {
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final Course course = taskManager.getCourse();
    int taskNum = task.getIndex();
    int lessonNum = task.getLesson().getIndex();
    assert course != null;
    final String pathToResource = FileUtil.join(course.getCourseDirectory(), EduNames.LESSON + lessonNum, EduNames.TASK + taskNum);
    final File resourceFile = new File(pathToResource, copyName);
    FileUtil.copy(new File(pathToResource, sourceName), resourceFile);
    return resourceFile;
  }

  @Nullable
  public static Sdk findSdk(@NotNull final Task task, @NotNull final Project project) {
    final Language language = task.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).findSdk(project);
  }

  @NotNull
  public static StudyTestRunner getTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    final Language language = task.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).getTestRunner(task, taskDir);
  }

  public static RunContentExecutor getExecutor(@NotNull final Project project, @NotNull final Task currentTask,
                                               @NotNull final ProcessHandler handler) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    return StudyExecutor.INSTANCE.forLanguage(language).getExecutor(project, handler);
  }

  public static void setCommandLineParameters(@NotNull final GeneralCommandLine cmd,
                                              @NotNull final Project project,
                                              @NotNull final String filePath,
                                              @NotNull final String sdkPath,
                                              @NotNull final Task currentTask) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    StudyExecutor.INSTANCE.forLanguage(language).setCommandLineParameters(cmd, project, filePath, sdkPath, currentTask);
  }

  public static void showNoSdkNotification(@NotNull final Task currentTask, @NotNull final Project project) {
    final Language language = currentTask.getLesson().getCourse().getLanguageById();
    StudyExecutor.INSTANCE.forLanguage(language).showNoSdkNotification(project);
  }


  /**
   * shows pop up in the center of "check task" button in study editor
   */
  public static void showCheckPopUp(@NotNull final Project project, @NotNull final Balloon balloon) {
    final StudyEditor studyEditor = getSelectedStudyEditor(project);
    assert studyEditor != null;

    balloon.show(computeLocation(studyEditor.getEditor()), Balloon.Position.above);
    Disposer.register(project, balloon);
  }

  public static RelativePoint computeLocation(Editor editor){

    final Rectangle visibleRect = editor.getComponent().getVisibleRect();
    Point point = new Point(visibleRect.x + visibleRect.width + 10,
                            visibleRect.y + 10);
    return new RelativePoint(editor.getComponent(), point);
  }


  /**
   * returns language manager which contains all the information about language specific file names
   */
  @Nullable
  public static StudyLanguageManager getLanguageManager(@NotNull final Course course) {
    Language language = course.getLanguageById();
    return language == null ? null : StudyLanguageManager.INSTANCE.forLanguage(language);
  }

  public static boolean isTestsFile(@NotNull Project project, @NotNull final String name) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }
    StudyLanguageManager manager = getLanguageManager(course);
    if (manager == null) {
      return false;
    }
    return manager.getTestFileName().equals(name);
  }

  @Nullable
  public static TaskFile getTaskFile(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return null;
    }
    VirtualFile taskDir = file.getParent();
    if (taskDir == null) {
      return null;
    }
    //need this because of multi-module generation
    if ("src".equals(taskDir.getName())) {
      taskDir = taskDir.getParent();
      if (taskDir == null) {
        return null;
      }
    }
    final String taskDirName = taskDir.getName();
    if (taskDirName.contains(EduNames.TASK)) {
      final VirtualFile lessonDir = taskDir.getParent();
      if (lessonDir != null) {
        int lessonIndex = EduUtils.getIndex(lessonDir.getName(), EduNames.LESSON);
        List<Lesson> lessons = course.getLessons();
        if (!indexIsValid(lessonIndex, lessons)) {
          return null;
        }
        final Lesson lesson = lessons.get(lessonIndex);
        int taskIndex = EduUtils.getIndex(taskDirName, EduNames.TASK);
        final List<Task> tasks = lesson.getTaskList();
        if (!indexIsValid(taskIndex, tasks)) {
          return null;
        }
        final Task task = tasks.get(taskIndex);
        return task.getFile(file.getName());
      }
    }
    return null;
  }


  public static void drawAllWindows(Editor editor, TaskFile taskFile) {
    editor.getMarkupModel().removeAllHighlighters();
    final Project project = editor.getProject();
    if (project == null) return;
    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      final JBColor color = taskManager.getColor(answerPlaceholder);
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, true, color);
    }
    final Document document = editor.getDocument();
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(document, new EduAnswerPlaceholderDeleteHandler(editor));
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile, true);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
  }

  @Nullable
  public static StudyEditor getSelectedStudyEditor(@NotNull final Project project) {
    try {
      final FileEditor fileEditor = FileEditorManagerEx.getInstanceEx(project).getSplitters().getCurrentWindow().
        getSelectedEditor().getSelectedEditorWithProvider().getFirst();
      if (fileEditor instanceof StudyEditor) {
        return (StudyEditor)fileEditor;
      }
    }
    catch (Exception e) {
      return null;
    }
    return null;
  }

  @Nullable
  public static Editor getSelectedEditor(@NotNull final Project project) {
    final StudyEditor studyEditor = getSelectedStudyEditor(project);
    if (studyEditor != null) {
      return studyEditor.getEditor();
    }
    return null;
  }

  public static void deleteGuardedBlocks(@NotNull final Document document) {
    if (document instanceof DocumentImpl) {
      final DocumentImpl documentImpl = (DocumentImpl)document;
      List<RangeMarker> blocks = documentImpl.getGuardedBlocks();
      for (final RangeMarker block : blocks) {
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
          document.removeGuardedBlock(block);
        }));
      }
    }
  }

  @Nullable
  public static Document getPatternDocument(@NotNull final TaskFile taskFile, String name) {
    Task task = taskFile.getTask();
    String lessonDir = EduNames.LESSON + String.valueOf(task.getLesson().getIndex());
    String taskDir = EduNames.TASK + String.valueOf(task.getIndex());
    Course course = task.getLesson().getCourse();
    File resourceFile = new File(course.getCourseDirectory());
    if (!resourceFile.exists()) {
      return  null;
    }
    String patternPath = FileUtil.join(resourceFile.getPath(), lessonDir, taskDir, name);
    VirtualFile patternFile = VfsUtil.findFileByIoFile(new File(patternPath), true);
    if (patternFile == null) {
      return null;
    }
    return FileDocumentManager.getInstance().getDocument(patternFile);
  }

  public static boolean isRenameableOrMoveable(@NotNull final Project project, @NotNull final Course course, @NotNull final PsiElement element) {
    if (element instanceof PsiFile) {
      VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      if (project.getBaseDir().equals(virtualFile.getParent())) {
        return false;
      }
      TaskFile file = getTaskFile(project, virtualFile);
      if (file != null) {
        return false;
      }
      String name = virtualFile.getName();
      return !isTestsFile(project, name) && !EduNames.TASK_HTML.equals(name);
    }
    if (element instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      VirtualFile parent = virtualFile.getParent();
      if (parent == null) {
        return true;
      }
      if (project.getBaseDir().equals(parent)) {
        return false;
      }
      Lesson lesson = course.getLesson(parent.getName());
      if (lesson != null) {
        Task task = lesson.getTask(virtualFile.getName());
        if (task != null) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean canRenameOrMove(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null || project == null) {
      return false;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }
    if (!isRenameableOrMoveable(project, course, element)) {
      return true;
    }
    return false;
  }

  @Nullable
  public static String getTaskTextFromTask(@Nullable final Task task, @Nullable final VirtualFile taskDirectory) {
    if (task == null) {
      return null;
    }
    String text = task.getText();
    if (text != null) {
      return text;
    }
    if (taskDirectory != null) {
      VirtualFile taskTextFile = taskDirectory.findChild(EduNames.TASK_HTML);
      if (taskTextFile == null) {
        VirtualFile srcDir = taskDirectory.findChild("src");
        if (srcDir != null) {
           taskTextFile = srcDir.findChild(EduNames.TASK_HTML);
        }
      }
      if (taskTextFile != null) {
        try {
          return FileUtil.loadTextAndClose(taskTextFile.getInputStream());
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    }
    return null;
  }
  
  @Nullable
  public static StudyToolWindowConfigurator getConfigurator(@NotNull final Project project) {
    StudyToolWindowConfigurator[] extensions = StudyToolWindowConfigurator.EP_NAME.getExtensions();
    for (StudyToolWindowConfigurator extension: extensions) {
      if (extension.accept(project)) {
        return extension;
      }
    }
    return null;
  }
}
