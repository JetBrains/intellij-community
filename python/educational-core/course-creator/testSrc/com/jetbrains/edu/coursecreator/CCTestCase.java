package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CCTestCase extends LightPlatformCodeInsightFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(CCTestCase.class);

  @Override
  protected String getTestDataPath() {
    //TODO: rewrite to work for plugin
    return new File(PathManager.getHomePath(), "community/python/educational-core/course-creator/testData").getPath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Course course = new Course();
    course.setName("test course");
    StudyTaskManager.getInstance(getProject()).setCourse(course);

    Lesson lesson = new Lesson();
    lesson.setName("lesson1");
    Task task = new Task();
    task.setName("task1");
    task.setIndex(1);
    lesson.addTask(task);
    lesson.setIndex(1);
    course.getLessons().add(lesson);
    course.setCourseMode(CCUtils.COURSE_MODE);
    course.initCourse(false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile lesson1 = myFixture.getProject().getBaseDir().createChildDirectory(this, "lesson1");
          lesson1.createChildDirectory(this, "task1");
        }
        catch (IOException e) {
          //ignore
        }
      }
    });

  }

  protected VirtualFile configureByTaskFile(String name) {
    VirtualFile file =
      myFixture.copyFileToProject(name, FileUtil.join(getProject().getBasePath(), "lesson1", "task1", name));
    myFixture.configureFromExistingVirtualFile(file);

    Document document = FileDocumentManager.getInstance().getDocument(file);
    Task task = StudyTaskManager.getInstance(getProject()).getCourse().getLessons().get(0).getTaskList().get(0);
    TaskFile taskFile = new TaskFile();
    taskFile.setTask(task);
    task.getTaskFiles().put(name, taskFile);
    for (AnswerPlaceholder placeholder : getPlaceholders(document, false)) {
      taskFile.addAnswerPlaceholder(placeholder);
    }
    taskFile.sortAnswerPlaceholders();
    return file;
  }

  private static List<AnswerPlaceholder> getPlaceholders(Document document, boolean useLength) {
    final List<AnswerPlaceholder> placeholders = new ArrayList<>();
    new WriteCommandAction(null) {
      @Override
      protected void run(@NotNull Result result) {
        final String openingTagRx = "<placeholder( taskText=\"(.+?)\")?( possibleAnswer=\"(.+?)\")?>";
        final String closingTagRx = "</placeholder>";
        CharSequence text = document.getCharsSequence();
        final Matcher openingMatcher = Pattern.compile(openingTagRx).matcher(text);
        final Matcher closingMatcher = Pattern.compile(closingTagRx).matcher(text);
        int pos = 0;
        while (openingMatcher.find(pos)) {
          AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder();
          answerPlaceholder.setUseLength(useLength);
          String taskText = openingMatcher.group(2);
          if (taskText != null) {
            answerPlaceholder.setTaskText(taskText);
            answerPlaceholder.setLength(taskText.length());
          }
          String possibleAnswer = openingMatcher.group(4);
          if (possibleAnswer != null) {
            answerPlaceholder.setPossibleAnswer(possibleAnswer);
          }
          answerPlaceholder.setOffset(openingMatcher.start());
          if (!closingMatcher.find(openingMatcher.end())) {
            LOG.error("No matching closing tag found");
          }
          if (useLength) {
            answerPlaceholder.setLength(closingMatcher.start() - openingMatcher.end());
          } else {
            if (possibleAnswer == null) {
              answerPlaceholder.setPossibleAnswer(document.getText(TextRange.create(openingMatcher.end(), closingMatcher.start())));
            }
          }
          document.deleteString(closingMatcher.start(), closingMatcher.end());
          document.deleteString(openingMatcher.start(), openingMatcher.end());
          placeholders.add(answerPlaceholder);
          pos = answerPlaceholder.getOffset() + answerPlaceholder.getRealLength();
        }
      }
    }.execute();
    return placeholders;
  }

  public Pair<Document, List<AnswerPlaceholder>> getPlaceholders(String name) {
    VirtualFile resultFile = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + "/" + name);
    Document document = FileDocumentManager.getInstance().getDocument(resultFile);
    Document tempDocument = EditorFactory.getInstance().createDocument(document.getCharsSequence());
    List<AnswerPlaceholder> placeholders = getPlaceholders(tempDocument, true);
    return Pair.create(tempDocument, placeholders);
  }
}


