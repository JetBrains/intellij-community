package com.jetbrains.python.sdk.flavors;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * @author traff
 */
public class MayaSdkFlavor extends PythonSdkFlavor {
  private MayaSdkFlavor() {
  }

  public static MayaSdkFlavor INSTANCE = new MayaSdkFlavor();

  public boolean isValidSdkPath(@NotNull File file) {
    return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("mayapy");
  }

  public String getVersionOption() {
    return "--version";
  }

  @NotNull
  @Override
  public String getName() {
    return "MayaPy";
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python; //TODO: maya icon
  }
}
