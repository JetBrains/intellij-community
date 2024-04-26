package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.util.containers.Interner;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p/>
 * Table of textmate syntax rules.
 * Table represents mapping from scopeNames to set of syntax rules {@link SyntaxNodeDescriptor}.
 * <p/>
 * In order to lexing some file with this rules you should retrieve syntax rule
 * by scope name of target language {@link #getSyntax(CharSequence)}.
 * <p/>
 * Scope name of target language can be find in syntax files of TextMate bundles.
 */
public final class TextMateSyntaxTable {
  private static final LoggerRt LOG = LoggerRt.getInstance(TextMateSyntaxTable.class);
  private final Map<CharSequence, SyntaxNodeDescriptor> rulesMap = new ConcurrentHashMap<>();
  private Object2IntMap<String> ruleIds; // guarded by this

  public synchronized void compact() {
    ruleIds = null;
  }

  /**
   * Append table with new syntax rules in order to support new language.
   *
   * @param plist Plist represented syntax file (*.tmLanguage) of target language.
   * @return language scope root name
   */
  @Nullable
  public CharSequence loadSyntax(Plist plist, @NotNull Interner<CharSequence> interner) {
    return loadRealNode(plist, null, interner).getScopeName();
  }

  /**
   * Returns root syntax rule by scope name.
   *
   * @param scopeName Name of scope defined for some language.
   * @return root syntax rule from table for language with given scope name.
   * If tables don't contain syntax rule for given scope,
   * method returns {@link SyntaxNodeDescriptor#EMPTY_NODE}.
   */
  @NotNull
  public SyntaxNodeDescriptor getSyntax(CharSequence scopeName) {
    SyntaxNodeDescriptor syntaxNodeDescriptor = rulesMap.get(scopeName);
    if (syntaxNodeDescriptor == null) {
      LOG.info("Can't find syntax node for scope: '" + scopeName + "'");
      return SyntaxNodeDescriptor.EMPTY_NODE;
    }
    return syntaxNodeDescriptor;
  }

  public void clear() {
    rulesMap.clear();
  }

  private SyntaxNodeDescriptor loadNestedSyntax(@NotNull Plist plist,
                                                @NotNull SyntaxNodeDescriptor parentNode,
                                                @NotNull Interner<CharSequence> interner) {
    return plist.contains(Constants.INCLUDE_KEY) ? loadProxyNode(plist, parentNode, interner) : loadRealNode(plist, parentNode, interner);
  }

  @NotNull
  private SyntaxNodeDescriptor loadRealNode(@NotNull Plist plist,
                                            @Nullable SyntaxNodeDescriptor parentNode,
                                            @NotNull Interner<CharSequence> interner) {
    PListValue scopeNameValue = plist.getPlistValue(Constants.StringKey.SCOPE_NAME.value);
    CharSequence scopeName = scopeNameValue != null ? interner.intern(scopeNameValue.getString()) : null;
    MutableSyntaxNodeDescriptor result = new SyntaxNodeDescriptorImpl(scopeName, parentNode);
    if (scopeName != null) {
      rulesMap.put(scopeName, result);
    }
    for (Map.Entry<String, PListValue> entry : plist.entries()) {
      PListValue pListValue = entry.getValue();
      if (pListValue != null) {
        String key = entry.getKey();
        Constants.StringKey stringKey = Constants.StringKey.fromName(key);
        if (stringKey != null) {
          String stringValue = pListValue.getString();
          if (stringValue != null) {
            result.setStringAttribute(stringKey, interner.intern(stringValue));
          }
          continue;
        }
        Constants.CaptureKey captureKey = Constants.CaptureKey.fromName(key);
        if (captureKey != null) {
          result.setCaptures(captureKey, loadCaptures(pListValue.getPlist(), result, interner));
          continue;
        }
        if (Constants.REPOSITORY_KEY.equalsIgnoreCase(key)) {
          loadRepository(result, pListValue, interner);
        }
        else if (Constants.PATTERNS_KEY.equalsIgnoreCase(key)) {
          loadPatterns(result, pListValue, interner);
        }
        else if (Constants.INJECTIONS_KEY.equalsIgnoreCase(key)) {
          loadInjections(result, pListValue, interner);
        }
      }
    }
    result.compact();
    return result;
  }

  @Nullable
  @SuppressWarnings("SSBasedInspection")
  private TextMateCapture @Nullable [] loadCaptures(@NotNull Plist captures,
                                                    @NotNull SyntaxNodeDescriptor parentNode,
                                                    @NotNull Interner<CharSequence> interner) {
    Int2ObjectOpenHashMap<TextMateCapture> map = new Int2ObjectOpenHashMap<>();
    int maxGroupIndex = -1;
    for (Map.Entry<String, PListValue> capture : captures.entries()) {
      try {
        int index = Integer.parseInt(capture.getKey());
        Plist captureDict = capture.getValue().getPlist();
        PListValue captureName = captureDict.getPlistValue(Constants.NAME_KEY);
        if (captureName != null) {
          map.put(index, new TextMateCapture.Name(interner.intern(captureName.getString())));
        }
        else {
          map.put(index, new TextMateCapture.Rule(loadRealNode(captureDict, parentNode, interner)));
        }
        maxGroupIndex = Math.max(maxGroupIndex, index);
      }
      catch (NumberFormatException ignore) {
      }
    }
    if (maxGroupIndex < 0 || map.isEmpty()) {
      return null;
    }
    TextMateCapture[] result = new TextMateCapture[maxGroupIndex + 1];
    map.int2ObjectEntrySet().fastForEach(e -> result[e.getIntKey()] = e.getValue());
    return result;
  }

  private SyntaxNodeDescriptor loadProxyNode(@NotNull Plist plist,
                                             @NotNull SyntaxNodeDescriptor result,
                                             @NotNull Interner<CharSequence> interner) {
    String include = plist.getPlistValue(Constants.INCLUDE_KEY, "").getString();
    if (!include.isEmpty() && include.charAt(0) == '#') {
      return new SyntaxRuleProxyDescriptor(getRuleId(include.substring(1)), result);
    }
    else if (Constants.INCLUDE_SELF_VALUE.equalsIgnoreCase(include) || Constants.INCLUDE_BASE_VALUE.equalsIgnoreCase(include)) {
      return new SyntaxRootProxyDescriptor(result);
    }
    int i = include.indexOf('#');
    CharSequence scope = i >= 0 ? include.subSequence(0, i) : include;
    String ruleId = i >= 0 ? include.substring(i + 1) : "";
    return new SyntaxScopeProxyDescriptor(interner.intern(scope), ruleId.isEmpty() ? -1 : getRuleId(ruleId), this, result);
  }

  private void loadPatterns(@NotNull MutableSyntaxNodeDescriptor result,
                            @NotNull PListValue pListValue,
                            @NotNull Interner<CharSequence> interner) {
    for (PListValue value : pListValue.getArray()) {
      result.addChild(loadNestedSyntax(value.getPlist(), result, interner));
    }
  }

  private void loadRepository(@NotNull MutableSyntaxNodeDescriptor result,
                              @NotNull PListValue pListValue,
                              @NotNull Interner<CharSequence> interner) {
    for (Map.Entry<String, PListValue> repoEntry : pListValue.getPlist().entries()) {
      PListValue repoEntryValue = repoEntry.getValue();
      if (repoEntryValue != null) {
        result.appendRepository(getRuleId(repoEntry.getKey()), loadNestedSyntax(repoEntryValue.getPlist(), result, interner));
      }
    }
  }

  private synchronized int getRuleId(@NotNull String ruleName) {
    if (ruleIds == null) {
      ruleIds = new Object2IntOpenHashMap<>();
    }
    int id = ruleIds.getInt(ruleName);
    if (id > 0) {
      return id;
    }
    int newId = ruleIds.size() + 1;
    ruleIds.put(ruleName, newId);
    return newId;
  }

  private void loadInjections(@NotNull MutableSyntaxNodeDescriptor result,
                              @NotNull PListValue pListValue,
                              @NotNull Interner<CharSequence> interner) {
    for (Map.Entry<String, PListValue> injectionEntry : pListValue.getPlist().entries()) {
      Plist injectionEntryValue = injectionEntry.getValue().getPlist();
      result.addInjection(new InjectionNodeDescriptor(injectionEntry.getKey(), loadRealNode(injectionEntryValue, result, interner)));
    }
  }
}
