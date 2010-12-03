/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intellij.tasks.actions;

import com.intellij.tasks.Task;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ide.BrowserUtil;

/**
 * @author Dmitry Avdeev
 */
public class TaskRenderer extends SimpleColoredComponent {

  public void setTask(Task task) {
    setIcon(task.getIcon());
    if (task.isIssue()) {
      final String url = task.getIssueUrl();
      if (url == null) {
        append(task.getId());
      } else {
        append(task.getId(), SimpleTextAttributes.LINK_ATTRIBUTES, new Runnable() {
          public void run() {
            BrowserUtil.launchBrowser(url);
          }
        });
      }
      append(": " + task.getSummary());
    } else {
      append(task.getSummary());
    }
  }
}
