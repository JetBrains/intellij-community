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
package com.intellij.designer.componentTree;

import com.intellij.ui.SimpleTextAttributes;

/**
 * @author Alexander Lobas
 */
public interface AttributeWrapper {
  AttributeWrapper DEFAULT = new AttributeWrapper() {
    @Override
    public SimpleTextAttributes getAttribute(SimpleTextAttributes attributes) {
      return attributes;
    }
  };

  AttributeWrapper REGULAR = new AttributeWrapper() {
    @Override
    public SimpleTextAttributes getAttribute(SimpleTextAttributes attributes) {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
  };

  SimpleTextAttributes getAttribute(SimpleTextAttributes attributes);
}