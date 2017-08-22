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
package com.jetbrains.rest.validation;

import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.psi.RestTitle;

public class RestTitleAnnotator extends RestAnnotator {
  @Override
  public void visitTitle(final RestTitle node) {
    final String name = node.getName();
    if (name == null) return;
    final String underline = node.getUnderline();
    final String overline = node.getOverline();
    if (underline != null && overline != null && overline.length() != underline.length()) {
      getHolder().createWarningAnnotation(node, RestBundle.message("ANN.title.length"));
    }
  }
}
