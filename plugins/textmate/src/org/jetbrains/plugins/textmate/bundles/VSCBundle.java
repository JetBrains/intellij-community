package org.jetbrains.plugins.textmate.bundles;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.textmate.Constants.*;

/**
 * @deprecated use `{@link VSCBundleReaderKt#readVSCBundle}`
 */
@Deprecated(forRemoval = true)
public class VSCBundle extends Bundle {
  private final Map<String, Collection<String>> grammarToExtensions = new LinkedHashMap<>();
  private final Map<String, Collection<String>> configToScopes = new HashMap<>();
  private final List<String> snippetPaths = new ArrayList<>();

  public VSCBundle(@NotNull String name,
                   @NotNull String bundle) {
    super(name, bundle, BundleType.VSCODE);
  }

  @Override
  public @NotNull Collection<File> getGrammarFiles() {
    loadExtensions();
    //noinspection SSBasedInspection
    return grammarToExtensions.keySet().stream().map((path) -> new File(bundleFile, path)).collect(Collectors.toList());
  }

  @Override
  public Collection<String> getExtensions(@NotNull File file, @NotNull Plist plist) {
    HashSet<String> result = new HashSet<>(super.getExtensions(file, plist));
    loadExtensions();
    result.addAll(grammarToExtensions.getOrDefault(FileUtilRt.toSystemIndependentName(
      Objects.requireNonNull(FileUtilRt.getRelativePath(bundleFile, file))), emptyList()));
    return result;
  }

  @Override
  public @NotNull Collection<File> getSnippetFiles() {
    loadExtensions();
    //noinspection SSBasedInspection
    return snippetPaths.stream().map((path) -> new File(bundleFile, path)).collect(Collectors.toList());
  }

  private void loadExtensions() {
    if (!grammarToExtensions.isEmpty()) return;
    File packageJson = new File(bundleFile, "package.json");
    try {
      Object json = createJsonReader().readValue(new FileReader(packageJson, StandardCharsets.UTF_8), Object.class);
      if (json instanceof Map) {
        Object contributes = ((Map<?, ?>)json).get("contributes");
        if (contributes instanceof Map) {
          Object languages = ((Map<?, ?>)contributes).get("languages");
          Object grammars = ((Map<?, ?>)contributes).get("grammars");
          Object snippets = ((Map<?, ?>)contributes).get("snippets");
          if (languages instanceof ArrayList && grammars instanceof ArrayList) {
            Map<String, Collection<String>> idToExtension = new HashMap<>();
            Map<String, String> idToConfig = new HashMap<>();
            for (Object language : (ArrayList)languages) {
              if (language instanceof Map) {
                Object id = ((Map<?, ?>)language).get("id");
                if (id instanceof String) {
                  Object extensions = ((Map<?, ?>)language).get("extensions");
                  if (extensions instanceof ArrayList) {
                    //noinspection unchecked
                    Stream<String> stream = ((ArrayList)extensions).stream().map(ext -> Strings.trimStart((String)ext, "."));
                    idToExtension.computeIfAbsent((String)id, (key) -> new HashSet<>()).addAll(stream.toList());
                  }
                  Object filenames = ((Map<?, ?>)language).get("filenames");
                  if (filenames instanceof ArrayList) {
                    //noinspection unchecked
                    idToExtension.computeIfAbsent((String)id, (key) -> new HashSet<>()).addAll((ArrayList)filenames);
                  }
                  Object configuration = ((Map<?, ?>)language).get("configuration");
                  if (configuration instanceof String) {
                    idToConfig.put((String)id, FileUtilRt.toSystemIndependentName((String)configuration));
                  }
                }
              }
            }

            if (snippets instanceof ArrayList) {
              for (Object snippet : (ArrayList)snippets) {
                if (snippet instanceof Map) {
                  Object path = ((Map<?, ?>)snippet).get("path");
                  if (path instanceof String) {
                    snippetPaths.add((String)path);
                  }
                }
              }
            }

            Map<String, Collection<String>> grammarExtensions = new LinkedHashMap<>();
            Map<String, Collection<String>> scopeConfig = new HashMap<>();
            for (Object grammar : (ArrayList)grammars) {
              if (grammar instanceof Map) {
                Object path = ((Map<?, ?>)grammar).get("path");
                Object language = ((Map<?, ?>)grammar).get("language");
                Collection<String> extensions = idToExtension.getOrDefault(language, emptyList());
                if (path instanceof String) {
                  grammarExtensions.put((String)path, extensions);
                }
                Object scopeName = ((Map<?, ?>)grammar).get("scopeName");
                String config = idToConfig.get(language);
                if (scopeName instanceof String && config != null) {
                  scopeConfig.computeIfAbsent(config, (key) -> new ArrayList<>()).add((String)scopeName);
                }
                Object embedded = ((Map<?, ?>)grammar).get("embeddedLanguages");
                if (embedded instanceof Map) {
                  for (Object embeddedScope : ((Map)embedded).keySet()) {
                    Object embeddedLanguage = ((Map<?, ?>)embedded).get(embeddedScope);
                    if (embeddedScope instanceof String && embeddedLanguage instanceof String) {
                      String embeddedConfig = idToConfig.get(embeddedLanguage);
                      if (embeddedConfig != null) {
                        scopeConfig.computeIfAbsent(embeddedConfig, (key) -> new ArrayList<>()).add((String)embeddedScope);
                      }
                    }
                  }
                }
              }
            }
            grammarToExtensions.putAll(grammarExtensions);
            configToScopes.putAll(scopeConfig);
          }
        }
      }
    }
    catch (Exception ignored) {}
  }

  @Override
  public @NotNull Collection<File> getPreferenceFiles() {
    loadExtensions();
    //noinspection SSBasedInspection
    return configToScopes.keySet().stream().map(config -> new File(bundleFile, config)).collect(Collectors.toList());
  }

  private static @NotNull Plist loadLanguageConfig(File languageConfig) throws IOException {
    try {
      Object json = createJsonReader().readValue(new FileReader(languageConfig, StandardCharsets.UTF_8), Object.class);
      Map<String, PListValue> map = new HashMap<>();
      if (json instanceof Map) {
        map.put(HIGHLIGHTING_PAIRS_KEY, loadBrackets((Map)json, "brackets"));
        map.put(SMART_TYPING_PAIRS_KEY, loadBrackets((Map)json, "surroundingPairs"));
        map.put(SHELL_VARIABLES_KEY, PListValue.Companion.array(loadComments((Map)json)));
        map.put(INDENTATION_RULES, PListValue.Companion.dict(loadIndentationRules((Map)json)));
      }
      return new Plist(map);
    }
    catch (FileNotFoundException e) {
      return Plist.Companion.getEMPTY_PLIST();
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  private static PListValue loadBrackets(Map json, String key) {
    Object brackets = json.get(key);
    if (!(brackets instanceof ArrayList)) {
      return null;
    }
    List<PListValue> pairs = new ArrayList<>();
    for (Object bracket : (ArrayList)brackets) {
      if (bracket instanceof ArrayList && ((ArrayList<?>)bracket).size() == 2) {
        pairs.add(PListValue.Companion.array(PListValue.Companion.string(((ArrayList<?>)bracket).get(0).toString()), PListValue.Companion.string(((ArrayList<?>)bracket).get(1).toString())));
      }
    }
    return PListValue.Companion.array(pairs);
  }

  private static List<PListValue> loadComments(Map json) {
    List<PListValue> variables = new ArrayList<>();
    Object comments = json.get("comments");
    if (comments instanceof Map) {
      Object line = ((Map<?, ?>)comments).get("lineComment");
      boolean hasLine = line instanceof String;
      if (hasLine) {
        variables.add(variable(COMMENT_START_VARIABLE, ((String)line).trim() + " "));
      }
      Object block = ((Map<?, ?>)comments).get("blockComment");
      if (block instanceof ArrayList && ((ArrayList<?>)block).size() == 2) {
        String suffix = hasLine ? "_2" : "";
        variables.add(variable(COMMENT_START_VARIABLE + suffix, ((ArrayList<?>)block).get(0).toString().trim() + " "));
        variables.add(variable(COMMENT_END_VARIABLE + suffix, " " + ((ArrayList<?>)block).get(1).toString().trim()));
      }
    }
    return variables;
  }

  private static Plist loadIndentationRules(Map json) {
    Map<String, PListValue> patterns = new HashMap<>();
    Object rules = json.get("indentationRules");
    if (rules instanceof Map) {
      loadIndentationPattern(patterns, rules, INCREASE_INDENT_PATTERN, "increaseIndentPattern");
      loadIndentationPattern(patterns, rules, DECREASE_INDENT_PATTERN, "decreaseIndentPattern");
      loadIndentationPattern(patterns, rules, INDENT_NEXT_LINE_PATTERN, "indentNextLinePattern");
      loadIndentationPattern(patterns, rules, UNINDENTED_LINE_PATTERN, "unIndentedLinePattern");
    }
    return new Plist(patterns);
  }

  private static void loadIndentationPattern(Map<String, PListValue> patterns, Object rules, String name, String key) {
    Object value = ((Map<?, ?>)rules).get(key);
    if (value instanceof String) {
      patterns.put(name, PListValue.Companion.string((String)value));
    }
  }

  private static PListValue variable(String name, String value) {
    Map<String, PListValue> variable = new HashMap<>();
    variable.put(NAME_KEY, PListValue.Companion.string(name));
    variable.put(VALUE_KEY, PListValue.Companion.string(value));
    return PListValue.Companion.dict(new Plist(variable));
  }

  private static ObjectMapper createJsonReader() {
    var factory = JsonFactory.builder()
      .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
      .build();
    return new ObjectMapper(factory).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}