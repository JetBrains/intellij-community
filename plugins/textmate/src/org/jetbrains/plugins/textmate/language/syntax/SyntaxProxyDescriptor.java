package org.jetbrains.plugins.textmate.language.syntax;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Syntax rule that represents include-rules.
 * In fact it is proxy for real syntax rule and it delegates all read-only methods
 * to appropriate real rule.
 * <p/>
 * User: zolotov
 */
class SyntaxProxyDescriptor implements SyntaxNodeDescriptor {
  private final String proxyName;

  private final SyntaxNodeDescriptor myParentNode;
  private final SyntaxNodeDescriptor myRootNode;
  private final TextMateSyntaxTable mySyntaxTable;
  private SyntaxNodeDescriptor myTargetNode;

  SyntaxProxyDescriptor(@NotNull final Plist plist,
                        @NotNull final SyntaxNodeDescriptor parentNode,
                        @NotNull final SyntaxNodeDescriptor rootNode,
                        @NotNull final TextMateSyntaxTable syntaxTable) {
    myRootNode = rootNode;
    mySyntaxTable = syntaxTable;
    proxyName = plist.getPlistValue(Constants.INCLUDE_KEY, "").getString();
    myParentNode = parentNode;
  }

  @Nullable
  @Override
  public String getStringAttribute(String key) {
    return getTargetNode().getStringAttribute(key);
  }

  @Nullable
  @Override
  public Plist getPlistAttribute(String key) {
    return getTargetNode().getPlistAttribute(key);
  }

  @Nullable
  @Override
  public RegexFacade getRegexAttribute(String key) {
    return getTargetNode().getRegexAttribute(key);
  }

  @NotNull
  @Override
  public List<SyntaxNodeDescriptor> getChildren() {
    return getTargetNode().getChildren();
  }

  @NotNull
  @Override
  public List<InjectionNodeDescriptor> getInjections() {
    return getTargetNode().getInjections();
  }

  @NotNull
  @Override
  public SyntaxNodeDescriptor findInRepository(String key) {
    return getTargetNode().findInRepository(key);
  }

  @NotNull
  @Override
  public String getScopeName() {
    return getTargetNode().getScopeName();
  }

  @Nullable
  @Override
  public SyntaxNodeDescriptor getParentNode() {
    return myParentNode;
  }

  @NotNull
  private SyntaxNodeDescriptor getTargetNode() {
    if (myTargetNode == null) {
      Set<SyntaxNodeDescriptor> visitedNodes = new HashSet<>();
      visitedNodes.add(this);
      SyntaxNodeDescriptor targetNode = computeTargetNode();
      while (targetNode instanceof SyntaxProxyDescriptor) {
        if (!visitedNodes.add(targetNode)) {
          targetNode = EMPTY_NODE;
          break;
        }
        targetNode = ((SyntaxProxyDescriptor)targetNode).computeTargetNode();
      }
      myTargetNode = targetNode;
    }
    return myTargetNode;
  }

  private SyntaxNodeDescriptor computeTargetNode() {
    if (proxyName.startsWith("#")) {
      return myParentNode.findInRepository(proxyName.substring(1));
    }
    else if (Constants.INCLUDE_SELF_VALUE.equalsIgnoreCase(proxyName)) {
      return myRootNode;
    }
    else if (Constants.INCLUDE_BASE_VALUE.equalsIgnoreCase(proxyName)) {
      return myRootNode;
    }
    else {
      return mySyntaxTable.getSyntax(proxyName);
    }
  }

  @Override
  public String toString() {
    return "Proxy rule for '" + proxyName + "'";
  }
}
