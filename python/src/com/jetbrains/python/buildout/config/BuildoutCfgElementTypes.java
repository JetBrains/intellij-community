/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.buildout.config;

import com.intellij.psi.tree.IFileElementType;

/**
 * @author traff
 */
public interface BuildoutCfgElementTypes {
  IFileElementType FILE = new IFileElementType(BuildoutCfgLanguage.INSTANCE);
  BuildoutCfgElementType ANY_ELEMENT = new BuildoutCfgElementType("ANY");

  BuildoutCfgElementType SECTION = new BuildoutCfgElementType("SECTION");
  BuildoutCfgElementType SECTION_HEADER = new BuildoutCfgElementType("SECTION_HEADER");
  BuildoutCfgElementType OPTION = new BuildoutCfgElementType("OPTION");
  BuildoutCfgElementType KEY = new BuildoutCfgElementType("KEY");
  BuildoutCfgElementType VALUE = new BuildoutCfgElementType("VALUE");
  BuildoutCfgElementType VALUE_LINE = new BuildoutCfgElementType("VALUE_LINE");
}
