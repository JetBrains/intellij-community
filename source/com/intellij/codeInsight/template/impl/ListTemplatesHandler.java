
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;

public class ListTemplatesHandler implements CodeInsightActionHandler{
  public void invoke(final Project project, final Editor editor, PsiFile file) {
    if (!file.isWritable()) return;
    EditorUtil.fillVirtualSpaceUntil(editor, editor.getCaretModel().getLogicalPosition().column, editor.getCaretModel().getLogicalPosition().line);

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
    int offset = editor.getCaretModel().getOffset();
    String prefix = getPrefix(editor.getDocument(), offset);
    int contextType = TemplateManager.getInstance(project).getContextType(file, offset);

    TemplateImpl[] templates = TemplateSettings.getInstance().getTemplates();
    ArrayList array = new ArrayList();
    for(int i = 0; i < templates.length; i++){
      TemplateImpl template = templates[i];
      if (template.isDeactivated() || template.isSelectionTemplate()) continue;
      String key = template.getKey();
      if (key.startsWith(prefix) && template.getTemplateContext().isInContext(contextType)){
        LookupItem item = new LookupItem(template, key);
        array.add(item);
      }
    }
    LookupItem[] items = (LookupItem[])array.toArray(new LookupItem[array.size()]);

    if (items.length == 0){
      String text = prefix.length() == 0
        ? "No templates defined in this context"
        : "No templates starting with '" + prefix + "' defined in this context";
      HintManager.getInstance().showErrorHint(editor, text);
      return;
    }

    Lookup lookup = LookupManager.getInstance(project).showLookup(editor, items, prefix, null, new CharFilter() {
      public int accept(char c) {
        if (Character.isJavaIdentifierPart(c)) return CharFilter.ADD_TO_PREFIX;
        return CharFilter.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    });
    lookup.addLookupListener(
      new LookupAdapter() {
        public void itemSelected(LookupEvent event) {
          TemplateManager.getInstance(project).startTemplate(editor, '\0');
        }
      }
    );
  }

  public boolean startInWriteAction() {
    return true;
  }

  private String getPrefix(Document document, int offset) {
    CharSequence chars = document.getCharsSequence();
    int start = offset;
    while(true){
      if (start == 0) break;
      char c = chars.charAt(start - 1);
      if (Character.isWhitespace(c)) break;
      //if (!Character.isJavaIdentifierPart(c)) break;
      start--;
    }
    return chars.subSequence(start, offset).toString();
  }
}