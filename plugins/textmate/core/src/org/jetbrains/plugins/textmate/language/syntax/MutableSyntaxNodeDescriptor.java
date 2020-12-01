package org.jetbrains.plugins.textmate.language.syntax;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;

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
public interface MutableSyntaxNodeDescriptor extends SyntaxNodeDescriptor {
  void addChild(SyntaxNodeDescriptor descriptor);

  void addInjection(@NotNull InjectionNodeDescriptor injection);

  void setStringAttribute(@NotNull Constants.StringKey key, @Nullable CharSequence value);

  void setCaptures(@NotNull Constants.CaptureKey key, @Nullable Int2ObjectMap<CharSequence> captures);

  void appendRepository(int ruleId, SyntaxNodeDescriptor descriptor);

  void setScopeName(@NotNull CharSequence scopeName);

  void compact();
}
