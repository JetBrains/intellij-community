package com.intellij.tasks.generic;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Editable variable which name can be used as placeholder and auto completed in EditorFields of
 * {@link GenericRepositoryEditor}. Variables is editable via {@link ManageTemplateVariablesDialog},
 * but if {@code shownOnFirstTab} property was set, it will also be shown on "General" tab among
 * standard fields like "Server URL", "Username" and "Password".
 *
 * @see GenericRepositoryEditor
 * @see ManageTemplateVariablesDialog
 *
 * @author evgeny.zakrevsky
 * @author Mikhail Golubev
 */
public class TemplateVariable {
  private String myName = "";
  private String myValue = "";
  private String myDescription = "";
  private boolean myReadOnly;
  private boolean myHidden;
  private boolean myShownOnFirstTab;

  public TemplateVariable(@NotNull @NonNls String name, @NotNull @NonNls String value) {
    myName = name;
    myValue = String.valueOf(value);
    myReadOnly = false;
    myDescription = "";
  }

  /**
   * Serialization constructor
   */
  @SuppressWarnings("unusedDesclaration")
  public TemplateVariable() {
    // empty
  }

  /**
   * Cloning constructor
   */
  private TemplateVariable(TemplateVariable other) {
    myName = other.getName();
    myValue = other.getValue();
    myDescription = other.getDescription();
    myHidden = other.isHidden();
    myReadOnly = other.isReadOnly();
    myShownOnFirstTab = other.isShownOnFirstTab();
  }

  public void setName(@NotNull @NonNls String name) {
    myName = name;
  }

  public void setValue(@NotNull @NonNls String value) {
    myValue = value;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getValue() {
    return myValue;
  }

  // TODO: actually not used in UI
  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@NotNull @NonNls String description) {
    myDescription = description;
  }

  @Attribute("readOnly")
  public boolean isReadOnly() {
    return myReadOnly;
  }

  public void setReadOnly(boolean readOnly) {
    myReadOnly = readOnly;
  }

  @Attribute("hidden")
  public boolean isHidden() {
    return myHidden;
  }

  public void setHidden(boolean hidden) {
    myHidden = hidden;
  }

  @Attribute("shownOnFirstTab")
  public boolean isShownOnFirstTab() {
    return myShownOnFirstTab;
  }

  public void setShownOnFirstTab(boolean shownOnFirstTab) {
    myShownOnFirstTab = shownOnFirstTab;
  }

  public TemplateVariable clone() {
    return new TemplateVariable(this);
  }

  @Override
  public String toString() {
    return String.format("TemplateVariable(name='%s', value='%s')", getName(), getValue());
  }

  /**
   * Represents predefined template variable such as "serverUrl", "login" or "password" which are not
   * set explicitly by user but instead taken from repository itself.
   *
   * @see GenericRepository
   */
  public abstract static class FactoryVariable extends TemplateVariable {

    protected FactoryVariable(@NotNull @NonNls String name) {
      this(name, false);
    }

    public FactoryVariable(@NotNull @NonNls String name, boolean hidden) {
      super(name, "");
      setHidden(hidden);
    }


    @NotNull
    @Override
    public abstract String getValue();

    @Override
    public final void setName(@NotNull String name) {
      throw new UnsupportedOperationException("Name of predefined variable can't be changed");
    }

    @Override
    public final void setValue(@NotNull String value) {
      throw new UnsupportedOperationException("Value of predefined variable can't be changed explicitly");
    }

    @Override
    public final void setShownOnFirstTab(boolean shownOnFirstTab) {
      throw new UnsupportedOperationException("This parameter can't be changed for predefined variable");
    }

    @Override
    public void setReadOnly(boolean readOnly) {
      throw new UnsupportedOperationException("This parameter can't be changed for predefined variable");
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }
  }

}
