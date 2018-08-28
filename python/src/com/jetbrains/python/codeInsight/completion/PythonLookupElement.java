// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
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
  public void handleInsert(@NotNull InsertionContext context) {
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

  @Override
  public int compareTo(final LookupElement o) {
    return myLookupString.compareTo(o.getLookupString());
  }

}

