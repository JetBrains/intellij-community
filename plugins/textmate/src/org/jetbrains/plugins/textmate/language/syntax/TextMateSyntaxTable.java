package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateInterner;
import org.jetbrains.plugins.textmate.plist.Plist;

/**
 * @deprecated use {@link TextMateSyntaxTableBuilder}
 */
@Deprecated(forRemoval = true)
public class TextMateSyntaxTable {
  @Nullable
  private volatile TextMateSyntaxTableBuilder myIncompleteBuilder = null;

  @Nullable
  private volatile TextMateSyntaxTableCore mySyntaxTable = null;

  /**
   * @deprecated use {@link TextMateSyntaxTableBuilder#addSyntax(Plist)}
   * Append table with new syntax rules in order to support new language.
   *
   * @param plist Plist represented syntax file (*.tmLanguage) of target language.
   * @return language scope root name
   */
  @Deprecated(forRemoval = true)
  public @Nullable CharSequence loadSyntax(Plist plist, @NotNull Interner<CharSequence> interner) {
    TextMateSyntaxTableBuilder builder;
    synchronized (this) {
      TextMateSyntaxTableBuilder incompleteBuilder = myIncompleteBuilder;
      if (incompleteBuilder == null) {
        builder = new TextMateSyntaxTableBuilder(new TextMateInterner() {
          @Override
          public @NotNull String intern(@NotNull String name) {
            return interner.intern(name).toString();
          }

          @Override
          public void clear() {
            interner.clear();
          }
        });
        myIncompleteBuilder = builder;
      }
      else {
        builder = incompleteBuilder;
      }
      mySyntaxTable = null;
    }

    return builder.addSyntax(plist);
  }

  /**
   * @deprecated use {@link TextMateSyntaxTableBuilder}
   *
   * Returns root syntax rule by scope name.
   *
   * @param scopeName Name of scope defined for some language.
   * @return root syntax rule from table for language with a given scope name.
   * If tables don't contain syntax rule for given scope,
   * method returns {@link SyntaxNodeDescriptor#EMPTY_NODE}.
   */
  @Deprecated(forRemoval = true)
  public @NotNull SyntaxNodeDescriptor getSyntax(CharSequence scopeName) {
    TextMateSyntaxTableCore actualSyntaxTable = mySyntaxTable;
    if (actualSyntaxTable != null) {
      return actualSyntaxTable.getSyntax(scopeName);
    }
    else {
      TextMateSyntaxTableBuilder incompleteBuilder = myIncompleteBuilder;
      if (incompleteBuilder != null) {
        TextMateSyntaxTableCore syntaxTable = incompleteBuilder.build();
        mySyntaxTable = syntaxTable;
        return syntaxTable.getSyntax(scopeName);
      }
      else {
        return SyntaxNodeDescriptor.EMPTY_NODE;
      }
    }
  }
}
