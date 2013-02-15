/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable;

import com.intellij.openapi.actionSystem.AnAction;

/**
 * @author Alexander Lobas
 */
public interface TablePanelActionPolicy {
  TablePanelActionPolicy EMPTY = new TablePanelActionPolicy() {
    @Override
    public boolean showAction(AnAction action) {
      return false;
    }
  };

  TablePanelActionPolicy ALL = new TablePanelActionPolicy() {
    @Override
    public boolean showAction(AnAction action) {
      return true;
    }
  };

  boolean showAction(AnAction action);
}