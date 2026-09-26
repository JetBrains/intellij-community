// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.emmet.rpc.EmmetAbbreviationBaloonTopic;
import com.intellij.codeInsight.template.emmet.rpc.ShowAbbreviationBaloonUiEvent;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.impl.EditorIdKt;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.LinkLabel;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ContextHelpLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Supplier;

import static com.intellij.util.ObjectUtils.doIfNotNull;

public class EmmetAbbreviationBalloon {
  private final String myAbbreviationsHistoryKey;
  private final String myLastAbbreviationKey;
  private final Callback myCallback;
  private final @NotNull EmmetContextHelp myContextHelp;

  private static @Nullable String ourTestingAbbreviation;


  public EmmetAbbreviationBalloon(@NotNull String abbreviationsHistoryKey,
                                  @NotNull String lastAbbreviationKey,
                                  @NotNull Callback callback,
                                  @NotNull EmmetContextHelp contextHelp) {
    myAbbreviationsHistoryKey = abbreviationsHistoryKey;
    myLastAbbreviationKey = lastAbbreviationKey;
    myCallback = callback;
    myContextHelp = contextHelp;
  }


  @TestOnly
  public static void setTestingAbbreviation(@NotNull String testingAbbreviation, @NotNull Disposable parentDisposable) {
    ourTestingAbbreviation = testingAbbreviation;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourTestingAbbreviation = null;
      }
    });
  }

  public void show(final @NotNull CustomTemplateCallback customTemplateCallback) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (ourTestingAbbreviation == null) {
        throw new RuntimeException("Testing abbreviation is not set. See EmmetAbbreviationBalloon#setTestingAbbreviation");
      }
      myCallback.onEnter(ourTestingAbbreviation);
      return;
    }

    var invocationEvent = new ShowAbbreviationBaloonUiEvent(
      EmmetAbbreviationBaloonTopic.nextTransactionId(),
      EditorIdKt.editorId(customTemplateCallback.getEditor()),
      myAbbreviationsHistoryKey,
      myLastAbbreviationKey,
      myContextHelp.getLinkText(),
      myContextHelp.getLinkUrl(),
      myContextHelp.getDescription());

    EmmetAbbreviationBaloonTopic.invokeUi(invocationEvent, myCallback, customTemplateCallback);
  }

  public static class EmmetContextHelp {
    private final @NotNull Supplier<@NotNull @Tooltip String> myDescription;

    private @Nullable Supplier<@Nullable @LinkLabel String> myLinkText = null;

    private @Nullable String myLinkUrl = null;

    public EmmetContextHelp(@NotNull Supplier<@Tooltip String> description) {
      myDescription = description;
    }

    public EmmetContextHelp(@NotNull Supplier<@Tooltip String> description,
                            @NotNull Supplier<@LinkLabel String> linkText,
                            @NotNull String linkUrl) {
      myDescription = description;
      myLinkText = linkText;
      myLinkUrl = linkUrl;
    }

    public static @NotNull ContextHelpLabel createHelpLabel(@Nullable @Tooltip String linkText,
                                                            @Nullable String linkUrl,
                                                            @NotNull @Tooltip String description) {
      if (StringUtil.isEmpty(linkText) || StringUtil.isEmpty(linkUrl)) {
        return ContextHelpLabel.create(description);
      }
      return ContextHelpLabel.createWithLink(null, description, linkText, () -> BrowserUtil.browse(linkUrl));
    }

    private @Nullable @Tooltip String getLinkText() {
      return doIfNotNull(myLinkText, Supplier::get);
    }

    private @NotNull @Tooltip String getDescription() {
      return myDescription.get();
    }

    public @Nullable String getLinkUrl() {
      return myLinkUrl;
    }
  }

  public interface Callback {
    void onEnter(@NotNull String abbreviation);
  }
}