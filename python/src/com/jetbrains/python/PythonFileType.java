/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 27.05.2005
 * Time: 22:26:32
 * To change this template use File | Settings | File Templates.
 */
public class PythonFileType extends LanguageFileType {
  public static PythonFileType INSTANCE = new PythonFileType();

  private Icon _icon;

  public PythonFileType() {
    super(new PythonLanguage());
    _icon = IconLoader.getIcon("python.png");
  }

  @NotNull
  public String getName() {
    return "Python";
  }

  @NotNull
  public String getDescription() {
    return "Python script";
  }

  @NotNull
  public String getDefaultExtension() {
    return "py";
  }

  @NotNull
  public Icon getIcon() {
    return _icon;
  }
}
