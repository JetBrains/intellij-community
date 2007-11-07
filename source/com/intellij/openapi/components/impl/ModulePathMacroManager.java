package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ModulePathMacroManager extends BasePathMacroManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.ModulePathMacroManager");

  private Module myModule;


  public ModulePathMacroManager(final Module module) {
    myModule = module;
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    getExpandModuleHomeReplacements(result);
    result.putAll(super.getExpandMacroMap());
    return result;
  }

  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();
    getModuleHomeReplacements(result, false);
    result.putAll(super.getReplacePathMap());
    getModuleHomeReplacements(result, myModule.isSavePathsRelative());
    return result;
  }

  private void getExpandModuleHomeReplacements(ExpandMacroToPathMap result) {
    String moduleDir = getModuleDir(myModule.getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));

    getExpandModuleHomeReplacements(result, f, "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$");
  }

  private static void getExpandModuleHomeReplacements(ExpandMacroToPathMap result, File f, String macro) {
    if (f == null) return;

    getExpandModuleHomeReplacements(result, f.getParentFile(), macro + "/..");
    String path = PathMacroMap.quotePath(f.getAbsolutePath());
    String s = macro;

    if (StringUtil.endsWithChar(path, '/')) s += "/";

    result.put(s, path);
  }

  private void getModuleHomeReplacements(@NonNls ReplacePathToMacroMap result, final boolean addRelativePathMacros) {
    String moduleDir = getModuleDir(myModule.getModuleFilePath());
    if (moduleDir == null) return;

    File f = new File(moduleDir.replace('/', File.separatorChar));
    // [dsl]: Q?
    //if(!f.exists()) return;

    String macro = "$" + PathMacrosImpl.MODULE_DIR_MACRO_NAME + "$";
    boolean check = false;
    while (f != null) {
      @NonNls String path = PathMacroMap.quotePath(f.getAbsolutePath());
      String s = macro;

      if (StringUtil.endsWithChar(path, '/')) s += "/";
      if (path.equals("/")) break;

      putIfAbsent(result, "file://" + path, "file://" + s, check);
      putIfAbsent(result, "file:/" + path, "file:/" + s, check);
      putIfAbsent(result, "file:" + path, "file:" + s, check);
      putIfAbsent(result, "jar://" + path, "jar://" + s, check);
      putIfAbsent(result, "jar:/" + path, "jar:/" + s, check);
      putIfAbsent(result, "jar:" + path, "jar:" + s, check);
      if (!path.equalsIgnoreCase("e:/") && !path.equalsIgnoreCase("r:/") && !path.equalsIgnoreCase("p:/")) {
        putIfAbsent(result, path, s, check);
      }

      if (!addRelativePathMacros) break;
      macro += "/..";
      check = true;
      f = f.getParentFile();
    }
  }

  private static void putIfAbsent(final ReplacePathToMacroMap result, @NonNls final String pathWithPrefix, @NonNls final String substWithPrefix, final boolean check) {
    if (check && result.get(pathWithPrefix) != null) return;
    result.put(pathWithPrefix, substWithPrefix);
  }

  @Nullable
    private static String getModuleDir(String moduleFilePath) {
    String moduleDir = new File(moduleFilePath).getParent();
    if (moduleDir == null) return null;
    moduleDir = moduleDir.replace(File.separatorChar, '/');
    if (moduleDir.endsWith(":/")) {
      moduleDir = moduleDir.substring(0, moduleDir.length() - 1);
    }
    return moduleDir;
  }

}
