package com.jetbrains.edu.learning.stepic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.StudySerializationUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;


public class StudyStepicFormatTest {

  @Test
  public void fromFirstVersion() throws IOException {
    doStepOptionsCreationTest("1.json");
  }

  @Test
  public void fromSecondVersion() throws IOException {
    doStepOptionsCreationTest("2.json");
  }

  @Test
  public void testWithSubtasks() throws IOException {
    StepicWrappers.StepOptions stepOptions = doStepOptionsCreationTest("3.json");
    assertEquals(1, stepOptions.lastSubtaskIndex);
  }


  private static StepicWrappers.StepOptions doStepOptionsCreationTest(String fileName) throws IOException {
    String responseString =
      FileUtil.loadFile(new File(getTestDataPath(), fileName));
    StepicWrappers.StepSource stepSource =
      EduStepicClient.deserializeStepicResponse(StepicWrappers.StepContainer.class, responseString).steps.get(0);
    StepicWrappers.StepOptions options = stepSource.block.options;
    List<TaskFile> files = options.files;
    assertTrue("Wrong number of task files", files.size() == 1);
    List<AnswerPlaceholder> placeholders = files.get(0).getAnswerPlaceholders();
    assertTrue("Wrong number of placeholders", placeholders.size() == 1);
    Map<Integer, AnswerPlaceholderSubtaskInfo> infos = placeholders.get(0).getSubtaskInfos();
    assertNotNull(infos);
    assertEquals(Collections.singletonList("Type your name here."), infos.get(0).getHints());
    assertEquals("Liana", infos.get(0).getPossibleAnswer());
    return options;
  }

  @Test
  public void testAvailableCourses() throws IOException {
    String responseString = FileUtil.loadFile(new File(getTestDataPath(), "courses.json"));
    StepicWrappers.CoursesContainer container =
      EduStepicClient.deserializeStepicResponse(StepicWrappers.CoursesContainer.class, responseString);
    assertNotNull(container.courses);
    assertTrue("Incorrect number of courses", container.courses.size() == 4);
  }

  @Test
  public void placeholderSerialization() throws IOException {
    final Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().
      registerTypeAdapter(AnswerPlaceholder.class, new StudySerializationUtils.Json.StepicAnswerPlaceholderAdapter()).create();
    AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder();
    answerPlaceholder.setOffset(1);
    answerPlaceholder.setLength(10);
    AnswerPlaceholderSubtaskInfo info1 = createSubtaskInfo("type here", "answer1", ContainerUtil.list("hint 1", "hint 2"));
    AnswerPlaceholderSubtaskInfo info2 = createSubtaskInfo("type here1", "answer2", ContainerUtil.list("hint 11", "hint 22"));
    answerPlaceholder.setSubtaskInfos(ContainerUtil.newHashMap(ContainerUtil.list(0, 1), ContainerUtil.list(info1, info2)));
    final String placeholderSerialization = gson.toJson(answerPlaceholder);
    String expected = FileUtil.loadFile(new File(getTestDataPath(), "placeholder.json"));
    JsonObject object = new JsonParser().parse(expected).getAsJsonObject();
    StudySerializationUtils.Json.removeIndexFromSubtaskInfos(object);
    assertEquals(gson.toJson(gson.fromJson(object, AnswerPlaceholder.class)), placeholderSerialization);

  }

  private static AnswerPlaceholderSubtaskInfo createSubtaskInfo(String placeholderText, String possibleAnswer, List<String> hints) {
    AnswerPlaceholderSubtaskInfo info = new AnswerPlaceholderSubtaskInfo();
    info.setPlaceholderText(placeholderText);
    info.setPossibleAnswer(possibleAnswer);
    info.setHints(hints);
    info.setNeedInsertText(true);
    return info;
  }

  @NotNull
  private static String getTestDataPath() {
    return FileUtil.join("testData/stepic");
  }
}
