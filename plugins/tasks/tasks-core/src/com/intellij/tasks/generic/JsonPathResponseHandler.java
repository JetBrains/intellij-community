// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.generic;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mikhail Golubev
 */
@Tag("JsonResponseHandler")
public final class JsonPathResponseHandler extends SelectorBasedResponseHandler {
  static {
    Configuration.setDefaults(new Configuration.Defaults() {
      private final JsonProvider jsonProvider = new JacksonJsonProvider();
      private final MappingProvider mappingProvider = new JacksonMappingProvider();

      @Override
      public JsonProvider jsonProvider() {
        return jsonProvider;
      }

      @Override
      public MappingProvider mappingProvider() {
        return mappingProvider;
      }

      @Override
      public Set<Option> options() {
        return EnumSet.noneOf(Option.class);
      }
    });
  }

  private static final Map<Class<?>, String> JSON_TYPES = Map.of(
    Map.class, "JSON object",
    List.class, "JSON array",
    String.class, "JSON string",
    Integer.class, "JSON number",
    Double.class, "JSON number",
    Boolean.class, "JSON boolean"
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

  private @Nullable Object extractRawValue(@NotNull Selector selector, @NotNull String source) throws Exception {
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

  private @Nullable <T> T extractValueAndCheckType(@NotNull Selector selector, @NotNull String source, Class<T> cls) throws Exception {
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

  @Override
  protected @NotNull List<Object> selectTasksList(@NotNull String response, int max) throws Exception {
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>)extractValueAndCheckType(getSelector(TASKS), response, List.class);
    if (list == null) {
      return ContainerUtil.emptyList();
    }
    JsonProvider jsonProvider = Configuration.defaultConfiguration().jsonProvider();
    return ContainerUtil.getFirstItems(ContainerUtil.map(list, o -> jsonProvider.toJson(o)), max);
  }

  @Override
  protected @Nullable String selectString(@NotNull Selector selector, @NotNull Object context) throws Exception {
    //return extractValueAndCheckType((String)context, selector, String.class);
    final Object value = extractRawValue(selector, (String)context);
    if (value == null) {
      return null;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value.toString(); //NON-NLS
    }
    throw new Exception(String.format("JsonPath expression '%s' should match string value. Got '%s' instead",
                                      selector.getPath(), value));
  }

  @Override
  protected @Nullable Boolean selectBoolean(@NotNull Selector selector, @NotNull Object context) throws Exception {
    return extractValueAndCheckType(selector, (String)context, Boolean.class);
  }

  @SuppressWarnings("UnusedDeclaration")
  private @Nullable Long selectLong(@NotNull Selector selector, @NotNull String source) throws Exception {
    return extractValueAndCheckType(selector, source, Long.class);
  }

  private @NotNull JsonPath lazyCompile(@NotNull String path) throws Exception {
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

  @Override
  public @NotNull ResponseType getResponseType() {
    return ResponseType.JSON;
  }
}
