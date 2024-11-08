package org.jetbrains.plugins.textmate.language.syntax;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;

import java.util.List;

/**
 * Syntax rule of languages from TextMate bundle.
 * Consists of:
 * <ul>
 * <li>
 * String attributes - string attributes of syntax node {@link Constants.StringKey}
 * </li>
 * <li>
 * Captures attributes - captures attributes of syntax node {@link Constants.CaptureKey}
 * </li>
 * <li>
 * Repository - set of named syntax rules (nodes) which can be included from other places in the grammar.
 * </li>
 * <li>
 * Children rules - set of nested syntax rules (from 'patterns' node)
 * </li>
 * </ul>
 * <p/>
 * User: zolotov
 */
public interface SyntaxNodeDescriptor {
  SyntaxNodeDescriptor EMPTY_NODE = new SyntaxNodeDescriptorImpl(null, null);

  @Nullable
  CharSequence getStringAttribute(@NotNull Constants.StringKey key);

  boolean hasBackReference(@NotNull Constants.StringKey key);

  TextMateCapture @Nullable [] getCaptureRules(@NotNull Constants.CaptureKey key);

  boolean hasBackReference(@NotNull Constants.CaptureKey key, int group);

  @NotNull
  List<SyntaxNodeDescriptor> getChildren();

  @NotNull
  List<InjectionNodeDescriptor> getInjections();

  @NotNull
  SyntaxNodeDescriptor findInRepository(int ruleId);

  /**
   * @return scope name if node is root for language or null otherwise
   */
  @Nullable
  CharSequence getScopeName();

  @Nullable
  SyntaxNodeDescriptor getParentNode();
}
