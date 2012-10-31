package com.intellij.tasks.generic;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
public class TemplateVariable {
  private String myName;
  private String myValue;
  private boolean myIsPredefined;
  private String myDescription;

  public TemplateVariable(@NotNull @NonNls String name, @NotNull @NonNls String value, boolean isPredefined, @Nullable String description) {
    myName = name;
    myValue = value;
    myIsPredefined = isPredefined;
    myDescription = description;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setValue(String value) {
    myValue = value;
  }

  public String getName() {
    return myName;
  }

  public String getValue() {
    return myValue;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public boolean getIsPredefined() {
    return myIsPredefined;
  }

  public TemplateVariable clone() {
    return new TemplateVariable(myName, myValue, myIsPredefined, myDescription);
  }

  public void setDescription(final String description) {
    myDescription = description;
  }
}
