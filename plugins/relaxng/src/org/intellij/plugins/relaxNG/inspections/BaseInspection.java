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

package org.intellij.plugins.relaxNG.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.SuppressIntentionAction;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.11.2007
 */
public abstract class BaseInspection extends XmlSuppressableInspectionTool {
  @Nls
  @NotNull
  public final String getGroupDisplayName() {
    return getRngGroupDisplayName();
  }

  public static String getRngGroupDisplayName() {
    return "RELAX NG";
  }

  @SuppressWarnings({ "SSBasedInspection" })
  @Override
  public boolean isSuppressedFor(PsiElement element) {
    if (element.getContainingFile() instanceof RncFile) {
      final RncDefine define = PsiTreeUtil.getParentOfType(element, RncDefine.class, false);
      if (define != null) {
        if (isSuppressedAt(define)) return true;
      }

      final RncGrammar grammar = PsiTreeUtil.getParentOfType(define, RncGrammar.class);
      if (grammar != null) {
        if (isSuppressedAt(grammar)) return true;
      }

      return false;
    } else {
      return super.isSuppressedFor(element);
    }
  }

  @SuppressWarnings({ "SSBasedInspection" })
  private boolean isSuppressedAt(RncElement location) {
    PsiElement prev = location.getPrevSibling();
    while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment) {
      if (prev instanceof PsiComment) {
        @NonNls String text = prev.getText();
        if (text.matches("\n*#\\s*suppress\\s.+") && (text.contains(getID()) || "ALL".equals(text))) return true;
      }
      prev = prev.getPrevSibling();
    }
    return false;
  }

  @Override
  @Nullable
  public SuppressIntentionAction[] getSuppressActions(PsiElement element) {
    if (element.getContainingFile() instanceof RncFile) {
      return ArrayUtil.mergeArrays(new SuppressIntentionAction[]{
              new SuppressAction("Define") {
                protected PsiElement getTarget(PsiElement element) {
                  return PsiTreeUtil.getParentOfType(element, RncDefine.class, false);
                }
              },
              new SuppressAction("Grammar") {
                protected PsiElement getTarget(PsiElement element) {
                  final RncDefine define = PsiTreeUtil.getParentOfType(element, RncDefine.class, false);
                  return define != null ? PsiTreeUtil.getParentOfType(define, RncGrammar.class, false) : null;
                }

                @SuppressWarnings({ "SSBasedInspection" })
                public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
                  return super.isAvailable(project, editor, element) && getTarget(element).getText().startsWith("grammar ");
                }
              }
      }, getXmlOnlySuppressions(element), SuppressIntentionAction.class);
    } else {
      return super.getSuppressActions(element);
    }
  }

  private SuppressIntentionAction[] getXmlOnlySuppressions(PsiElement element) {
    return ContainerUtil.map(super.getSuppressActions(element), new Function<SuppressIntentionAction, SuppressIntentionAction>() {
      public SuppressIntentionAction fun(final SuppressIntentionAction action) {
        return new SuppressIntentionAction() {
          public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
            action.invoke(project, editor, element);
          }

          public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
            return element != null && element.getContainingFile().getFileType() == StdFileTypes.XML &&
                    action.isAvailable(project, editor, element);
          }

          @NotNull
          public String getText() {
            return action.getText();
          }

          public boolean startInWriteAction() {
            return action.startInWriteAction();
          }

          @NotNull
          public String getFamilyName() {
            return action.getFamilyName();
          }
        };
      }
    }, new SuppressIntentionAction[0]);
  }

  private void suppress(PsiFile file, @NotNull PsiElement location) {
    suppress(file, location, "#suppress " + getID(), new Function<String, String>() {
      public String fun(final String text) {
        return text + ", " + getID();
      }
    });
  }

  @SuppressWarnings({ "SSBasedInspection" })
  private static void suppress(PsiFile file, @NotNull PsiElement location, String suppressComment, Function<String, String> replace) {
    final Project project = file.getProject();
    final VirtualFile vfile = file.getVirtualFile();
    if (vfile == null || ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(vfile).hasReadonlyFiles()) {
      return;
    }
    
    final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
    assert doc != null;

    PsiElement leaf = location.getPrevSibling();

    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getPrevSibling();

    while (leaf instanceof PsiComment || leaf instanceof PsiWhiteSpace) {
      @NonNls String text = leaf.getText();
      if (text.matches("\n*#\\s*suppress\\s.+")) {
        final TextRange textRange = leaf.getTextRange();
        doc.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), replace.fun(text));
        return;
      }
      leaf = leaf.getPrevSibling();
    }

    final int offset = location.getTextRange().getStartOffset();
    doc.insertString(offset, suppressComment + "\n");
    CodeStyleManager.getInstance(project).adjustLineIndent(doc, offset + suppressComment.length());
//    UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
  }

  @Override
  @NotNull
  public abstract RncElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);

  private abstract class SuppressAction extends SuppressIntentionAction {
    private final String myLocation;

    public SuppressAction(String location) {
      myLocation = location;
    }

    @NotNull
    public String getText() {
      return "Suppress for " + myLocation;
    }

    @NotNull
    public String getFamilyName() {
      return getDisplayName();
    }

    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
      suppress(element.getContainingFile(), getTarget(element));
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
      return getTarget(element) != null;
    }

    protected abstract PsiElement getTarget(PsiElement element);
  }
}
