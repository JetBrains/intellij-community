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
package com.intellij.spellchecker.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;

import java.io.File;


@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class SPFileUtil {

  public static void processFilesRecursively(final String rootPath, final Consumer<String> consumer){
    final File rootFile = new File(rootPath);
    if (rootFile.exists() && rootFile.isDirectory()){
      FileUtil.processFilesRecursively(rootFile, new Processor<File>() {
        public boolean process(final File file) {
          if (!file.isDirectory()){
            final String path = file.getPath();
            if (path.endsWith(".dic")){
              consumer.consume(path);
            }
          }
          return true;
        }
      });
    }
  }
}
