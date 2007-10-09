/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import java.awt.*;

public interface NullableComponent {

  boolean isNull();

  class Check {

    private Check() {
    }

    public static boolean isNull(Component c) {
      if (c == null) return true;
      if (c instanceof NullableComponent) return ((NullableComponent)c).isNull();
      return false;
    }

    public static boolean isNullOrHidden(Component c) {
      if (c != null && !c.isShowing()) return true;
      return isNull(c);
    }

    public static boolean isNotNullAndVisible(Component c) {
      return !isNull(c) && c.isVisible();
    }
  }

}
