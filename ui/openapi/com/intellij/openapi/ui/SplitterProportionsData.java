/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 19-Sep-2006
 * Time: 12:02:30
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.util.JDOMExternalizable;

import java.awt.*;

public interface SplitterProportionsData extends JDOMExternalizable {

  void saveSplitterProportions(Component root);

  void restoreSplitterProportions(Component root);

  void externalizeToDimensionService(String key);

  void externalizeFromDimensionService(String key);
}