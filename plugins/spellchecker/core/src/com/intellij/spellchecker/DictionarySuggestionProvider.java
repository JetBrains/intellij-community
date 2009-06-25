package com.intellij.spellchecker;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.NameSuggestionProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


public class DictionarySuggestionProvider implements NameSuggestionProvider {
    private boolean active;

    public void setActive(boolean active) {
        this.active = active;
    }

    public SuggestedNameInfo getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, List<String> result) {
        assert result != null;
        if (!active) {
            return null;
        }
        String text = nameSuggestionContext.getText();
        if (nameSuggestionContext instanceof PsiLocalVariable) {
            assert ((PsiLocalVariable) nameSuggestionContext).getNameIdentifier() != null;
            //noinspection ConstantConditions
            text = ((PsiLocalVariable) nameSuggestionContext).getNameIdentifier().getText();
        }
        if (text == null) {
            return null;
        }

        SpellCheckerManager manager = SpellCheckerManager.getInstance(element.getProject());

        String[] words = NameUtil.nameToWords(text);

        int index = 0;
        List[] res = new List[words.length];
        int i = 0;
        for (String word : words) {
            int start = text.indexOf(word, index);
            int end = start + word.length();
            if (!manager.hasProblem(word)) {
                List<String> variants = new ArrayList<String>();
                variants.add(word);
                res[i++] = variants;
            } else {
                List<String> variants = manager.getSuggestions(word);
                res[i++] = variants;
            }
            index = end;
        }

        String[] all = null;
        int counter[] = new int[i];
        int size = 1;
        for (int j = 0; j < i; j++) {
            size *= res[j].size();
        }
        all = new String[size];

        for (int k = 0; k < size; k++) {
            for (int j = 0; j < i; j++) {
                if (all[k] == null) {
                    all[k] = "";
                }
                all[k] += res[j].get(counter[j]);
                counter[j]++;
                if (counter[j] >= res[j].size()) {
                    counter[j] = 0;
                }
            }
        }

        result.addAll(Arrays.asList(all));
        return null;
    }

    public Collection<LookupElement> completeName(PsiElement element, PsiElement nameSuggestionContext, String prefix) {
        return null;
    }
}
