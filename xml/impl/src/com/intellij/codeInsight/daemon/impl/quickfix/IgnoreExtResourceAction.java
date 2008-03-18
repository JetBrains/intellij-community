package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class IgnoreExtResourceAction extends BaseExtResourceAction {
  protected String getQuickFixKeyId() {
    return "ignore.external.resource.text";
  }

  protected void doInvoke(final PsiFile file, final int offset, final String uri, final Editor editor) throws IncorrectOperationException {
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResource(uri);
  }
}
