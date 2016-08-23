package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.ComparisonFailure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CCTestCase extends CodeInsightFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(CCTestCase.class);

  @Nullable
  public static RangeHighlighter getHighlighter(MarkupModel model, AnswerPlaceholder placeholder) {
    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      int endOffset = placeholder.getOffset() + placeholder.getRealLength();
      if (highlighter.getStartOffset() == placeholder.getOffset() && highlighter.getEndOffset() == endOffset) {
        return highlighter;
      }
    }
    return null;
  }

  protected static void checkHighlighters(TaskFile taskFile, MarkupModel markupModel) {
    for (AnswerPlaceholder answerPlaceholder : taskFile.getAnswerPlaceholders()) {
      if (getHighlighter(markupModel, answerPlaceholder) == null) {
        throw new AssertionError("No highlighter for placeholder: " + CCTestsUtil.getPlaceholderPresentation(answerPlaceholder));
      }
    }
  }

  public void checkByFile(TaskFile taskFile, String fileName, boolean useLength) {
    Pair<Document, List<AnswerPlaceholder>> placeholders = getPlaceholders(fileName, useLength, true);
    String message = "Placeholders don't match";
    if (taskFile.getAnswerPlaceholders().size() != placeholders.second.size()) {
      throw new ComparisonFailure(message,
                                  CCTestsUtil.getPlaceholdersPresentation(taskFile.getAnswerPlaceholders()),
                                  CCTestsUtil.getPlaceholdersPresentation(placeholders.second));
    }
    for (AnswerPlaceholder answerPlaceholder : placeholders.getSecond()) {
      AnswerPlaceholder placeholder = taskFile.getAnswerPlaceholder(answerPlaceholder.getOffset());
      if (!CCTestsUtil.comparePlaceholders(placeholder, answerPlaceholder)) {
        throw new ComparisonFailure(message,
                                    CCTestsUtil.getPlaceholdersPresentation(taskFile.getAnswerPlaceholders()),
                                    CCTestsUtil.getPlaceholdersPresentation(placeholders.second));
      }
    }
  }

  @Override
  protected String getBasePath() {
    return "/community/python/educational-core/course-creator/testData";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Course course = new Course();
    course.setName("test course");
    course.setCourseDirectory(getProject().getBasePath());
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

  protected VirtualFile copyFileToTask(String name) {
    return myFixture.copyFileToProject(name, FileUtil.join(getProject().getBasePath(), "lesson1", "task1", name));
  }

  protected VirtualFile configureByTaskFile(String name) {
    Task task = StudyTaskManager.getInstance(getProject()).getCourse().getLessons().get(0).getTaskList().get(0);
    TaskFile taskFile = new TaskFile();
    taskFile.setTask(task);
    task.getTaskFiles().put(name, taskFile);
    VirtualFile file = copyFileToTask(name);
    myFixture.configureFromExistingVirtualFile(file);
    Document document = FileDocumentManager.getInstance().getDocument(file);
    for (AnswerPlaceholder placeholder : getPlaceholders(document, false)) {
      taskFile.addAnswerPlaceholder(placeholder);
    }
    taskFile.sortAnswerPlaceholders();
    StudyUtils.drawAllWindows(myFixture.getEditor(), taskFile);
    CCUtils.createResourceFile(file, StudyTaskManager.getInstance(getProject()).getCourse(), file.getParent());
    return file;
  }

  private static List<AnswerPlaceholder> getPlaceholders(Document document, boolean useLength) {
    final List<AnswerPlaceholder> placeholders = new ArrayList<>();
    new WriteCommandAction(null) {
      @Override
      protected void run(@NotNull Result result) {
        final String openingTagRx = "<placeholder( taskText=\"(.+?)\")?( possibleAnswer=\"(.+?)\")?( hint=\"(.+?)\")?>";
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
          String hint = openingMatcher.group(6);
          if (hint != null) {
            answerPlaceholder.setHint(hint);
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
    return getPlaceholders(name, true, false);
  }

  public Pair<Document, List<AnswerPlaceholder>> getPlaceholders(String name, boolean useLength, boolean removeMarkers) {
    VirtualFile resultFile = LocalFileSystem.getInstance().findFileByPath(getTestDataPath() + "/" + name);
    Document document = FileDocumentManager.getInstance().getDocument(resultFile);
    Document tempDocument = EditorFactory.getInstance().createDocument(document.getCharsSequence());
    if (removeMarkers) {
      EditorTestUtil.extractCaretAndSelectionMarkers(tempDocument);
    }
    List<AnswerPlaceholder> placeholders = getPlaceholders(tempDocument, useLength);
    return Pair.create(tempDocument, placeholders);
  }
}


