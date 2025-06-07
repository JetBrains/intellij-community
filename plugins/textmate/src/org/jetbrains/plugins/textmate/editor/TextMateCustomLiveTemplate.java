package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PlainPrefixMatcher;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.CustomLiveTemplateLookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateBundle;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.language.preferences.SnippetsRegistry;
import org.jetbrains.plugins.textmate.language.preferences.TextMateSnippet;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.psi.TextMateFile;

import java.util.Collection;
import java.util.Collections;

public class TextMateCustomLiveTemplate extends CustomLiveTemplateBase {
  @Override
  public @Nullable String computeTemplateKeyWithoutContextChecking(@NotNull CustomTemplateCallback callback) {
    CharSequence result = "";

    Editor editor = callback.getEditor();
    CharSequence sequence = editor.getDocument().getImmutableCharSequence();
    Collection<TextMateSnippet> availableSnippets = getAvailableSnippets(editor);
    for (TextMateSnippet snippet : availableSnippets) {
      CharSequence prefix = getPrefixForSnippet(sequence, editor.getCaretModel().getOffset(), snippet);
      if (prefix != null && prefix.length() > result.length()) {
        result = prefix;
      }
    }
    return !availableSnippets.isEmpty() ? result.toString() : null;
  }

  @Override
  public @Nullable String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    int offset = callback.getEditor().getCaretModel().getOffset();
    CharSequence charsSequence = callback.getEditor().getDocument().getImmutableCharSequence();
    for (TextMateSnippet snippet : getAvailableSnippets(callback.getEditor())) {
      String key = snippet.getKey();
      if (key.length() <= offset && StringUtil.equals(key, charsSequence.subSequence(offset - key.length(), offset))) {
        return key;
      }
    }
    return null;
  }

  @Override
  public void expand(@NotNull String key, @NotNull CustomTemplateCallback callback) {
    //todo parse content and build template/templates
    TextMateService service = TextMateService.getInstance();
    if (service != null) {
      SnippetsRegistry snippetsRegistry = service.getSnippetRegistry();
      Editor editor = callback.getEditor();
      TextMateScope scope = TextMateEditorUtils.getCurrentScopeSelector(((EditorEx)editor));
      Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet(key, scope);
      if (snippets.size() > 1) {
        LookupImpl lookup = (LookupImpl)LookupManager.getInstance(callback.getProject())
          .createLookup(editor, LookupElement.EMPTY_ARRAY, "", new LookupArranger.DefaultArranger());
        for (TextMateSnippet snippet : snippets) {
          lookup.addItem(new TextMateSnippetLookupElement(snippet), new PlainPrefixMatcher(key));
        }

        Project project = editor.getProject();
        lookup.addLookupListener(new MyLookupAdapter(project, editor, callback.getFile()));
        lookup.refreshUi(false, true);
        lookup.showLookup();
      }
      else if (snippets.size() == 1) {
        TextMateSnippet snippet = ContainerUtil.getFirstItem(snippets);
        assert snippet != null;
        expand(editor, snippet);
      }
    }
  }

  @Override
  public void wrap(@NotNull String selection, @NotNull CustomTemplateCallback callback) {
    // todo
  }

  @Override
  public @NotNull Collection<? extends CustomLiveTemplateLookupElement> getLookupElements(@NotNull PsiFile file,
                                                                                          @NotNull Editor editor,
                                                                                          int offset) {
    TextMateService service = TextMateService.getInstance();
    if (service == null) {
      return super.getLookupElements(file, editor, offset);
    }
    return ContainerUtil.map(getAvailableSnippets(editor),
                             (Function<TextMateSnippet, CustomLiveTemplateLookupElement>)snippet -> new TextMateSnippetLookupElement(snippet));
  }

  @Override
  public void addCompletions(CompletionParameters parameters, CompletionResultSet result) {
    int endOffset = parameters.getOffset();
    Editor editor = parameters.getEditor();
    CharSequence sequence = editor.getDocument().getImmutableCharSequence();
    for (TextMateSnippet snippet : getAvailableSnippets(editor)) {
      CharSequence prefix = getPrefixForSnippet(sequence, endOffset, snippet);
      if (prefix != null && StringUtil.startsWith(snippet.getKey(), prefix)) {
        result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(prefix.toString()))
          .addElement(new TextMateSnippetLookupElement(snippet));
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull CustomTemplateCallback callback, int offset, boolean wrapping) {
    PsiFile file = callback.getFile();
    return file instanceof TextMateFile && ApplicationManager.getApplication().isInternal();
  }

  @Override
  public boolean hasCompletionItem(@NotNull CustomTemplateCallback callback, int offset) {
    return isApplicable(callback, offset, false);
  }

  @Override
  public char getShortcut() {
    // todo settings
    return '\t';
  }

  @Override
  public boolean supportsWrapping() {
    return true;
  }

  @Override
  public @NotNull String getTitle() {
    return TextMateBundle.message("textmate.live.template.name");
  }

  private static @NotNull Collection<TextMateSnippet> getAvailableSnippets(@NotNull Editor editor) {
    TextMateService service = TextMateService.getInstance();
    if (service != null) {
      SnippetsRegistry snippetsRegistry = service.getSnippetRegistry();
      TextMateScope scope = TextMateEditorUtils.getCurrentScopeSelector(((EditorEx)editor));
      return snippetsRegistry.getAvailableSnippets(scope);
    }
    return Collections.emptyList();
  }

  private static @Nullable CharSequence getPrefixForSnippet(@NotNull CharSequence sequence, int offset, @NotNull TextMateSnippet snippet) {
    int startOffset = Math.max(offset - snippet.getKey().length(), 0);
    for (int i = startOffset; i <= offset; i++) {
      if (i == 0 || StringUtil.isWhiteSpace(sequence.charAt(i - 1))) {
        CharSequence prefix = sequence.subSequence(i, offset);
        if (StringUtil.startsWith(snippet.getKey(), prefix)) {
          return prefix;
        }
      }
    }
    return null;
  }

  private static void expand(@NotNull Editor editor, @NotNull TextMateSnippet snippet) {
    String key = snippet.getKey();
    int offset = editor.getCaretModel().getOffset();
    int newOffset = Math.max(offset - key.length(), 0);
    editor.getDocument().deleteString(newOffset, offset);
    editor.getCaretModel().moveToOffset(newOffset);
    EditorModificationUtilEx.insertStringAtCaret(editor, snippet.getContent());
  }

  private static class MyLookupAdapter implements LookupListener {
    private final Project myProject;
    private final Editor myEditor;
    private final PsiFile myFile;

    MyLookupAdapter(Project project, Editor editor, PsiFile file) {
      myProject = project;
      myEditor = editor;
      myFile = file;
    }

    @Override
    public void itemSelected(final @NotNull LookupEvent event) {
      final LookupElement item = event.getItem();
      assert item instanceof CustomLiveTemplateLookupElement;
      if (myFile != null) {
        WriteCommandAction.runWriteCommandAction(myProject,
                                                 TextMateBundle.message("textmate.expand.live.template.command.name"),
                                                 null,
                                                 () -> ((CustomLiveTemplateLookupElement)item).expandTemplate(myEditor, myFile),
                                                 myFile);
      }
    }
  }

  private class TextMateSnippetLookupElement extends CustomLiveTemplateLookupElement {
    private final TextMateSnippet mySnippet;

    TextMateSnippetLookupElement(@NotNull TextMateSnippet snippet) {
      super(TextMateCustomLiveTemplate.this, snippet.getKey(), snippet.getKey(), snippet.getDescription(), false, true);
      mySnippet = snippet;
    }

    @Override
    public void expandTemplate(@NotNull Editor editor, @NotNull PsiFile file) {
      expand(editor, mySnippet);
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
      super.renderElement(presentation);
      presentation.setTypeText(mySnippet.getName());
      presentation.setTypeGrayed(true);
    }
  }
}
