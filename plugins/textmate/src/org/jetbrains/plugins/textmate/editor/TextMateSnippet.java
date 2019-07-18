package org.jetbrains.plugins.textmate.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.TextMateScopeSelectorOwner;

public class TextMateSnippet implements TextMateScopeSelectorOwner {
  private final String myKey;
  private final String myContent;
  private final String myScope;
  private final String myName;
  private final String myDescription;
  private final String mySettingsId;

  public TextMateSnippet(@NotNull String key, @NotNull String content, @NotNull String scope, @NotNull String name, 
                         @NotNull String description, @NotNull String settingsId) {
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
  public String getScopeSelector() {
    return myScope;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  public String getSettingsId() {
    return mySettingsId;
  }
}
