/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;

public interface PyDebugValueTransformer {
  ExtensionPointName<PyDebugValueTransformer> EP_NAME = ExtensionPointName.create("Pythonid.pyDebugValueTransformer");

  /**
   * This method is used in python debugger to modify content and presentation of stack frame nodes
   * @param children
   * @return modified children list
   */
  XValueChildrenList getTransformedChildren(@NotNull final XValueChildrenList children);
}
