// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.javaee.ExternalResourceConfigurable;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public class IgnoreExtResourceAction extends BaseExtResourceAction {

  private static final String KEY = "xml.intention.ignore.external.resource.text";

  @Override
  protected String getQuickFixKeyId() {
    return KEY;
  }

  @Override
  protected void doInvoke(final @NotNull PsiFile psiFile, final int offset, final @NotNull String uri, final Editor editor) throws IncorrectOperationException {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(Collections.singletonList(uri), null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    int offset = editor.getCaretModel().getOffset();
    final String uri = findUri(psiFile, offset);
    if (uri == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    String pathToConfigurable = ConfigurableExtensionPointUtil.getConfigurablePath(ExternalResourceConfigurable.class, project);
    String text = new HtmlBuilder().wrapWithHtmlBody()
      .child(HtmlChunk.body().child(HtmlChunk.template(XmlBundle.message("ignore.ext.resource.preview", "$uri$", "$path$"),
                                                       Map.entry("uri", HtmlChunk.link("uri", uri)),
                                                       Map.entry("path", HtmlChunk.text(pathToConfigurable).italic())))).toString();
    return new IntentionPreviewInfo.Html(text);
  }
  
  

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
