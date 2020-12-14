package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

public class TextMateSnippet implements TextMateScopeSelectorOwner {
  private final String myKey;
  private final String myContent;
  private final CharSequence myScope;
  private final String myName;
  private final @Nls(capitalization = Nls.Capitalization.Sentence) String myDescription;
  private final String mySettingsId;

  public TextMateSnippet(@NotNull @NlsSafe String key, @NotNull String content, @NotNull CharSequence scope, @NotNull String name,
                         @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String description, @NotNull String settingsId) {
    myKey = key;
    myContent = content;
    myScope = scope;
    myName = name;
    myDescription = description;
    mySettingsId = settingsId;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  @NotNull
  public String getContent() {
    return myContent;
  }

  @NotNull
  @Override
  public CharSequence getScopeSelector() {
    return myScope;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getSettingsId() {
    return mySettingsId;
  }
}
