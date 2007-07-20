package com.intellij.ide.fileTemplates;

import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.velocity.runtime.parser.ParseException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * @author MYakovlev
 * Date: Jul 24, 2002
 */
public interface FileTemplate{
  @NonNls String ATTRIBUTE_EXCEPTION = "EXCEPTION";
  @NonNls String ATTRIBUTE_DESCRIPTION = "DESCRIPTION";
  @NonNls String ATTRIBUTE_DISPLAY_NAME = "DISPLAY_NAME";

  @NonNls String ATTRIBUTE_RETURN_TYPE = "RETURN_TYPE";
  @NonNls String ATTRIBUTE_DEFAULT_RETURN_VALUE = "DEFAULT_RETURN_VALUE";
  @NonNls String ATTRIBUTE_CALL_SUPER = "CALL_SUPER";

  @NonNls String ourEncoding = CharsetToolkit.UTF8;
  @NonNls String ATTRIBUTE_CLASS_NAME = "CLASS_NAME";
  @NonNls String ATTRIBUTE_METHOD_NAME = "METHOD_NAME";
  @NonNls String ATTRIBUTE_PACKAGE_NAME = "PACKAGE_NAME";
  @NonNls String ATTRIBUTE_NAME = "NAME";

  @NotNull String[] getUnsetAttributes(@NotNull Properties properties) throws ParseException;

  @NotNull String getName();

  void setName(@NotNull String name);

  boolean isJavaClassTemplate();

  boolean isDefault();

  @NotNull
  String getDescription();

  @NotNull
  String getText();

  void setText(String text);

  @NotNull
  String getText(Map attributes) throws IOException;

  @NotNull
  String getText(Properties attributes) throws IOException;

  @NotNull String getExtension();

  void setExtension(@NotNull String extension);

  boolean isAdjust();

  void setAdjust(boolean adjust);

  boolean isInternal();

}
