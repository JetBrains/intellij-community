package com.jetbrains.edu.learning;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.*;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicWrappers;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class StudySerializationUtils {
  private static final Logger LOG = Logger.getInstance(StudySerializationUtils.class);

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
  public static final String ID = "id";
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
    public final static String REMOTE_COURSE = "RemoteCourse";
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
    public static final String SUBTASK_INFO = "AnswerPlaceholderSubtaskInfo";
    public static final String SUBTASK_INFOS = "subtaskInfos";
    public static final String ADDITIONAL_HINTS = "additionalHints";
    public static final String POSSIBLE_ANSWER = "possibleAnswer";
    public static final String SELECTED = "selected";
    public static final String TASK_TEXT = "taskText";
    public static final String PLACEHOLDER_TEXT = "placeholderText";
    private static final String TASK_HTML = "task.html";
    private static final String TASK_MD = "task.md";
    private static String LAST_SUBTASK_INDEX = "lastSubtaskIndex";
    private static String THEORY_TAG = "theoryTask";
    private static String ADAPTIVE_TASK_PARAMETERS = "adaptiveTaskParameters";
    private static String ADAPTIVE = "adaptive";
    public static final String PYCHARM_TASK = "PyCharmTask";
    private static String TASK_WITH_SUBTASKS = "TaskWithSubtasks";
    private static String THEORY_TASK = "TheoryTask";
    private static String CHOICE_TASK = "ChoiceTask";
    private static String CODE_TASK = "CodeTask";
    private static String TASK_TEXTS = "taskTexts";

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

    @NotNull
    public static Element convertToForthVersion(Element state) throws StudyUnrecognizedFormatException {
      Element taskManagerElement = state.getChild(MAIN_ELEMENT);
      Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
      for (Element lesson : getChildList(courseElement, LESSONS)) {
        for (Element task : getChildList(lesson, TASK_LIST)) {
          Map<String, Element> taskFiles = getChildMap(task, TASK_FILES);
          for (Map.Entry<String, Element> entry : taskFiles.entrySet()) {
            Element taskFileElement = entry.getValue();
            for (Element placeholder : getChildList(taskFileElement, ANSWER_PLACEHOLDERS)) {
              Element valueElement = new Element(SUBTASK_INFO);
              addChildMap(placeholder, SUBTASK_INFOS, Collections.singletonMap(String.valueOf(0), valueElement));
              for (String childName : ContainerUtil.list(HINT, POSSIBLE_ANSWER, SELECTED, STATUS, TASK_TEXT)) {
                Element child = getChildWithName(placeholder, childName, true);
                if (child == null) {
                  continue;
                }
                valueElement.addContent(child.clone());
              }
              renameElement(getChildWithName(valueElement, TASK_TEXT), PLACEHOLDER_TEXT);
              Element hint = getChildWithName(valueElement, HINT);
              Element firstHint = new Element(OPTION).setAttribute(VALUE, hint.getAttributeValue(VALUE));
              List<Element> newHints = new ArrayList<>();
              newHints.add(firstHint);
              newHints.addAll(ContainerUtil.map(getChildList(placeholder, ADDITIONAL_HINTS, true), Element::clone));
              addChildList(valueElement, "hints", newHints);
            }
          }
        }
      }

      return state;
    }

    public static Element convertToFifthVersion(Element state) throws StudyUnrecognizedFormatException {
      Element taskManagerElement = state.getChild(MAIN_ELEMENT);
      Element courseElement = getChildWithName(taskManagerElement, COURSE).getChild(COURSE_TITLED);
      final int courseId = getAsInt(courseElement, ID);
      if (courseElement != null && courseId > 0) {
        courseElement.setName(REMOTE_COURSE);
      }
      final Element adaptive = getChildWithName(courseElement, ADAPTIVE);
      for (Element lesson : getChildList(courseElement, LESSONS)) {
        for (Element task : getChildList(lesson, TASK_LIST)) {
          final Element lastSubtaskIndex = getChildWithName(task, LAST_SUBTASK_INDEX, true); //could be broken by 3->4 migration
          final Element adaptiveParams = getChildWithName(task, ADAPTIVE_TASK_PARAMETERS, true);
          Element theoryTask = getChildWithName(task, THEORY_TAG, true);
          if (theoryTask == null && adaptiveParams != null) {
            theoryTask = getChildWithName(adaptiveParams, THEORY_TAG, true);
          }
          final boolean hasAdaptiveParams = adaptiveParams != null && !adaptiveParams.getChildren().isEmpty();
          if (lastSubtaskIndex != null && Integer.valueOf(lastSubtaskIndex.getAttributeValue(VALUE)) != 0) {
            task.setName(TASK_WITH_SUBTASKS);
          }
          else if (theoryTask != null && Boolean.valueOf(theoryTask.getAttributeValue(VALUE))) {
            task.setName(THEORY_TASK);
          }
          else if (hasAdaptiveParams) {
            task.setName(CHOICE_TASK);
            final Element adaptiveParameters = adaptiveParams.getChildren().get(0);
            for (Element element : adaptiveParameters.getChildren()) {
              final Attribute name = element.getAttribute(NAME);
              if (name != null && !THEORY_TAG.equals(name.getValue())) {
                final Content elementCopy = element.clone();
                task.addContent(elementCopy);
              }
            }
          }
          else if (Boolean.valueOf(adaptive.getAttributeValue(VALUE))) {
            task.setName(CODE_TASK);
          }
          else {
            task.setName(PYCHARM_TASK);
          }
          task.removeContent(adaptiveParams);
          task.removeContent(theoryTask);
        }
      }
      return state;
    }

    public static Element convertToSixthVersion(Element state, Project project) throws StudyUnrecognizedFormatException {
      Element taskManagerElement = state.getChild(MAIN_ELEMENT);
      Element courseHolder = getChildWithName(taskManagerElement, COURSE);
      Element courseElement = courseHolder.getChild(COURSE_TITLED);
      if (courseElement == null) {
        courseElement = courseHolder.getChild(REMOTE_COURSE);
        if (courseElement == null) {
          throw new StudyUnrecognizedFormatException();
        }
      }
      for (Element lesson : getChildList(courseElement, LESSONS)) {
        for (Element task : getChildList(lesson, TASK_LIST)) {
          VirtualFile taskDir = getTaskDir(project, lesson, task);
          if (taskDir == null) {
            throw new StudyUnrecognizedFormatException();
          }
          List<VirtualFile> taskDescriptionFiles = Arrays.stream(taskDir.getChildren())
            .filter(file -> isTaskDescriptionFile(file.getName()))
            .collect(Collectors.toList());
          Map<String, String> taskTextsMap = new HashMap<>();
          for (VirtualFile file : taskDescriptionFiles) {
            try {
              String text = VfsUtilCore.loadText(file);
              String key = FileUtil.getNameWithoutExtension(file.getName());
              if (key.equals(EduNames.TASK) && taskDescriptionFiles.size() > 1) {
                taskTextsMap.put(EduNames.TASK + EduNames.SUBTASK_MARKER + 0, text);
              }
              else {
                taskTextsMap.put(key, text);
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
          }
          addTextChildMap(task, TASK_TEXTS, taskTextsMap);
          for (VirtualFile file : taskDescriptionFiles) {
            ApplicationManager.getApplication().runWriteAction(() -> {
              try {
                file.delete(project);
              }
              catch (IOException e) {
                LOG.error(e);
              }
            });
          }
        }
      }
      return state;
    }

    public static VirtualFile getTaskDir(Project project, Element lesson, Element task)
      throws StudyUnrecognizedFormatException {
      VirtualFile taskDir =
        project.getBaseDir().findChild(EduNames.LESSON + getAsInt(lesson, INDEX)).findChild(EduNames.TASK + getAsInt(task, INDEX));
      if (taskDir == null) {
        return null;
      }
      VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
      if (srcDir != null) {
        taskDir = srcDir;
      }
      return taskDir;
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

    public static Element addChildMap(Element parent, String name, Map<String, Element> value) {
      Element mapElement = new Element(MAP);
      for (Map.Entry<String, Element> entry : value.entrySet()) {
        Element entryElement = new Element("entry");
        mapElement.addContent(entryElement);
        String key = entry.getKey();
        entryElement.setAttribute("key", key);
        Element valueElement = new Element("value");
        valueElement.addContent(entry.getValue());
        entryElement.addContent(valueElement);
      }
      return addChildWithName(parent, name, mapElement);
    }

    public static Element addTextChildMap(Element parent, String name, Map<String, String> value) {
      Element mapElement = new Element(MAP);
      for (Map.Entry<String, String> entry : value.entrySet()) {
        Element entryElement = new Element("entry");
        mapElement.addContent(entryElement);
        String key = entry.getKey();
        entryElement.setAttribute("key", key);
        entryElement.setAttribute("value", entry.getValue());
      }
      return addChildWithName(parent, name, mapElement);
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

    private static boolean isTaskDescriptionFile(@NotNull final String fileName) {
      if (TASK_HTML.equals(fileName) || TASK_MD.equals(fileName)) {
        return true;
      }
      String extension = FileUtilRt.getExtension(fileName);
      if (!extension.equals(FileUtilRt.getExtension(TASK_HTML)) && !extension.equals(FileUtilRt.getExtension(TASK_MD))) {
        return false;
      }
      return fileName.contains(EduNames.TASK) && fileName.contains(EduNames.SUBTASK_MARKER);
    }
  }

  public static class Json {

    public static final String TASK_LIST = "task_list";
    public static final String TASK_FILES = "task_files";
    public static final String FILES = "files";
    public static final String TESTS = "test";
    public static final String TEXTS = "text";
    public static final String HINTS = "hints";
    public static final String SUBTASK_INFOS = "subtask_infos";
    public static final String FORMAT_VERSION = "format_version";
    public static final String INDEX = "index";
    public static final String TASK_TYPE = "task_type";
    public static final String NAME = "name";
    public static final String LAST_SUBTASK = "last_subtask_index";

    private Json() {
    }

    public static class StepicStepOptionsAdapter implements JsonDeserializer<StepicWrappers.StepOptions> {
      @Override
      public StepicWrappers.StepOptions deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {
        JsonObject stepOptionsJson = json.getAsJsonObject();
        JsonPrimitive versionJson = stepOptionsJson.getAsJsonPrimitive(FORMAT_VERSION);
        int version = 1;
        if (versionJson != null) {
          version = versionJson.getAsInt();
        }
        switch (version) {
          case 1:
            stepOptionsJson = convertToSecondVersion(stepOptionsJson);
          case 2:
            stepOptionsJson = convertToThirdVersion(stepOptionsJson);
          // uncomment for future versions
          //case 3:
          //  stepOptionsJson = convertToFourthVersion(stepOptionsJson);
        }
        convertSubtaskInfosToMap(stepOptionsJson);
        StepicWrappers.StepOptions stepOptions =
          new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
            .fromJson(stepOptionsJson, StepicWrappers.StepOptions.class);
        stepOptions.formatVersion = EduStepicConnector.CURRENT_VERSION;
        return stepOptions;
      }

      private static JsonObject convertToThirdVersion(JsonObject stepOptionsJson) {
        if (!stepOptionsJson.has(LAST_SUBTASK)) return stepOptionsJson;
        final int lastSubtaskIndex = stepOptionsJson.get(LAST_SUBTASK).getAsInt();
        if (lastSubtaskIndex == 0) return stepOptionsJson;
        final JsonArray tests = stepOptionsJson.getAsJsonArray(TESTS);
        if (tests.size() > 0) {
          final JsonObject fileWrapper = tests.get(0).getAsJsonObject();
          if (fileWrapper.has(NAME)) {
            replaceWithSubtask(fileWrapper);
          }
        }
        final JsonArray descriptions = stepOptionsJson.getAsJsonArray(TEXTS);
        if (descriptions != null && descriptions.size() > 0) {
          final JsonObject fileWrapper = descriptions.get(0).getAsJsonObject();
          if (fileWrapper.has(NAME)) {
            replaceWithSubtask(fileWrapper);
          }
        }
        return stepOptionsJson;
      }

      private static void replaceWithSubtask(JsonObject fileWrapper) {
        final String file = fileWrapper.get(NAME).getAsString();
        final String extension = FileUtilRt.getExtension(file);
        final String name = FileUtil.getNameWithoutExtension(file);
        if (!name.contains(EduNames.SUBTASK_MARKER)) {
          fileWrapper.remove(NAME);
          fileWrapper.add(NAME, new JsonPrimitive(name + "_subtask0." + extension));
        }
      }

      private static JsonObject convertSubtaskInfosToMap(JsonObject stepOptionsJson) {
        final JsonArray files = stepOptionsJson.getAsJsonArray(FILES);
        if (files != null) {
          for (JsonElement taskFileElement : files) {
            JsonObject taskFileObject = taskFileElement.getAsJsonObject();
            JsonArray placeholders = taskFileObject.getAsJsonArray(PLACEHOLDERS);
            for (JsonElement placeholder : placeholders) {
              JsonObject placeholderObject = placeholder.getAsJsonObject();
              removeIndexFromSubtaskInfos(placeholderObject);
            }
          }
        }
        return stepOptionsJson;
      }

      private static JsonObject convertToSecondVersion(JsonObject stepOptionsJson) {
        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
        final JsonArray files = stepOptionsJson.getAsJsonArray(FILES);
        if (files != null) {
          for (JsonElement taskFileElement : files) {
            JsonObject taskFileObject = taskFileElement.getAsJsonObject();
            JsonArray placeholders = taskFileObject.getAsJsonArray(PLACEHOLDERS);
            for (JsonElement placeholder : placeholders) {
              JsonObject placeholderObject = placeholder.getAsJsonObject();
              convertToAbsoluteOffset(taskFileObject, placeholderObject);
              convertMultipleHints(gson, placeholderObject);
              convertToSubtaskInfo(placeholderObject);
            }
          }
        }
        return stepOptionsJson;
      }

      private static void convertMultipleHints(Gson gson, JsonObject placeholderObject) {
        final String hintString = placeholderObject.getAsJsonPrimitive(HINT).getAsString();
        final JsonArray hintsArray = new JsonArray();

        try {
          final Type listType = new TypeToken<List<String>>() {
          }.getType();
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

      private static void convertToAbsoluteOffset(JsonObject taskFileObject, JsonObject placeholderObject) {
        int line = placeholderObject.getAsJsonPrimitive(LINE).getAsInt();
        int start = placeholderObject.getAsJsonPrimitive(START).getAsInt();
        if (line == -1) {
          placeholderObject.addProperty(OFFSET, start);
        }
        else {
          Document document = EditorFactory.getInstance().createDocument(taskFileObject.getAsJsonPrimitive(TEXT).getAsString());
          placeholderObject.addProperty(OFFSET, document.getLineStartOffset(line) + start);
        }
      }
    }

    public static void removeIndexFromSubtaskInfos(JsonObject placeholderObject) {
      JsonArray infos = placeholderObject.getAsJsonArray(SUBTASK_INFOS);
      Map<Integer, JsonObject> objectsToInsert = new HashMap<>();
      for (JsonElement info : infos) {
        JsonObject object = info.getAsJsonObject();
        int index = object.getAsJsonPrimitive(INDEX).getAsInt();
        objectsToInsert.put(index, object);
      }
      placeholderObject.remove(SUBTASK_INFOS);
      JsonObject newInfos = new JsonObject();
      placeholderObject.add(SUBTASK_INFOS, newInfos);
      for (Map.Entry<Integer, JsonObject> entry : objectsToInsert.entrySet()) {
        newInfos.add(entry.getKey().toString(), entry.getValue());
      }
    }

    private static void convertToSubtaskInfo(JsonObject placeholderObject) {
      JsonArray subtaskInfos = new JsonArray();
      placeholderObject.add(SUBTASK_INFOS, subtaskInfos);
      JsonArray hintsArray = new JsonArray();
      hintsArray.add(placeholderObject.getAsJsonPrimitive(HINT).getAsString());
      JsonArray additionalHints = placeholderObject.getAsJsonArray(ADDITIONAL_HINTS);
      if (additionalHints != null) {
        hintsArray.addAll(additionalHints);
      }
      JsonObject subtaskInfo = new JsonObject();
      subtaskInfos.add(subtaskInfo);
      subtaskInfo.add(INDEX, new JsonPrimitive(0));
      subtaskInfo.add(HINTS, hintsArray);
      subtaskInfo.addProperty(POSSIBLE_ANSWER, placeholderObject.getAsJsonPrimitive(POSSIBLE_ANSWER).getAsString());
    }

    public static class TaskAdapter implements JsonSerializer<Task>, JsonDeserializer<Task> {

      @Override
      public JsonElement serialize(Task src, Type typeOfSrc, JsonSerializationContext context) {
        Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
        JsonElement tree = gson.toJsonTree(src);
        final JsonObject task = tree.getAsJsonObject();
        task.add(TASK_TYPE, new JsonPrimitive(src.getTaskType()));
        return task;
      }

      @Override
      public Task deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
        final JsonObject object = json.getAsJsonObject();
        if (object.has(TASK_TYPE)) {
          final String taskType = object.get(TASK_TYPE).getAsString();
          switch (taskType) {
            case "choice": return gson.fromJson(json, ChoiceTask.class);
            case "theory": return gson.fromJson(json, TheoryTask.class);
            case "code": return gson.fromJson(json, CodeTask.class);
            case "pycharm": return gson.fromJson(json, PyCharmTask.class);
            case "subtasks": return gson.fromJson(json, TaskWithSubtasks.class);
            case "output": return gson.fromJson(json, OutputTask.class);
            default: {
              LOG.warn("Unsupported task type " + taskType);
              return null;
            }
          }
        }
        LOG.warn("No task type found in json " + json.toString());
        return null;
      }
    }

    public static class StepicAnswerPlaceholderAdapter implements JsonSerializer<AnswerPlaceholder> {
      @Override
      public JsonElement serialize(AnswerPlaceholder placeholder, Type typeOfSrc, JsonSerializationContext context) {
        Gson gson = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
        JsonElement answerPlaceholderJson = gson.toJsonTree(placeholder);
        JsonObject answerPlaceholderObject = answerPlaceholderJson.getAsJsonObject();
        JsonObject subtaskInfos = answerPlaceholderObject.getAsJsonObject(SUBTASK_INFOS);
        JsonArray infosArray = new JsonArray();
        for (Map.Entry<String, JsonElement> entry : subtaskInfos.entrySet()) {
          JsonObject subtaskInfo = entry.getValue().getAsJsonObject();
          subtaskInfo.add(INDEX, new JsonPrimitive(Integer.valueOf(entry.getKey())));
          infosArray.add(subtaskInfo);
        }
        answerPlaceholderObject.remove(SUBTASK_INFOS);
        answerPlaceholderObject.add(SUBTASK_INFOS, infosArray);
        return answerPlaceholderJson;
      }
    }
  }
}
