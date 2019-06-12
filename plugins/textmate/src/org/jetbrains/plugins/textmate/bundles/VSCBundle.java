package org.jetbrains.plugins.textmate.bundles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptyList;
import static org.jetbrains.plugins.textmate.Constants.*;
import static org.jetbrains.plugins.textmate.plist.PListValue.*;

public class VSCBundle extends Bundle {
  private final Map<String, List<String>> grammarToExtensions = new HashMap<>();
  private final MultiMap<String, String> configToScopes = new MultiMap<>();

  public VSCBundle(@NotNull String name,
                   @NotNull String bundle) {
    super(name, bundle, BundleType.VSCODE);
  }

  @NotNull
  @Override
  public Collection<File> getGrammarFiles() {
    loadExtensions();
    return ContainerUtil.map(grammarToExtensions.keySet(), (path) -> new File(bundleFile, path));

  }

  @Override
  public List<String> getExtensions(File file, Plist plist) {
    loadExtensions();
    return grammarToExtensions.getOrDefault(FileUtil.toSystemIndependentName(Objects.requireNonNull(FileUtil.getRelativePath(bundleFile, file))), emptyList());
  }

  private void loadExtensions() {
    if (!grammarToExtensions.isEmpty()) return;
    File packageJson = new File(bundleFile, "package.json");
    try {
      Object json = new Gson().fromJson(new FileReader(packageJson), Object.class);
      if (json instanceof Map) {
        Object contributes = ((Map)json).get("contributes");
        if (contributes instanceof Map) {
          Object languages = ((Map)contributes).get("languages");
          Object grammars = ((Map)contributes).get("grammars");
          if (languages instanceof ArrayList && grammars instanceof ArrayList) {
            Map<String, List<String>> idToExtension = new HashMap<>();
            Map<String, String> idToConfig = new HashMap<>();
            for (Object language : (ArrayList)languages) {
              if (language instanceof Map) {
                Object id = ((Map)language).get("id");
                if (id instanceof String) {
                  Object extensions = ((Map)language).get("extensions");
                  if (extensions instanceof ArrayList) {
                    idToExtension.put((String)id, ContainerUtil.map((ArrayList)extensions, (ext) -> StringUtil.trimStart((String)ext, ".")));
                  }
                  Object configuration = ((Map)language).get("configuration");
                  if (configuration instanceof String) {
                    idToConfig.put((String)id, (String)configuration);
                  }
                }
              }
            }
            Map<String, List<String>> grammarExtensions = new HashMap<>();
            MultiMap<String, String> scopeConfig = new MultiMap<>();
            for (Object grammar : (ArrayList)grammars) {
              if (grammar instanceof Map) {
                Object path = ((Map)grammar).get("path");
                Object language = ((Map)grammar).get("language");
                List<String> extensions = idToExtension.get(language);
                if (path instanceof String) {
                  grammarExtensions.put((String)path, extensions != null ? extensions : emptyList());
                }
                Object scopeName = ((Map)grammar).get("scopeName");
                String config = idToConfig.get(language);
                if (scopeName instanceof String && config != null) {
                  scopeConfig.putValue(config, (String)scopeName);
                }
                Object embedded = ((Map)grammar).get("embeddedLanguages");
                if (embedded instanceof Map) {
                  for (Object embeddedScope : ((Map)embedded).keySet()) {
                    Object embeddedLanguage = ((Map)embedded).get(embeddedScope);
                    if (embeddedScope instanceof String && embeddedLanguage instanceof String) {
                      String embeddedConfig = idToConfig.get(embeddedLanguage);
                      if (embeddedConfig != null) {
                        scopeConfig.putValue(embeddedConfig, (String)embeddedScope);
                      }
                    }
                  }
                }
              }
            }
            grammarToExtensions.putAll(grammarExtensions);
            configToScopes.putAllValues(scopeConfig);
          }
        }
      }
    }
    catch (FileNotFoundException ignored) {
    }
  }

  @NotNull
  @Override
  public Collection<File> getPreferenceFiles() {
    return ContainerUtil.map(configToScopes.keySet(), (String config) -> new File(bundleFile, config));
  }

  @Override
  public List<Pair<String, Plist>> loadPreferenceFile(File file) throws IOException {
    Plist fromJson = loadLanguageConfig(file);
    return ContainerUtil.map(configToScopes.get(FileUtil.getRelativePath(bundleFile, file)), (String scope) -> Pair.create(scope, fromJson));
  }

  @NotNull
  private static Plist loadLanguageConfig(File languageConfig) throws IOException {
    Gson gson = new GsonBuilder().setLenient().create();
    Object json = gson.fromJson(new FileReader(languageConfig), Object.class);
    Plist settings = new Plist();
    if (json instanceof Map) {
      settings.setEntry(HIGHLIGHTING_PAIRS_KEY, loadBrackets((Map)json, "brackets"));
      settings.setEntry(SMART_TYPING_PAIRS_KEY, loadBrackets((Map)json, "surroundingPairs"));
      settings.setEntry(SHELL_VARIABLES_KEY, array(loadComments((Map)json)));
    }
    return settings;
  }

  private static PListValue loadBrackets(Map json, String key) {
    Object brackets = json.get(key);
    if (!(brackets instanceof ArrayList)) {
      return null;
    }
    List<PListValue> pairs = new ArrayList<>();
    for (Object bracket : (ArrayList)brackets) {
      if (bracket instanceof ArrayList && ((ArrayList)bracket).size() == 2) {
        pairs.add(array(string(((ArrayList)bracket).get(0).toString()), string(((ArrayList)bracket).get(1).toString())));
      }
    }
    return array(pairs);
  }

  private static List<PListValue> loadComments(Map json) {
    List<PListValue> variables = new ArrayList<>();
    Object comments = json.get("comments");
    if (comments instanceof Map) {
      Object line = ((Map)comments).get("lineComment");
      boolean hasLine = line instanceof String;
      if (hasLine) {
        variables.add(variable(COMMENT_START_VARIABLE, ((String)line).trim() + " "));
      }
      Object block = ((Map)comments).get("blockComment");
      if (block instanceof ArrayList && ((ArrayList)block).size() == 2) {
        String suffix = hasLine ? "_2" : "";
        variables.add(variable(COMMENT_START_VARIABLE + suffix, ((ArrayList)block).get(0).toString().trim() + " "));
        variables.add(variable(COMMENT_END_VARIABLE + suffix, " " + ((ArrayList)block).get(1).toString().trim()));
      }
    }
    return variables;
  }

  private static PListValue variable(String name, String value) {
    Plist variable = new Plist();
    variable.setEntry(NAME_KEY, string(name));
    variable.setEntry(VALUE_KEY, string(value));
    return dict(variable);
  }
}
