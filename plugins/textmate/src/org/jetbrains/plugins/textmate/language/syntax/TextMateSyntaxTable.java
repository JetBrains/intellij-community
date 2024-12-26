package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateInterner;
import org.jetbrains.plugins.textmate.plist.Plist;

public class TextMateSyntaxTable extends TextMateSyntaxTableCore {
  /**
   * @deprecated use {@link #addSyntax(Plist, TextMateInterner)}
   * Append table with new syntax rules in order to support new language.
   *
   * @param plist Plist represented syntax file (*.tmLanguage) of target language.
   * @return language scope root name
   */
  @Deprecated(forRemoval = true)
  public @Nullable CharSequence loadSyntax(Plist plist, @NotNull Interner<CharSequence> interner) {
    return addSyntax(plist, new TextMateInterner() {
      @Override
      public @NotNull String intern(@NotNull String name) {
        return interner.intern(name).toString();
      }

      @Override
      public void clear() {
        interner.clear();
      }
    });
  }
}
