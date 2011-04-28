package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.django.lang.template.parsing.DjangoTemplateTokenTypes;
import com.jetbrains.django.util.DjangoUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class DjangoTemplateLineBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
  private final PyDebuggerEditorsProvider myEditorsProvider = new PyDebuggerEditorsProvider();

  public DjangoTemplateLineBreakpointType() {
    super("django-line", "Django Line Breakpoint");
  }

  public boolean canPutAt(@NotNull final VirtualFile file, final int line, @NotNull final Project project) {
    final Ref<Boolean> stoppable = Ref.create(false);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      if (DjangoUtil.isDjangoTemplateDocument(document, project)) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, new Processor<PsiElement>() {
          public boolean process(PsiElement psiElement) {
            if (psiElement != null && (psiElement.getNode().getElementType() == DjangoTemplateTokenTypes.DJANGO_EXPRESSION_START ||
                                       psiElement.getNode().getElementType() == DjangoTemplateTokenTypes.DJANGO_TAG_START)) {
              stoppable.set(true);
              return false;
            } else {
              return true;
            }
          }
        });
      }
    }

    return stoppable.get();
  }

  @Nullable
  public XBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
    return null;
  }

  @Override
  public String getBreakpointsDialogHelpTopic() {
    return "reference.dialogs.breakpoints";
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }
}
