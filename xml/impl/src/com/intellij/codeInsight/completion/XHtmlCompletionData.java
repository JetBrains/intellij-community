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
package com.intellij.codeInsight.completion;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 28.11.2004
 * Time: 0:23:35
 * To change this template use File | Settings | File Templates.
 */
public class XHtmlCompletionData extends HtmlCompletionData {
  public XHtmlCompletionData() {
    super(false);
  }

  protected boolean isCaseInsensitive() {
    return false;
  }
}
