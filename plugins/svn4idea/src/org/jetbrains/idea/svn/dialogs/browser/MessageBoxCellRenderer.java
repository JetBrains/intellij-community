/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.idea.svn.dialogs.browser;

import javax.swing.*;
import java.awt.*;

class MessageBoxCellRenderer extends DefaultListCellRenderer {

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value != null) {
      String message = (String) value;
      message = message.replace('\r', '|');
      message = message.replace('\n', '|');
      if (message.length() > 50) {
        message = message.substring(0, 50) + "[...]";
      }
      value = message;
    }
    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
  }
}
