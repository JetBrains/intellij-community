package com.intellij.tasks.generic;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.Tag;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
@Tag("JsonResponseHandler")
public final class JsonPathResponseHandler extends SelectorBasedResponseHandler {

  private static final Map<Class<?>, String> JSON_TYPES = ContainerUtil.newHashMap(
    new Pair<>(Map.class, "JSON object"),
    new Pair<>(List.class, "JSON array"),
    new Pair<>(String.class, "JSON string"),
    new Pair<>(Integer.class, "JSON number"),
    new Pair<>(Double.class, "JSON number"),
    new Pair<>(Boolean.class, "JSON boolean")
  );

  private final Map<String, JsonPath> myCompiledCache = new HashMap<>();

  /**
   * Serialization constructor
   */
  @SuppressWarnings("UnusedDeclaration")
  public JsonPathResponseHandler() {
  }

  public JsonPathResponseHandler(GenericRepository repository) {
    super(repository);
  }

  @Nullable
  private Object extractRawValue(@NotNull Selector selector, @NotNull String source) throws Exception {
    if (StringUtil.isEmpty(selector.getPath())) {
      return null;
    }
    JsonPath jsonPath = lazyCompile(selector.getPath());
    Object value;
    try {
      value = jsonPath.read(source);
    }
    catch (InvalidPathException e) {
      throw new Exception(String.format("JsonPath expression '%s' doesn't match", selector.getPath()), e);
    }
    if (value == null) {
      return null;
    }
    return value;
  }

  @Nullable
  private <T> T extractValueAndCheckType(@NotNull Selector selector, @NotNull String source, Class<T> cls) throws Exception {
    final Object value = extractRawValue(selector, source);
    if (value == null) {
      return null;
    }
    if (!(cls.isInstance(value))) {
      throw new Exception(
        String.format("JsonPath expression '%s' should match %s. Got '%s' instead",
                      selector.getPath(), JSON_TYPES.get(cls), value));
    }
    @SuppressWarnings("unchecked")
    T casted = (T)value;
    return casted;
  }

  @NotNull
  @Override
  protected List<Object> selectTasksList(@NotNull String response, int max) throws Exception {
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>)extractValueAndCheckType(getSelector(TASKS), response, List.class);
    if (list == null) {
      return ContainerUtil.emptyList();
    }
    return ContainerUtil.map2List(list, (Function<Object, Object>)o -> o.toString()).subList(0, Math.min(list.size(), max));
  }

  @Nullable
  @Override
  protected String selectString(@NotNull Selector selector, @NotNull Object context) throws Exception {
    //return extractValueAndCheckType((String)context, selector, String.class);
    final Object value = extractRawValue(selector, (String)context);
    if (value == null) {
      return null;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }
    throw new Exception(String.format("JsonPath expression '%s' should match string value. Got '%s' instead",
                                      selector.getPath(), value));
  }

  @Nullable
  @Override
  protected Boolean selectBoolean(@NotNull Selector selector, @NotNull Object context) throws Exception {
    return extractValueAndCheckType(selector, (String)context, Boolean.class);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  private Long selectLong(@NotNull Selector selector, @NotNull String source) throws Exception {
    return extractValueAndCheckType(selector, source, Long.class);
  }

  @NotNull
  private JsonPath lazyCompile(@NotNull String path) throws Exception {
    JsonPath jsonPath = myCompiledCache.get(path);
    if (jsonPath == null) {
      try {
        jsonPath = JsonPath.compile(path);
        myCompiledCache.put(path, jsonPath);
      }
      catch (InvalidPathException e) {
        throw new Exception(String.format("Malformed JsonPath expression '%s'", path));
      }
    }
    return jsonPath;
  }

  @NotNull
  @Override
  public ResponseType getResponseType() {
    return ResponseType.JSON;
  }
}
