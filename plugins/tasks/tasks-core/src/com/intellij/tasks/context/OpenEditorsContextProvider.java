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

package com.intellij.tasks.context;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class OpenEditorsContextProvider extends WorkingContextProvider {

  private final FileEditorManagerImpl myFileEditorManager;

  public OpenEditorsContextProvider(FileEditorManager fileEditorManager) {
    myFileEditorManager = fileEditorManager instanceof FileEditorManagerImpl ? (FileEditorManagerImpl)fileEditorManager : null;
  }

  @NotNull
  @Override
  public String getId() {
    return "editors";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Open editors and positions";
  }

  public void saveContext(Element element) {
    if (myFileEditorManager != null) {
      myFileEditorManager.writeExternal(element);
    }
  }

  public void loadContext(Element element) {
    if (myFileEditorManager != null) {
      myFileEditorManager.readExternal(element);
      myFileEditorManager.getMainSplitters().openFiles();
    }
  }

  public void clearContext() {
    if (myFileEditorManager != null) {
      myFileEditorManager.closeAllFiles();
      myFileEditorManager.getMainSplitters().clear();
    }
  }
}
