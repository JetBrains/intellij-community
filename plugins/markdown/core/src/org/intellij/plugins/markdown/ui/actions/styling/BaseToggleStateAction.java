package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.markdown.ui.actions.MarkdownActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseToggleStateAction extends ToggleAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(BaseToggleStateAction.class);

  @NotNull
  protected abstract String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd);

  @Nullable
  protected String getExistingBoundString(@NotNull CharSequence text, int startOffset) {
    return String.valueOf(text.charAt(startOffset));
  }

  protected abstract boolean shouldMoveToWordBounds();

  @NotNull
  protected abstract IElementType getTargetNodeType();

  @NotNull
  protected SelectionState getCommonState(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    return MarkdownActionUtil.getCommonParentOfType(element1, element2, getTargetNodeType()) == null
           ? SelectionState.NO
           : SelectionState.YES;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(MarkdownActionUtil.findMarkdownTextEditor(e) != null);
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null) {
      return false;
    }

    SelectionState lastState = null;
    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      final var elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret);
      final SelectionState state = getCommonState(elements.getFirst(), elements.getSecond());
      if (lastState == null) {
        lastState = state;
      }
      else if (lastState != state) {
        lastState = SelectionState.INCONSISTENT;
        break;
      }
    }

    if (lastState == SelectionState.INCONSISTENT) {
      e.getPresentation().setEnabled(false);
      return false;
    }
    else {
      e.getPresentation().setEnabled(true);
      return lastState == SelectionState.YES;
    }
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, final boolean state) {
    final Editor editor = MarkdownActionUtil.findMarkdownTextEditor(e);
    final PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
    if (editor == null || psiFile == null) {
      return;
    }


    WriteCommandAction.runWriteCommandAction(psiFile.getProject(), getTemplatePresentation().getText(), null, () -> {
      if (!psiFile.isValid()) {
        return;
      }

      final Document document = editor.getDocument();
      for (Caret caret : ContainerUtil.reverse(editor.getCaretModel().getAllCarets())) {
        if (!state) {
          final var elements = MarkdownActionUtil.getElementsUnderCaretOrSelection(psiFile, caret);
          final PsiElement closestEmph = MarkdownActionUtil.getCommonParentOfType(elements.getFirst(),
                                                                                  elements.getSecond(),
                                                                                  getTargetNodeType());
          if (closestEmph == null) {
            LOG.warn("Could not find enclosing element on its destruction");
            continue;
          }

          final TextRange range = closestEmph.getTextRange();
          removeEmphFromSelection(document, caret, range);
        }
        else {
          addEmphToSelection(document, caret);
        }
      }

      PsiDocumentManager.getInstance(psiFile.getProject()).commitDocument(document);
    });
  }

  public void removeEmphFromSelection(@NotNull Document document, @NotNull Caret caret, @NotNull TextRange nodeRange) {
    final CharSequence text = document.getCharsSequence();
    final String boundString = getExistingBoundString(text, nodeRange.getStartOffset());
    if (boundString == null) {
      LOG.warn("Could not fetch bound string from found node");
      return;
    }
    final int boundLength = boundString.length();

    // Easy case --- selection corresponds to some emph
    if (nodeRange.getStartOffset() + boundLength == caret.getSelectionStart()
        && nodeRange.getEndOffset() - boundLength == caret.getSelectionEnd()) {
      document.deleteString(nodeRange.getEndOffset() - boundLength, nodeRange.getEndOffset());
      document.deleteString(nodeRange.getStartOffset(), nodeRange.getStartOffset() + boundLength);
      return;
    }


    int from = caret.getSelectionStart();
    int to = caret.getSelectionEnd();

    if (shouldMoveToWordBounds()) {
      while (from - boundLength > nodeRange.getStartOffset() && Character.isWhitespace(text.charAt(from - 1))) {
        from--;
      }
      while (to + boundLength < nodeRange.getEndOffset() && Character.isWhitespace(text.charAt(to))) {
        to++;
      }
    }

    if (to + boundLength == nodeRange.getEndOffset()) {
      document.deleteString(nodeRange.getEndOffset() - boundLength, nodeRange.getEndOffset());
    }
    else {
      document.insertString(to, boundString);
    }

    if (from - boundLength == nodeRange.getStartOffset()) {
      document.deleteString(nodeRange.getStartOffset(), nodeRange.getStartOffset() + boundLength);
    }
    else {
      document.insertString(from, boundString);
    }
  }

  public void addEmphToSelection(@NotNull Document document, @NotNull Caret caret) {
    int from = caret.getSelectionStart();
    int to = caret.getSelectionEnd();

    final CharSequence text = document.getCharsSequence();

    if (shouldMoveToWordBounds()) {
      while (from < to && Character.isWhitespace(text.charAt(from))) {
        from++;
      }
      while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
        to--;
      }

      if (from == to) {
        from = caret.getSelectionStart();
        to = caret.getSelectionEnd();
      }
    }

    final String boundString = getBoundString(text, from, to);
    document.insertString(to, boundString);
    document.insertString(from, boundString);

    if (caret.getSelectionStart() == caret.getSelectionEnd()) {
      caret.moveCaretRelatively(boundString.length(), 0, false, false);
    }
  }

  protected enum SelectionState {
    YES,
    NO,
    INCONSISTENT
  }
}
