package com.intellij.tasks.generic;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: evgeny.zakrevsky
 * Date: 10/26/12
 */
public class TemplateVariable {
  private String myName;
  private String myValue = "";
  private String myDescription;
  private boolean myIsPredefined;
  private boolean myIsHidden;
  private boolean myIsShownOnFirstTab;

  public static TemplateVariableBuilder builder(String name) {
    return new TemplateVariableBuilder(name);
  }

  private TemplateVariable(TemplateVariableBuilder builder) {
    myName = builder.myName;
    myValue = builder.myValue;
    myDescription = builder.myDescription;
    myIsHidden = builder.myIsHidden;
    myIsShownOnFirstTab = builder.myIsShowOnFirstTab;
    myIsPredefined = builder.myIsPredefined;
  }

  public TemplateVariable(String name, Object value) {
    this(name, value, false, "");
  }

  public TemplateVariable(@NotNull @NonNls String name, @NotNull @NonNls Object value, boolean isPredefined, @Nullable String description) {
    myName = name;
    myValue = String.valueOf(value);
    myIsPredefined = isPredefined;
    myDescription = description;
  }

  /**
   * Serialization constructor
   */
  public TemplateVariable() {
  }


  /**
   * Cloning constructor
   */
  private TemplateVariable(TemplateVariable other) {
    myName = other.getName();
    myValue = other.getValue();
    myDescription = other.getDescription();
    myIsHidden = other.getIsHidden();
    myIsPredefined = other.getIsPredefined();
    myIsShownOnFirstTab = other.getIsShownOnFirstTab();
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

  @Attribute("isPredefined")
  public boolean getIsPredefined() {
    return myIsPredefined;
  }

  public void setIsPredefined(boolean isPredefined) {
    myIsPredefined = isPredefined;
  }

  @Attribute("isHidden")
  public boolean getIsHidden() {
    return myIsHidden;
  }

  public void setIsHidden(boolean isHidden) {
    myIsHidden = isHidden;
  }

  @Attribute("shownOnFirstTab")
  public boolean getIsShownOnFirstTab() {
    return myIsShownOnFirstTab;
  }

  public void setIsShownOnFirstTab(boolean isShownOnFirstTab) {
    myIsShownOnFirstTab = isShownOnFirstTab;
  }

  public TemplateVariable clone() {
    return new TemplateVariable(this);
  }

  public void setDescription(final String description) {
    myDescription = description;
  }

  @Override
  public String toString() {
    return String.format("TemplateVariable(name='%s', value='%s')", getName(), getValue());
  }

  public static class TemplateVariableBuilder {
    private String myName;
    private String myValue = "";
    private String myDescription;
    private boolean myIsHidden;
    private boolean myIsPredefined;
    private boolean myIsShowOnFirstTab;

    private TemplateVariableBuilder(String name) {
      myName = name;
    }

    public TemplateVariableBuilder value(Object value) {
      myValue = String.valueOf(value);
      return this;
    }

    public TemplateVariableBuilder description(String description) {
      myDescription = description;
      return this;
    }

    public TemplateVariableBuilder isHidden(boolean isHidden) {
      myIsHidden = isHidden;
      return this;
    }

    public TemplateVariableBuilder isPredefined(boolean isPredefined) {
      myIsPredefined = isPredefined;
      return this;
    }

    public TemplateVariableBuilder isShownOnFirstTab(boolean isShowOnFirstTab) {
      myIsShowOnFirstTab = isShowOnFirstTab;
      return this;
    }

    public TemplateVariable build() {
      return new TemplateVariable(this);
    }
  }

  /**
   * Represents predefined template variable such as "serverUrl", "login" or "password" which are not
   * set explicitly by user but instead taken from repository itself.
   */
  public abstract static class PredefinedFactoryVariable extends TemplateVariable {

    protected PredefinedFactoryVariable(String name) {
      this(name, false);
    }

    public PredefinedFactoryVariable(String name, boolean isHidden) {
      this(name, name, isHidden);
    }

    public PredefinedFactoryVariable(String name, String description, boolean isHidden) {
      super(builder(name).description(description).isHidden(isHidden));
    }

    @Override
    public abstract String getValue();

    @Override
    public final void setName(String name) {
      throw new UnsupportedOperationException("Name of predefined variable can't be changed");
    }

    @Override
    public final void setValue(String value) {
      throw new UnsupportedOperationException("Value of predefined variable can't be changed explicitly");
    }

    @Override
    public final void setIsShownOnFirstTab(boolean isShownOnFirstTab) {
      throw new UnsupportedOperationException("This parameter can't be changed for predefined variable");
    }

    @Override
    public void setIsPredefined(boolean isPredefined) {
      throw new UnsupportedOperationException("This parameter can't be changed for predefined variable");
    }

    @Override
    public boolean getIsPredefined() {
      return true;
    }
  }

}
