/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath;

import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.TestOnly;

public class XPathSupportLoader {
  @TestOnly
  public static void createFileTypes() {
    FileTypeManager manager = FileTypeManager.getInstance();
    if (manager.getFileTypeByExtension(XPathFileType.XPATH.getDefaultExtension()) != XPathFileType.XPATH) {
      manager.registerFileType(XPathFileType.XPATH, XPathFileType.XPATH.getDefaultExtension());
      manager.registerFileType(XPathFileType.XPATH2, XPathFileType.XPATH2.getDefaultExtension());
    }
  }
}
