package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
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
    List<CourseInfo> filtered = ContainerUtil.filter(container.courses, info -> EduStepicConnector.canBeOpened(info));
    assertEquals(ContainerUtil.newArrayList("Adaptive Python", "Introduction to Python", "format2"), ContainerUtil.map(filtered, CourseInfo::getName));
  }

  @NotNull
  private static String getTestDataPath() {
    return FileUtil.join(PlatformTestUtil.getCommunityPath(), "python/educational-core/student/testData/stepic");
  }
}
