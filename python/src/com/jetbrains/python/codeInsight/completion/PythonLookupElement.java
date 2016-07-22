/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.BasicInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * TODO: Add description
 * User: dcheryasov
 * Date: Mar 2, 2010 5:05:24 PM
 */
public class PythonLookupElement extends LookupElement implements Comparable<LookupElement>{

  protected final String myLookupString;
  protected final String myTypeText;
  protected final boolean isBold;
  protected final Icon myIcon;
  private final Icon myTypeIcon;
  protected final String myTailText;
  protected InsertHandler<PythonLookupElement> myHandler;

  public PythonLookupElement(@NotNull final String lookupString,
                           @Nullable final String tailText,
                           @Nullable final String typeText, final boolean bold,
                           @Nullable final Icon icon,
                           @Nullable final Icon typeIcon,
                           @NotNull final InsertHandler<PythonLookupElement> handler) {
    myLookupString = lookupString;
    myTailText = tailText;
    myTypeText = typeText;
    isBold = bold;
    myIcon = icon;
    myTypeIcon = typeIcon;
    myHandler = handler;
  }

  public PythonLookupElement(@NotNull final String lookupString,
                           @Nullable final String tailText,
                           @Nullable final String typeText, final boolean bold,
                           @Nullable final Icon icon,
                           @Nullable final Icon typeIcon) {
    this(lookupString, tailText, typeText, bold, icon, typeIcon, new BasicInsertHandler<>());
  }

  public PythonLookupElement(
    @NotNull final String lookupString,
    final boolean bold,
    @Nullable final Icon icon
  ) {
    this(lookupString, null, null, bold, icon, null, new BasicInsertHandler<>());
  }

  @NotNull
  public String getLookupString() {
    return myLookupString;
  }

  @Nullable
  public String getTailText() {
    return !StringUtil.isEmpty(myTailText) ? myTailText : null;
  }

  @Nullable
  protected String getTypeText() {
    return !StringUtil.isEmpty(myTypeText) ? myTypeText : null;
  }

  public Icon getIcon() {
    return myIcon;
  }


  public Icon getTypeIcon() {
    return myTypeIcon;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    myHandler.handleInsert(context, this);
  }

  public void setHandler(InsertHandler<PythonLookupElement> handler) {
    myHandler = handler;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
    presentation.setItemTextBold(isBold);
    presentation.setTailText(getTailText());
    presentation.setTypeText(getTypeText(), getTypeIcon());
    presentation.setIcon(getIcon());
  }

  public int compareTo(final LookupElement o) {
    return myLookupString.compareTo(o.getLookupString());
  }

}

