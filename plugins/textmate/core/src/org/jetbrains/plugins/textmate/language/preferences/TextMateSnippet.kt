package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

public final class TextMateSnippet implements TextMateScopeSelectorOwner {
  private final String myKey;
  private final String myContent;
  private final CharSequence myScope;
  private final String myName;
  private final @Nls(capitalization = Nls.Capitalization.Sentence) String myDescription;
  private final String mySettingsId;

  public TextMateSnippet(@NotNull String key, @NotNull String content, @NotNull CharSequence scope, @NotNull String name,
                         @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String description, @NotNull String settingsId) {
    myKey = key;
    myContent = content;
    myScope = scope;
    myName = name;
    myDescription = description;
    mySettingsId = settingsId;
  }

  public @NotNull String getKey() {
    return myKey;
  }

  public @NotNull String getContent() {
    return myContent;
  }

  @Override
  public @NotNull CharSequence getScopeSelector() {
    return myScope;
  }

  public @NotNull String getName() {
    return myName;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return myDescription;
  }

  public @NotNull String getSettingsId() {
    return mySettingsId;
  }
}
