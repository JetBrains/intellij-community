/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package myActions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public class GarbageCollectionAction extends AnAction {
  private ImageIcon myIcon;

  public GarbageCollectionAction() {
    super("GC", "Run garbage collection", null);
  }

  public void actionPerformed(AnActionEvent event) {
    System.gc();
  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
      if (myIcon == null) {
        java.net.URL resource = GarbageCollectionAction.class.getResource("/icons/garbage.png");
        myIcon = new ImageIcon(resource);
      }
      presentation.setIcon(myIcon);
    }
  }
}
