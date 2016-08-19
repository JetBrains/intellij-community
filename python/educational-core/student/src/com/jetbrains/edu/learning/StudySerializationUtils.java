package com.jetbrains.edu.learning;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StudySerializationUtils {

  public static final String PLACEHOLDERS = "placeholders";
  public static final String LINE = "line";
  public static final String START = "start";
  public static final String LENGTH = "length";
  public static final String POSSIBLE_ANSWER = "possible_answer";
  public static final String HINT = "hint";
  public static final String ADDITIONAL_HINTS = "additional_hints";
  public static final String OFFSET = "offset";
  public static final String TEXT = "text";
  public static final String LESSONS = "lessons";
  public static final String COURSE = "course";
  public static final String COURSE_TITLED = "Course";
  public static final String STATUS = "status";
  public static final String AUTHOR = "author";
  public static final String AUTHORS = "authors";
  public static final String MY_INITIAL_START = "myInitialStart";

  private StudySerializationUtils() {
  }

  public static class StudyUnrecognizedFormatException extends Exception {
  }

  public static class Xml {
    public final static String COURSE_ELEMENT = "courseElement";
    public final static String MAIN_ELEMENT = "StudyTaskManager";
    public static final String MAP = "map";
    public static final String KEY = "key";
    public static final String VALUE = "value";
    public static final String NAME = "name";
    public static final String LIST = "list";
    public static final String OPTION = "option";
    public static final String INDEX = "index";
    public static final String STUDY_STATUS_MAP = "myStudyStatusMap";
    public static final String TASK_STATUS_MAP = "myTaskStatusMap";
    public static final String LENGTH = "length";
    public static final String ANSWER_PLACEHOLDERS = "answerPlaceholders";
    public static final String TASK_LIST = "taskList";
    public static final String TASK_FILES = "taskFiles";
    public static final String INITIAL_STATE = "initialState";
    public static final String MY_INITIAL_STATE = "MyInitialState";
    public static final String MY_LINE = "myLine";
    public static final String MY_START = "myStart";
    public static final String MY_LENGTH = "myLength";
    public static final String HINTS = "hints";
    public static final String HINT = "hint";
    public static final String AUTHOR_TITLED = "Author";
    public static final String FIRST_NAME = "first_name";
    public static final String SECOND_NAME = "second_name";
    public static final String MY_INITIAL_LINE = "myInitialLine";
    public static final String MY_INITIAL_LENGTH = "myInitialLength";
    public static final String ANSWER_PLACEHOLDER = "AnswerPlaceholder";
    public static final String TASK_WINDOWS = "taskWindows";
    public static final String RESOURCE_PATH = "resourcePath";
    public static final String COURSE_DIRECTORY = "courseDirectory";

    private Xml() {
    }

    public static int getVersion(Element element) throws StudyUnrecognizedFormatException {
      if (element.getChild(COURSE_ELEMENT) != null) {
        return 1;
      }

      final Element taskManager = element.getChild(MAIN_ELEMENT);

      Element versionElement = getChildWithName(taskManager, "VERSION");
      if (versionElement == null) {
        return -1;
      }

      return Integer.valueOf(versionElement.getAttributeValue(VALUE));
    }

    public static Element convertToSecondVersion(Element element) throws StudyUnrecognizedFormatException {
      final Element oldCourseElement = element.getChild(COURSE_ELEMENT);
      Element state = new Element(MAIN_ELEMENT);

      Element course = addChildWithName(state, COURSE, oldCourseElement.clone());
      course.setName(COURSE_TITLED);

      Element author = getChildWithName(course, AUTHOR);
      String authorString = author.getAttributeValue(VALUE);
      course.removeContent(author);

      String[] names = authorString.split(" ", 2);
      Element authorElement = new Element(AUTHOR_TITLED);
      addChildWithName(authorElement, FIRST_NAME, names[0]);
      addChildWithName(authorElement, SECOND_NAME, names.length == 1 ? "" : names[1]);

      addChildList(course, AUTHORS, Collections.singletonList(authorElement));

      Element courseDirectoryElement = getChildWithName(course, RESOURCE_PATH);
      renameElement(courseDirectoryElement, COURSE_DIRECTORY);

      for (Element lesson : getChildList(course, LESSONS)) {
        incrementIndex(lesson);
        for (Element task : getChildList(lesson, TASK_LIST)) {
          incrementIndex(task);
          Map<String, Element> taskFiles = getChildMap(task, TASK_FILES);
          for (Element taskFile : taskFiles.values()) {
            renameElement(getChildWithName(taskFile, TASK_WINDOWS), ANSWER_PLACEHOLDERS);
            for (Element placeholder : getChildList(taskFile, ANSWER_PLACEHOLDERS)) {
              placeholder.setName(ANSWER_PLACEHOLDER);

              Element initialState = new Element(MY_INITIAL_STATE);
              addChildWithName(placeholder, INITIAL_STATE, initialState);
              addChildWithName(initialState, MY_LINE, getChildWithName(placeholder, MY_INITIAL_LINE).getAttributeValue(VALUE));
              addChildWithName(initialState, MY_START, getChildWithName(placeholder, MY_INITIAL_START).getAttributeValue(VALUE));
              addChildWithName(initialState, MY_LENGTH, getChildWithName(placeholder, MY_INITIAL_LENGTH).getAttributeValue(VALUE));
            }
          }
        }
      }
      element.removeContent();
      element.addContent(state);
      return element;
    }

    public static Map<String, String> fillStatusMap(Element taskManagerElement, String mapName, XMLOutputter outputter)
      throws StudyUnrecognizedFormatException {
      Map<Element, String> sourceMap = getChildMap(taskManagerElement, mapName);
      Map<String, String> destMap = new HashMap<>();
      for (Map.Entry<Element, String> entry : sourceMap.entrySet()) {
        String status = entry.getValue();
        if (status.equals(StudyStatus.Unchecked.toString())) {
          continue;
        }
        destMap.put(outputter.outputString(entry.getKey()), status);
      }
      return destMap;
    }

    public static Element convertToThirdVersion(Element state, Project project) throws StudyUnrecognizedFormatException {
      Element taskManagerElement = state.getChild(MAIN_ELEMENT);
      XMLOutputter outputter = new XMLOutputter();

      Map<String, String> placeholderTextToStatus = fillStatusMap(taskManagerElement, STUDY_STATUS_MAP, outputter);
      Map<String, String> taskFileToStatusMap = fillStatusMap(taskManagerElement, TASK_STATUS_MAP, outputter);

      Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
      for (Element lesson : getChildList(courseElement, LESSONS)) {
        int lessonIndex = getAsInt(lesson, INDEX);
        for (Element task : getChildList(lesson, TASK_LIST)) {
          String taskStatus = null;
          int taskIndex = getAsInt(task, INDEX);
          Map<String, Element> taskFiles = getChildMap(task, TASK_FILES);
          for (Map.Entry<String, Element> entry : taskFiles.entrySet()) {
            Element taskFileElement = entry.getValue();
            String taskFileText = outputter.outputString(taskFileElement);
            String taskFileStatus = taskFileToStatusMap.get(taskFileText);
            if (taskFileStatus != null && (taskStatus == null || taskFileStatus.equals(StudyStatus.Failed.toString()))) {
              taskStatus = taskFileStatus;
            }
            Document document = StudyUtils.getDocument(project.getBasePath(), lessonIndex, taskIndex, entry.getKey());
            if (document == null) {
              continue;
            }
            for (Element placeholder : getChildList(taskFileElement, ANSWER_PLACEHOLDERS)) {
              taskStatus = addStatus(outputter, placeholderTextToStatus, taskStatus, placeholder);
              addOffset(document, placeholder);
              addInitialState(document, placeholder);
            }
          }
          if (taskStatus != null) {
            addChildWithName(task, STATUS, taskStatus);
          }
        }
      }
      return state;
    }

    public static String addStatus(XMLOutputter outputter,
                                   Map<String, String> placeholderTextToStatus,
                                   String taskStatus,
                                   Element placeholder) {
      String placeholderText = outputter.outputString(placeholder);
      String status = placeholderTextToStatus.get(placeholderText);
      if (status != null) {
        addChildWithName(placeholder, STATUS, status);
        if (taskStatus == null || status.equals(StudyStatus.Failed.toString())) {
          taskStatus = status;
        }
      }
      return taskStatus;
    }

    public static void addInitialState(Document document, Element placeholder) throws StudyUnrecognizedFormatException {
      Element initialState = getChildWithName(placeholder, INITIAL_STATE).getChild(MY_INITIAL_STATE);
      int initialLine = getAsInt(initialState, MY_LINE);
      int initialStart = getAsInt(initialState, MY_START);
      int initialOffset = document.getLineStartOffset(initialLine) + initialStart;
      addChildWithName(initialState, OFFSET, initialOffset);
      renameElement(getChildWithName(initialState, MY_LENGTH), LENGTH);
    }

    public static void addOffset(Document document, Element placeholder) throws StudyUnrecognizedFormatException {
      int line = getAsInt(placeholder, LINE);
      int start = getAsInt(placeholder, START);
      int offset = document.getLineStartOffset(line) + start;
      addChildWithName(placeholder, OFFSET, offset);
    }

    public static int getAsInt(Element element, String name) throws StudyUnrecognizedFormatException {
      return Integer.valueOf(getChildWithName(element, name).getAttributeValue(VALUE));
    }

    public static void incrementIndex(Element element) throws StudyUnrecognizedFormatException {
      Element index = getChildWithName(element, INDEX);
      int indexValue = Integer.parseInt(index.getAttributeValue(VALUE));
      changeValue(index, indexValue + 1);
    }

    public static void renameElement(Element element, String newName) {
      element.setAttribute(NAME, newName);
    }

    public static void changeValue(Element element, Object newValue) {
      element.setAttribute(VALUE, newValue.toString());
    }

    public static Element addChildWithName(Element parent, String name, Element value) {
      Element child = new Element(OPTION);
      child.setAttribute(NAME, name);
      child.addContent(value);
      parent.addContent(child);
      return value;
    }

    public static Element addChildWithName(Element parent, String name, Object value) {
      Element child = new Element(OPTION);
      child.setAttribute(NAME, name);
      child.setAttribute(VALUE, value.toString());
      parent.addContent(child);
      return child;
    }

    public static Element addChildList(Element parent, String name, List<Element> elements) {
      Element listElement = new Element(LIST);
      for (Element element : elements) {
        listElement.addContent(element);
      }
      return addChildWithName(parent, name, listElement);
    }

    public static List<Element> getChildList(Element parent, String name) throws StudyUnrecognizedFormatException {
      return getChildList(parent, name, false);
    }

    public static List<Element> getChildList(Element parent, String name, boolean optional) throws StudyUnrecognizedFormatException {
      Element listParent = getChildWithName(parent, name, optional);
      if (listParent != null) {
        Element list = listParent.getChild(LIST);
        if (list != null) {
          return list.getChildren();
        }
      }
      return Collections.emptyList();
    }

    public static Element getChildWithName(Element parent, String name) throws StudyUnrecognizedFormatException {
      return getChildWithName(parent, name, false);
    }

    public static Element getChildWithName(Element parent, String name, boolean optional) throws StudyUnrecognizedFormatException {
      for (Element child : parent.getChildren()) {
        Attribute attribute = child.getAttribute(NAME);
        if (attribute == null) {
          continue;
        }
        if (name.equals(attribute.getValue())) {
          return child;
        }
      }
      if (optional) {
        return null;
      }
      throw new StudyUnrecognizedFormatException();
    }

    public static <K, V> Map<K, V> getChildMap(Element element, String name) throws StudyUnrecognizedFormatException {
      return getChildMap(element, name, false);
    }

    public static <K, V> Map<K, V> getChildMap(Element element, String name, boolean optional) throws StudyUnrecognizedFormatException {
      Element mapParent = getChildWithName(element, name, optional);
      if (mapParent != null) {
        Element map = mapParent.getChild(MAP);
        if (map != null) {
          HashMap result = new HashMap();
          for (Element entry : map.getChildren()) {
            Object key = entry.getAttribute(KEY) == null ? entry.getChild(KEY).getChildren().get(0) : entry.getAttributeValue(KEY);
            Object value = entry.getAttribute(VALUE) == null ? entry.getChild(VALUE).getChildren().get(0) : entry.getAttributeValue(VALUE);
            result.put(key, value);
          }
          return result;
        }
      }
      return Collections.emptyMap();
    }
  }

  public static class Json {

    public static final String TASK_LIST = "task_list";
    public static final String TASK_FILES = "task_files";

    private Json() {
    }

    public static class CourseTypeAdapter implements JsonDeserializer<Course> {

      private final File myCourseFile;

      public CourseTypeAdapter(File courseFile) {
        myCourseFile = courseFile;
      }

      @Override
      public Course deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject courseObject = json.getAsJsonObject();
        JsonArray lessons = courseObject.getAsJsonArray(LESSONS);
        for (int lessonIndex = 1; lessonIndex <= lessons.size(); lessonIndex++) {
          JsonObject lessonObject = lessons.get(lessonIndex - 1).getAsJsonObject();
          JsonArray tasks = lessonObject.getAsJsonArray(TASK_LIST);
          for (int taskIndex = 1; taskIndex <= tasks.size(); taskIndex++) {
            JsonObject taskObject = tasks.get(taskIndex - 1).getAsJsonObject();
            for (Map.Entry<String, JsonElement> taskFile : taskObject.getAsJsonObject(TASK_FILES).entrySet()) {
              String name = taskFile.getKey();
              String filePath = FileUtil.join(myCourseFile.getParent(), EduNames.LESSON + lessonIndex, EduNames.TASK + taskIndex, name);
              VirtualFile resourceFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(filePath));
              if (resourceFile == null) {
                continue;
              }
              Document document = FileDocumentManager.getInstance().getDocument(resourceFile);
              if (document == null) {
                continue;
              }
              JsonObject taskFileObject = taskFile.getValue().getAsJsonObject();
              JsonArray placeholders = taskFileObject.getAsJsonArray(PLACEHOLDERS);
              for (JsonElement placeholder : placeholders) {
                JsonObject placeholderObject = placeholder.getAsJsonObject();
                if (placeholderObject.getAsJsonPrimitive(OFFSET) != null) {
                  break;
                }
                int line = placeholderObject.getAsJsonPrimitive(LINE).getAsInt();
                int start = placeholderObject.getAsJsonPrimitive(START).getAsInt();
                int offset = document.getLineStartOffset(line) + start;
                placeholderObject.addProperty(OFFSET, offset);
              }
            }
          }
        }
        return new GsonBuilder().create().fromJson(json, Course.class);
      }
    }

    public static class StepicTaskFileAdapter implements JsonDeserializer<TaskFile> {

      @Override
      public TaskFile deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        JsonObject taskFileObject = json.getAsJsonObject();
        JsonArray placeholders = taskFileObject.getAsJsonArray(PLACEHOLDERS);
        for (JsonElement placeholder : placeholders) {
          JsonObject placeholderObject = placeholder.getAsJsonObject();
          int line = placeholderObject.getAsJsonPrimitive(LINE).getAsInt();
          int start = placeholderObject.getAsJsonPrimitive(START).getAsInt();
          if (line == -1) {
            placeholderObject.addProperty(OFFSET, start);
          }
          else {
            Document document = EditorFactory.getInstance().createDocument(taskFileObject.getAsJsonPrimitive(TEXT).getAsString());
            placeholderObject.addProperty(OFFSET, document.getLineStartOffset(line) + start);
          }
          final String hintString = placeholderObject.getAsJsonPrimitive(HINT).getAsString();
          final JsonArray hintsArray = new JsonArray();

          try {
            final Type listType = new TypeToken<List<String>>() {}.getType();
            final List<String> hints = gson.fromJson(hintString, listType);
            if (hints != null && !hints.isEmpty()) {
              for (int i = 0; i < hints.size(); i++) {
                if (i == 0) {
                  placeholderObject.addProperty(HINT, hints.get(0));
                  continue;
                }
                hintsArray.add(hints.get(i));
              }
              placeholderObject.add(ADDITIONAL_HINTS, hintsArray);
            }
            else {
              placeholderObject.addProperty(HINT, "");
            }
          }
          catch (JsonParseException e) {
            hintsArray.add(hintString);
          }
        }

        return gson.fromJson(json, TaskFile.class);
      }
    }

    public static class StepicAnswerPlaceholderAdapter implements JsonSerializer<AnswerPlaceholder> {
      @Override
      public JsonElement serialize(AnswerPlaceholder src, Type typeOfSrc, JsonSerializationContext context) {
        final List<String> hints = src.getHints();

        final int length = src.getLength();
        final int start = src.getOffset();
        final String possibleAnswer = src.getPossibleAnswer();
        int line = -1;

        final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final JsonObject answerPlaceholder = new JsonObject();
        answerPlaceholder.addProperty(LINE, line);
        answerPlaceholder.addProperty(START, start);
        answerPlaceholder.addProperty(LENGTH, length);
        answerPlaceholder.addProperty(POSSIBLE_ANSWER, possibleAnswer);

        final String jsonHints = gson.toJson(hints);
        answerPlaceholder.addProperty(HINT, jsonHints);

        return answerPlaceholder;
      }
    }
  }
}
