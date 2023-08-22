package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class TextMateScope {
  public static final TextMateScope EMPTY = new TextMateScope(null, null);
  public static final TextMateScope WHITESPACE = EMPTY.add("token.whitespace");

  @Nullable
  private final CharSequence scopeName;

  @Nullable
  private final TextMateScope parentScope;

  private final int level;

  private final int dotsCount;

  private final int hashCode;

  private final boolean empty;

  public TextMateScope(@Nullable CharSequence scopeName,
                       @Nullable TextMateScope parentScope) {
    this.scopeName = scopeName;
    this.parentScope = parentScope;
    this.dotsCount = (scopeName != null ? Strings.countChars(scopeName, '.') : 0) +
                     (parentScope != null ? parentScope.dotsCount : 0);
    this.hashCode = Objects.hash(scopeName, parentScope);
    this.empty = (parentScope == null || parentScope.isEmpty()) && (scopeName == null || scopeName.isEmpty());
    this.level = parentScope != null ? parentScope.level + 1 : 0;
  }

  public int getDotsCount() {
    return dotsCount;
  }

  public int getLevel() {
    return level;
  }

  public TextMateScope add(@Nullable CharSequence scopeName) {
    return new TextMateScope(scopeName, this);
  }

  @Nullable
  public CharSequence getScopeName() {
    return scopeName;
  }

  @Nullable
  public TextMateScope getParent() {
    return parentScope;
  }

  @NotNull
  public TextMateScope getParentOrSelf() {
    return parentScope != null ? parentScope : this;
  }

  public boolean isEmpty() {
    return empty;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (scopeName != null) {
      builder.append(scopeName);
    }

    TextMateScope parent = parentScope;
    while (parent != null) {
      CharSequence parentScopeName = parent.scopeName;
      if (parentScopeName != null) {
        if (!builder.isEmpty()) {
          builder.insert(0, " ");
        }
        builder.insert(0, parentScopeName);
      }
      parent = parent.parentScope;
    }
    return builder.toString().trim();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TextMateScope scope = (TextMateScope)o;
    return level == scope.level && hashCode == scope.hashCode && Objects.equals(scopeName, scope.scopeName);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
}
