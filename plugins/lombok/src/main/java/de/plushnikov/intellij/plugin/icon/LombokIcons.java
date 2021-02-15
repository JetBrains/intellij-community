package de.plushnikov.intellij.plugin.icon;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public final class LombokIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, LombokIcons.class);
  }

  public static final Icon CLASS_ICON = load("/icons/nodes/lombokClass.svg");
  public static final Icon FIELD_ICON = load("/icons/nodes/lombokField.svg");
  public static final Icon METHOD_ICON = load("/icons/nodes/lombokMethod.svg");

  public static final Icon CONFIG_FILE_ICON = load("/icons/config.svg");
  public static final Icon LOMBOK_ICON = load("/icons/lombok.svg");
}
