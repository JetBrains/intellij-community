/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.ide.DataManager;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.xslt.psi.XsltElement;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.jetbrains.annotations.NotNull;

public class RenameVariableFix extends AbstractFix {
    private final XsltElement myElement;
    private final String myPlace;

    public RenameVariableFix(XmlTag tag, String place) {
        myElement = XsltElementFactory.getInstance().wrapElement(tag, XsltElement.class);
        myPlace = place;
    }

    @NotNull
    public String getText() {
        final String type = LanguageFindUsages.INSTANCE.forLanguage(myElement.getLanguage()).getType(myElement);
        return "Rename " + myPlace + " " + StringUtil.capitalize(type);
    }

    public boolean isAvailableImpl(@NotNull Project project, Editor editor, PsiFile file) {
        return myElement.isValid();
    }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Runnable runnable = new Runnable() {
      public void run() {
        RefactoringActionHandlerFactory.getInstance().createRenameHandler().invoke(project, new PsiElement[]{myElement},
                                                                                   DataManager.getInstance().getDataContext());
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
    }
  }

    protected boolean requiresEditor() {
        return false;
    }
}
