package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.StringInterner;
import gnu.trove.THashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.Map;

/**
 * User: zolotov
 * <p/>
 * Table of textmate syntax rules.
 * Table represents mapping from scopeNames to set of syntax rules {@link SyntaxNodeDescriptor}.
 * <p/>
 * In order to lexing some file with this rules you should retrieve syntax rule
 * by scope name of target language {@link this#getSyntax(String)}.
 * <p/>
 * Scope name of target language can be find in syntax files of TextMate bundles.
 */
public class TextMateSyntaxTable {
  private static final Logger LOG = Logger.getInstance(TextMateSyntaxTable.class);
  private final Map<String, SyntaxNodeDescriptor> rulesMap = new THashMap<>();
  private TObjectIntHashMap<String> ruleIds;
  private StringInterner interner;

  public void compact() {
    ruleIds = null;
    interner = null;
  }

  /**
   * Append table with new syntax rules in order to support new language.
   *
   * @param plist Plist represented syntax file (*.tmLanguage) of target language.
   * @return language scope root name
   */
  @NotNull
  public String loadSyntax(Plist plist) {
    final SyntaxNodeDescriptor rootSyntaxNode = loadRealNode(plist, null);
    return rootSyntaxNode.getScopeName();
  }

  /**
   * Returns root syntax rule by scope name.
   *
   * @param scopeName Name of scope defined for some language.
   * @return root syntax rule from table for language with given scope name.
   * If tables doesn't contain syntax rule for given scope,
   * method returns {@link SyntaxNodeDescriptor#EMPTY_NODE}.
   */
  @NotNull
  public SyntaxNodeDescriptor getSyntax(String scopeName) {
    SyntaxNodeDescriptor syntaxNodeDescriptor = rulesMap.get(scopeName);
    if (syntaxNodeDescriptor == null) {
      LOG.debug("Can't find syntax node for scope: '" + scopeName + "'");
      return SyntaxNodeDescriptor.EMPTY_NODE;
    }
    return syntaxNodeDescriptor;
  }

  public void clear() {
    rulesMap.clear();
  }

  private SyntaxNodeDescriptor loadNestedSyntax(Plist plist, SyntaxNodeDescriptor parentNode) {
    return plist.contains(Constants.INCLUDE_KEY) ? loadProxyNode(plist, parentNode) : loadRealNode(plist, parentNode);
  }

  @NotNull
  private SyntaxNodeDescriptor loadRealNode(Plist plist, SyntaxNodeDescriptor parentNode) {
    MutableSyntaxNodeDescriptor result = new SyntaxNodeDescriptorImpl(parentNode);
    for (Map.Entry<String, PListValue> entry : plist.entries()) {
      PListValue pListValue = entry.getValue();
      if (pListValue != null) {
        String key = intern(entry.getKey());
        if (ArrayUtil.contains(key, Constants.REGEX_KEY_NAMES)) {
          String pattern = pListValue.getString();
          if (pattern != null) {
            result.setRegexAttribute(key, RegexFacade.regex(pattern));
          }
        }
        else if (ArrayUtil.contains(key, Constants.STRING_KEY_NAMES)) {
          if (key.equals(Constants.WHILE_KEY) || key.equals(Constants.END_KEY)) {
            result.setStringAttribute(key, pListValue.getString());
          }
          else {
            result.setStringAttribute(key, intern(pListValue.getString()));
          }
        }
        else if (ArrayUtil.contains(key, Constants.CAPTURES_KEY_NAMES)) {
          result.setCaptures(key, loadCaptures(pListValue.getPlist()));
        }
        else if (Constants.REPOSITORY_KEY.equalsIgnoreCase(key)) {
          loadRepository(result, pListValue);
        }
        else if (Constants.PATTERNS_KEY.equalsIgnoreCase(key)) {
          loadPatterns(result, pListValue);
        }
        else if (Constants.INJECTIONS_KEY.equalsIgnoreCase(key)) {
          loadInjections(result, pListValue);
        }
      }
    }
    if (plist.contains(Constants.SCOPE_NAME_KEY)) {
      final String scopeName = plist.getPlistValue(Constants.SCOPE_NAME_KEY, Constants.DEFAULT_SCOPE_NAME).getString();
      result.setScopeName(scopeName);
      rulesMap.put(scopeName, result);
    }
    result.compact();
    return result;
  }

  @NotNull
  private String intern(String key) {
    if (interner == null) {
      interner = new StringInterner();
      for (String name : Constants.STRING_KEY_NAMES) {
        interner.intern(name);
      }
      for (String name : Constants.CAPTURES_KEY_NAMES) {
        interner.intern(name);
      }
      for (String name : Constants.REGEX_KEY_NAMES) {
        interner.intern(name);
      }
    }
    return interner.intern(key);
  }

  @Nullable
  private TIntObjectHashMap<String> loadCaptures(Plist captures) {
    TIntObjectHashMap<String> result = new TIntObjectHashMap<>();
    for (Map.Entry<String, PListValue> capture : captures.entries()) {
      try {
        int index = Integer.parseInt(capture.getKey());
        Plist captureDict = capture.getValue().getPlist();
        String captureName = captureDict.getPlistValue(Constants.NAME_KEY, "").getString();
        result.put(index, intern(captureName));
      }
      catch (NumberFormatException ignore) {
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    result.trimToSize();
    return result;
  }

  private SyntaxNodeDescriptor loadProxyNode(@NotNull Plist plist, @NotNull SyntaxNodeDescriptor result) {
    String include = plist.getPlistValue(Constants.INCLUDE_KEY, "").getString();
    if (StringUtil.startsWithChar(include, '#')) {
      return new SyntaxRuleProxyDescriptor(getRuleId(include.substring(1)), result);
    }
    else if (Constants.INCLUDE_SELF_VALUE.equalsIgnoreCase(include) || Constants.INCLUDE_BASE_VALUE.equalsIgnoreCase(include)) {
      return new SyntaxRootProxyDescriptor(result);
    }
    return new SyntaxScopeProxyDescriptor(include, this, result);
  }

  private void loadPatterns(MutableSyntaxNodeDescriptor result, PListValue pListValue) {
    for (PListValue value : pListValue.getArray()) {
      result.addChild(loadNestedSyntax(value.getPlist(), result));
    }
  }

  private void loadRepository(MutableSyntaxNodeDescriptor result, PListValue pListValue) {
    for (Map.Entry<String, PListValue> repoEntry : pListValue.getPlist().entries()) {
      PListValue repoEntryValue = repoEntry.getValue();
      if (repoEntryValue != null) {
        result.appendRepository(getRuleId(repoEntry.getKey()), loadNestedSyntax(repoEntryValue.getPlist(), result));
      }
    }
  }

  private int getRuleId(@NotNull String ruleName) {
    if (ruleIds == null) {
      ruleIds = new TObjectIntHashMap<>();
    }
    int id = ruleIds.get(ruleName);
    if (id > 0) {
      return id;
    }
    int newId = ruleIds.size() + 1;
    ruleIds.put(ruleName, newId);
    return newId;
  }

  private void loadInjections(MutableSyntaxNodeDescriptor result, PListValue pListValue) {
    for (Map.Entry<String, PListValue> injectionEntry : pListValue.getPlist().entries()) {
      Plist injectionEntryValue = injectionEntry.getValue().getPlist();
      result.addInjection(new InjectionNodeDescriptor(injectionEntry.getKey(), loadRealNode(injectionEntryValue, result)));
    }
  }
}
