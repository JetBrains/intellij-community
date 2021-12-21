/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.openapi.editor.impl.AbstractRtlTest;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ui.UIUtil;

import java.nio.charset.StandardCharsets;

public class PropertiesRtlTest extends AbstractRtlTest {
  public void testComment() {
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8);
    UIUtil.dispatchAllInvocationEvents();
    checkBidiRunBoundaries("# |R", "properties");
  }
}
