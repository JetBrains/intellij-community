/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 01.08.2007
*/
public class RncFileType extends LanguageFileType  {
  public static final String RNC_EXT = "rnc";

  private static FileType INSTANCE;

  private RncFileType() {
    super(RngCompactLanguage.INSTANCE);
  }

  @NotNull
  @NonNls
  public String getName() {
    return "RNG Compact";
  }

  @NotNull
  public String getDescription() {
    return "RELAX NG Compact Syntax";
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "rnc";
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.findIcon("/fileTypes/text.png");
  }

  public static synchronized FileType getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new RncFileType();
    }
    return INSTANCE;
  }

  public static class Factory extends FileTypeFactory {
    public void createFileTypes(@NotNull FileTypeConsumer fileTypeConsumer) {
      fileTypeConsumer.consume(getInstance(), RNC_EXT);
    }

    public void createFileTypes(@NotNull PairConsumer<FileType, String> consumer) {
      consumer.consume(getInstance(), RNC_EXT);
    }
  }
}