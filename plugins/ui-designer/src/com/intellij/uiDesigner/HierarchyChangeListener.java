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
package com.intellij.uiDesigner;

import java.util.EventListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public interface HierarchyChangeListener extends EventListener{
  /**
   * This event is fired each time when the something was changes inside component tree.
   * For example root container changes, or some undoable action has beed performed, etc.
   */
  void hierarchyChanged();
}
