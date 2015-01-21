package com.jetbrains.edu.learning.stepic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.HttpRequests;
import com.jetbrains.edu.learning.course.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class StudyStepicConnector {
  private static final String stepicApiUrl = "https://stepic.org/api/";
  private static final Logger LOG = Logger.getInstance(StudyStepicConnector.class.getName());
  public static CourseInfo ourTestCourseInfo;   // TODO: to be removed

  private StudyStepicConnector() {}

  public static List<CourseInfo> getCourses() {
    ourTestCourseInfo = new CourseInfo("name", "author", "description");
    return Collections.singletonList(ourTestCourseInfo);   // TODO: uncomment proper implementation
    /*try {
      return HttpRequests.request(stepicApiUrl + "courses").connect(new HttpRequests.RequestProcessor<List<CourseInfo>>() {

        @Override
        public List<CourseInfo> process(@NotNull HttpRequests.Request request) throws IOException {
          final BufferedReader reader = request.getReader();
          Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
          return gson.fromJson(reader, CoursesContainer.class).courses;
        }
      });
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
    }
    return null;*/
  }

  public static Course getCourse(@NotNull final CourseInfo info) {
    final Course course = new Course();
    course.author = info.getAuthor();
    course.description = info.getDescription();
    course.name = info.getName();
    course.lessons = new ArrayList<Lesson>();

    if (info.equals(ourTestCourseInfo)) {   // TODO: to be removed
      try {
        course.lessons.addAll(getLessons(0));
      }
      catch (IOException e) {
        LOG.error("IOException " + e.getMessage());
      }
      return course;
    }

    try {
      for (Integer section : info.sections) {
        course.lessons.addAll(getLessons(section));
      }
      return course;
    }
    catch (IOException e) {
      LOG.error("IOException " + e.getMessage());
    }
    return null;
  }

  public static List<Lesson> getLessons(int sectionId) throws IOException {
    final List<Lesson> lessons;
    if (sectionId == 0) {         // TODO: to be removed
       lessons =
        HttpRequests.request(stepicApiUrl + "lessons/9205").connect(new HttpRequests.RequestProcessor<List<Lesson>>() {

          @Override
          public List<Lesson> process(@NotNull HttpRequests.Request request) throws IOException {
            final BufferedReader reader = request.getReader();
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
            return gson.fromJson(reader, Section.class).lessons;
          }
        });
    }
    else {
      lessons =
        HttpRequests.request(stepicApiUrl + "sections/" + String.valueOf(sectionId))
          .connect(new HttpRequests.RequestProcessor<List<Lesson>>() {

            @Override
            public List<Lesson> process(@NotNull HttpRequests.Request request) throws IOException {
              final BufferedReader reader = request.getReader();
              Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
              return gson.fromJson(reader, Section.class).lessons;
            }
          });
    }
    for (Lesson lesson : lessons) {
      lesson.taskList = new ArrayList<Task>();
      for (Integer s : lesson.steps) {
        final Step step = getStep(s);
        final Task task = new Task();
        task.name = step.name;
        task.text = step.text;
        task.testsText = step.options.test;

        task.taskFiles = new HashMap<String, TaskFile>();      // TODO: it looks like we don't need taskFiles as map anymore
        if (step.options.files != null) {
          for (TaskFile taskFile : step.options.files) {
            task.taskFiles.put(taskFile.name, taskFile);
          }
        }
        lesson.taskList.add(task);
      }
    }
    return lessons;
  }

  public static Step getStep(Integer step) throws IOException {
    return HttpRequests.request(stepicApiUrl + "steps/" + String.valueOf(step)).connect(new HttpRequests.RequestProcessor<Step>() {

      @Override
      public Step process(@NotNull HttpRequests.Request request) throws IOException {
        final BufferedReader reader = request.getReader();
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        return gson.fromJson(reader, StepContainer.class).steps.get(0).block;
      }
    });

  }

  private static class Section {
    public List<Lesson> lessons;
  }

  private static class StepContainer {
    List<StepRaw> steps;
  }

  private static class Step {
    StepOptions options;
    String text;
    String name;
  }

  private static class StepOptions {
    String test;
    List<TaskFile> files;
  }

  private static class StepRaw {
    Step block;
  }

  private static class CoursesContainer {
    public List<CourseInfo> courses;
  }

}
