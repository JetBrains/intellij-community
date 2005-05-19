/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 08.05.2005
 * Time: 0:17:52
 */
package com.intellij.lang.properties;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public interface ResourceBundle {
  Icon ICON = PropertiesFileType.FILE_ICON;

  @NotNull List<VirtualFile> getPropertiesFiles();

  @NotNull String getBaseName();
}