package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.08.2003
 * Time: 16:20:00
 * To change this template use Options | File Templates.
 */
public class TestLookupManager extends LookupManagerImpl{
  private Project myProject;
  public TestLookupManager(Project project){
    super(project, EditorFactory.getInstance());
    myProject = project;
  }

  public Lookup showLookup(
      final Editor editor,
      LookupItem[] items,
      String prefix,
      LookupItemPreferencePolicy itemPreferencePolicy,
      CharFilter filter) {
    hideActiveLookup();

    final CodeInsightSettings settings = CodeInsightSettings.getInstance();

    items = (LookupItem[])items.clone();
    if (!settings.SHOW_SIGNATURES_IN_LOOKUPS){
      items = filterEqualSignatures(items);
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());

    if (sortItems(psiFile, items)) {
      Arrays.sort(items, COMPARATOR);
    }

    myActiveLookup = new LookupImpl(myProject, editor, items, prefix, itemPreferencePolicy, filter);
    myActiveLookupEditor = editor;
    ((LookupImpl)myActiveLookup).show();
    return myActiveLookup;
  }

  private static boolean sortItems(PsiFile containingFile, LookupItem[] items) {
    if (!(containingFile instanceof XmlFile)) return true;

    for (int i = 0; i < items.length; i++) {
      LookupItem item = items[i];
      if (item.getObject() instanceof PsiElement) return true;
    }

    return CodeInsightSettings.getInstance().SORT_XML_LOOKUP_ITEMS;
  }


  public void forceSelection(char completion, int index){
    final LookupImpl lookup = ((LookupImpl)myActiveLookup);
    if(lookup == null) throw new RuntimeException("There are no items in this lookup");
    final LookupItem[] items = lookup.getItems();
    final LookupItem lookupItem = items[index];
    lookup.setCurrentItem(lookupItem);
    lookup.finishLookup(completion);
  }

  public void forceSelection(char completion, LookupItem item){
    final LookupImpl lookup = ((LookupImpl)myActiveLookup);
    lookup.setCurrentItem(item);
    lookup.finishLookup(completion);
  }


  public LookupItem[] getItems(){
    final LookupImpl lookup = ((LookupImpl)myActiveLookup);
    return lookup != null ? lookup.getItems() : null;
  }
}
