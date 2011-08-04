/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.impl;

import com.intellij.tasks.Comment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Dennis.Ushakov
 */
public class SimpleComment extends Comment {
  private final String myText;
  private final String myAuthor;
  private final Date myDate;

  public SimpleComment(@Nullable Date date, @Nullable String author, @NotNull String text) {
    myDate = date;
    myAuthor = author;
    myText = text;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public String getAuthor() {
    return myAuthor;
  }

  @Override
  public Date getDate() {
    return myDate;
  }
}
