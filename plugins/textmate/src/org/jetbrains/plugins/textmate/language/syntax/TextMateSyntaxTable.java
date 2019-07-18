package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.PListValue;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;
import org.joni.exception.JOniException;

import java.util.HashMap;
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
  private final Map<String, SyntaxNodeDescriptor> rulesMap = new HashMap<>();

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
    return result;
  }

  private SyntaxNodeDescriptor loadProxyNode(@NotNull Plist plist, @NotNull SyntaxNodeDescriptor result) {
    SyntaxNodeDescriptor rootNode = findRootNode(result);
    return new SyntaxProxyDescriptor(plist, result, rootNode, this);
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

  @NotNull
  private static SyntaxNodeDescriptor findRootNode(@NotNull SyntaxNodeDescriptor result) {
    SyntaxNodeDescriptor rootNode = result;
    SyntaxNodeDescriptor parentNode = result;
    while (parentNode != null) {
      rootNode = parentNode;
      parentNode = rootNode.getParentNode();
    }
    return rootNode;
  }
}
