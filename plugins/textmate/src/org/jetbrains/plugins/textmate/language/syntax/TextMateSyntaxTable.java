package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.TextMateInterner;
import org.jetbrains.plugins.textmate.plist.Plist;

import java.util.Set;

public class TextMateSyntaxTable extends TextMateSyntaxTableCore {
  /**
   * @deprecated use {@link #addSyntax(Plist, TextMateInterner)}
   * Append table with new syntax rules in order to support new language.
   *
   * @param plist Plist represented syntax file (*.tmLanguage) of target language.
   * @return language scope root name
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public CharSequence loadSyntax(Plist plist, @NotNull Interner<CharSequence> interner) {
    return addSyntax(plist, new TextMateInterner() {
      @Override
      public @NotNull CharSequence intern(@NotNull CharSequence name) {
        return interner.intern(name);
      }

      @Override
      public void clear() {
        interner.clear();
      }
    });
  }
}
