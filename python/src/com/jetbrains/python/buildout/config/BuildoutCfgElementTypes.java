package com.jetbrains.python.buildout.config;

import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.yaml.YAMLElementType;
import org.jetbrains.yaml.YAMLLanguage;

/**
 * @author traff
 */
public interface BuildoutCfgElementTypes {
  IFileElementType FILE = new IFileElementType(BuildoutCfgLanguage.INSTANCE);
  BuildoutCfgElementType ANY_ELEMENT = new BuildoutCfgElementType("ANY");

  BuildoutCfgElementType SECTION = new BuildoutCfgElementType("SECTION");
  BuildoutCfgElementType SECTION_HEADER = new BuildoutCfgElementType("SECTION_HEADER");
  BuildoutCfgElementType KEY = new BuildoutCfgElementType("KEY");
  BuildoutCfgElementType VALUE = new BuildoutCfgElementType("VALUE");
}
