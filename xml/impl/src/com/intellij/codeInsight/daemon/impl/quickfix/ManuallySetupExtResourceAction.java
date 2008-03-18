package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.javaee.ExternalResourceConfigurable;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;

/**
 * @author mike
 */
public class ManuallySetupExtResourceAction extends BaseExtResourceAction {

  protected String getQuickFixKeyId() {
    return "manually.setup.external.resource";
  }

  protected void doInvoke(final PsiFile file, final int offset, final String uri, final Editor editor) throws IncorrectOperationException {
    ExternalResourceManager.getInstance().addResource(uri,"");
    final ExternalResourceConfigurable component = ShowSettingsUtil.getInstance().findApplicationConfigurable(ExternalResourceConfigurable.class);
    ShowSettingsUtil.getInstance().editConfigurable(
      file.getProject(),
      component,
      new Runnable() {
        public void run() {
          component.selectResource(uri);
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return false;
  }
}
