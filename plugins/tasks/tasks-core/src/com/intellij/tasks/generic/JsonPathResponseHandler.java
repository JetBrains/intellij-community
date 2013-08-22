package com.intellij.tasks.generic;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.Task;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Author: Mikhail Golubev
 */
@Tag("JsonResponseHandler")
public final class JsonPathResponseHandler extends SelectorBasedResponseHandler {

  private static final Map<Class<?>, String> JSON_TYPES = ContainerUtil.newHashMap(
    new Pair<Class<?>, String>(Map.class, "JSON object"),
    new Pair<Class<?>, String>(List.class, "JSON array"),
    new Pair<Class<?>, String>(String.class, "JSON string"),
    new Pair<Class<?>, String>(Integer.class, "JSON number"),
    new Pair<Class<?>, String>(Double.class, "JSON number"),
    new Pair<Class<?>, String>(Boolean.class, "JSON boolean")
  );

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public JsonPathResponseHandler() {
  }

  public JsonPathResponseHandler(GenericRepository repository) {
    super(repository);
  }

  @Override
  public FileType getSelectorFileType() {
    return PlainTextFileType.INSTANCE;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public Task[] doParseIssues(String response) throws Exception {
    Object tasksMatch = JsonPath.read(response, getSelectorPath("tasks"));
    if (!(tasksMatch instanceof List)) {
      throw new Exception("Selector 'task' should match array of tasks. Got " + tasksMatch.toString() + " instead");
    }
    List<Object> tasks = (List<Object>)tasksMatch;
    List<GenericTask> result = new ArrayList<GenericTask>(tasks.size());
    for (Object rawTask : tasks) {
      String taskText = rawTask.toString();
      String id = extractId(taskText, getSelector("id"));
      String summary = extractString(taskText, getSelector("summary"));
      assert summary != null;
      GenericTask task = new GenericTask(id, summary, myRepository);
      task.setDescription(extractString(response, getSelector("description")));
      task.setIssueUrl(extractString(response, getSelector("issueUrl")));
      Boolean closed = extractBoolean(response, getSelector("closed"));
      if (closed != null) {
        task.setClosed(closed);
      }
      task.setUpdated(extractDate(response, getSelector("updated")));
      task.setCreated(extractDate(response, getSelector("created")));
      result.add(task);
    }
    return result.toArray(new Task[result.size()]);
  }

  @Nullable
  @Override
  public Task doParseIssue(String response) throws Exception {
    String id = extractId(response, getSelector("singleTask-id"));
    String summary = extractString(response, getSelector("singleTask-summary"));
    GenericTask task = new GenericTask(id, summary, myRepository);
    task.setDescription(extractString(response, getSelector("singleTask-description")));
    task.setIssueUrl(extractString(response, getSelector("singleTask-issueUrl")));
    Boolean closed = extractBoolean(response, getSelector("singleTask-closed"));
    if (closed != null) {
      task.setClosed(closed);
    }
    task.setUpdated(extractDate(response, getSelector("singleTask-updated")));
    task.setCreated(extractDate(response, getSelector("singleTask-created")));
    return task;
  }

  @SuppressWarnings({"unchecked", "MethodMayBeStatic"})
  @Nullable
  private <T> T extractValueAndCheckType(String source, Selector selector, Class<T> cls) throws Exception {
    if (selector == null || StringUtil.isEmpty(selector.getPath())) {
      return null;
    }
    Object value;
    try {
      value = JsonPath.read(source, selector.getPath());
    }
    catch (InvalidPathException e) {
      // NOTE: could be thrown when selector is actually invalid or just not matched
      throw new Exception(String.format("JsonPath expression '%s' is malformed or didn't match", selector.getPath()), e);
    }
    if (value == null) {
      return null;
    }
    if (!(cls.isInstance(value))) {
      throw new Exception(
        String.format("Selector '%s' should match %s. Got '%s' instead", selector.getName(), JSON_TYPES.get(cls), value.toString()));
    }
    return (T)value;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  private String extractId(String task, Selector idSelector) throws Exception {
    Object rawId;
    try {
      rawId = JsonPath.read(task, idSelector.getPath());
    }
    catch (InvalidPathException e) {
      throw new Exception(String.format("JsonPath expression '%s' is malformed or didn't match", idSelector.getPath()), e);
    }
    if (!(rawId instanceof String) && !(rawId instanceof Long)) {
      throw new Exception(
        String.format("Selector 'id' should match either JSON string or JSON number value. Got '%s' instead", rawId.toString()));
    }
    return String.valueOf(rawId);
  }

  private String extractString(String source, Selector selector) throws Exception {
    return extractValueAndCheckType(source, selector, String.class);
  }

  private Boolean extractBoolean(String source, Selector selector) throws Exception {
    return extractValueAndCheckType(source, selector, Boolean.class);
  }

  private Long extractLong(String source, Selector selector) throws Exception {
    return extractValueAndCheckType(source, selector, Long.class);
  }

  private Date extractDate(String response, Selector selector) throws Exception {
    String dateString = extractString(response, selector);
    if (dateString == null) {
      return null;
    }
    return GenericRepositoryUtil.parseISO8601Date(dateString);
  }

  @Override
  public ResponseType getResponseType() {
    return ResponseType.JSON;
  }
}
