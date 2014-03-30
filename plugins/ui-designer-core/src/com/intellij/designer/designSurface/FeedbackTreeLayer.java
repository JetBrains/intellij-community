/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.designSurface;

import com.intellij.designer.model.RadComponent;

/**
 * @author Alexander Lobas
 */
public interface FeedbackTreeLayer {
  int INSERT_BEFORE = 0;
  int INSERT_AFTER = 1;
  int INSERT_SELECTION = 2;

  void mark(RadComponent component, int feedback);

  void unmark();

  boolean isBeforeLocation(RadComponent component, int x, int y);

  boolean isAfterLocation(RadComponent component, int x, int y);
}