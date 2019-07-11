package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;
import org.joni.exception.JOniException;

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
        String key = entry.getKey();
        if (ArrayUtil.contains(key, Constants.REGEX_KEY_NAMES)) {
          try {
            String pattern = pListValue.getString();
            if (pattern != null) {
              result.setRegexAttribute(key, RegexFacade.regex(pattern));
            }
          }
          catch (JOniException e) {
            LOG.error("Cannot compile pattern '" + pListValue.getString() + "' for '" + key + "'", e);
          }
        }
        else if (ArrayUtil.contains(key, Constants.STRING_KEY_NAMES)) {
          result.setStringAttribute(key, pListValue.getString());
        }
        else if (ArrayUtil.contains(key, Constants.DICT_KEY_NAMES)) {
          result.setPlistAttribute(key, pListValue.getPlist());
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

  private SyntaxNodeDescriptor loadProxyNode(@NotNull Plist plist, @NotNull SyntaxNodeDescriptor result) {
    String include = plist.getPlistValue(Constants.INCLUDE_KEY, "").getString();
    if (StringUtil.startsWithChar(include, '#')) {
      // todo: convert to int
      return new SyntaxRuleProxyDescriptor(include.substring(1), result);
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
        // todo: convert to int
        result.appendRepository(repoEntry.getKey(), loadNestedSyntax(repoEntryValue.getPlist(), result));
      }
    }
  }

  private void loadInjections(MutableSyntaxNodeDescriptor result, PListValue pListValue) {
    for (Map.Entry<String, PListValue> injectionEntry : pListValue.getPlist().entries()) {
      Plist injectionEntryValue = injectionEntry.getValue().getPlist();
      result.addInjection(new InjectionNodeDescriptor(injectionEntry.getKey(), loadRealNode(injectionEntryValue, result)));
    }
  }
}
