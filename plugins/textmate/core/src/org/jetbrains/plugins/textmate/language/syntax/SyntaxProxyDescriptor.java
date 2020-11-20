package org.jetbrains.plugins.textmate.language.syntax;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Syntax rule that represents include-rules.
 * In fact it is proxy for real syntax rule and it delegates all read-only methods
 * to appropriate real rule.
 * <p/>
 */
abstract class SyntaxProxyDescriptor implements SyntaxNodeDescriptor {
  private final SyntaxNodeDescriptor myParentNode;
  private SyntaxNodeDescriptor myTargetNode;

  SyntaxProxyDescriptor(@NotNull final SyntaxNodeDescriptor parentNode) {
    myParentNode = parentNode;
  }

  @Nullable
  @Override
  public CharSequence getStringAttribute(@NotNull Constants.StringKey key) {
    return getTargetNode().getStringAttribute(key);
  }

  @Nullable
  @Override
  public Int2ObjectMap<CharSequence> getCaptures(@NotNull Constants.CaptureKey key) {
    return getTargetNode().getCaptures(key);
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
  public SyntaxNodeDescriptor findInRepository(int ruleId) {
    return getTargetNode().findInRepository(ruleId);
  }

  @NotNull
  @Override
  public CharSequence getScopeName() {
    return getTargetNode().getScopeName();
  }

  @NotNull
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

  protected abstract SyntaxNodeDescriptor computeTargetNode();
}
