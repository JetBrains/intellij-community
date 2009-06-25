package com.intellij.spellchecker.engine;

import com.swabunga.spell.engine.SpellDictionaryHashMap;
import com.swabunga.spell.engine.Word;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * Jazzy implementation of Spell Checker.
 */
final class JazzySpellChecker implements SpellChecker {
    private final SpellCheckerWrapper delegate = new SpellCheckerWrapper();
    private final Map<SpellDictionaryImpl, Set<Character>> dictionaries = new HashMap<SpellDictionaryImpl, Set<Character>>();
    private final Set<Character> allowed = new HashSet<Character>();
    private SpellDictionaryImpl userDictionary;

    JazzySpellChecker() {
        setUserDictionary();
    }

    public void addDictionary(@NotNull InputStream is, @NonNls String encoding, @NotNull Locale locale) throws IOException {
        SpellDictionaryImpl spellDictionary = new SpellDictionaryImpl(new InputStreamReader(is, encoding), locale);
        Set<Character> indexedChars = spellDictionary.getIndexedChars();
        dictionaries.put(spellDictionary, indexedChars);
        allowed.addAll(indexedChars);
        delegate.addDictionary(spellDictionary);
    }

    public void addToDictionary(@NotNull String word) {
        delegate.addToDictionary(word);
        indexWord(word);
    }

    private void indexWord(CharSequence word) {
        indexWord(word, allowed);
    }

    private static void indexWord(CharSequence word, Set<Character> index) {
        for (int i = 0; i < word.length(); i++) {
            index.add(word.charAt(i));
        }
    }

    public void ignoreAll(@NotNull String word) {
        delegate.ignoreAll(word);
    }

    public boolean isIgnored(@NotNull String word) {
        return !isEntireWordAllowed(word, allowed) || delegate.isIgnored(word);
    }

    public boolean isCorrect(@NotNull String word) {
        return !isEntireWordAllowed(word, allowed) || delegate.isCorrect(word);
    }

    private static boolean isEntireWordAllowed(CharSequence word, Set<Character> index) {
        for (int i = 0; i < word.length(); i++) {
            if (!index.contains(word.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private Set<Character> findDictionaryIndex(String word) {
        Set<Character> commonIndex = null;
        Set<Character> lastIndex = null;
        Collection<Set<Character>> indexes = dictionaries.values();
        for (Set<Character> index : indexes) {
            if (isEntireWordAllowed(word, index)) {
                if (lastIndex != null && commonIndex == null) {
                    commonIndex = new HashSet<Character>(lastIndex.size() + index.size());
                    commonIndex.addAll(lastIndex);
                    lastIndex = commonIndex;
                }
                if (commonIndex != null) {
                    commonIndex.addAll(index);
                } else {
                    lastIndex = index;
                }
            }
        }
        return lastIndex;
    }

    @NotNull
    @SuppressWarnings({"unchecked"})
    public List<String> getSuggestions(@NotNull String word, int threshold) {
        List<Word> words = delegate.getSuggestions(word, threshold);
        Set<Character> index = findDictionaryIndex(word);
        List<String> strings = new ArrayList<String>(words.size());
        for (Word w : words) {
            String suggestion = w.getWord();
            if (index == null || isEntireWordAllowed(suggestion, index)) {
                strings.add(suggestion);
            }
        }
        return strings;
    }

    @NotNull
    public List<String> getSuggestionsExt(@NotNull String text, int threshold) {
        String[] words = NameUtil.nameToWords(text);
        List<String> result = new ArrayList<String>();
        int index = 0;
        List[] res = new List[words.length];
        int i = 0;
        for (String word : words) {
            int start = text.indexOf(word, index);
            int end = start + word.length();
            if (!isCorrect(word)) {
                List<String> variants = new ArrayList<String>();
                variants.add(word);
                res[i++] = variants;
            } else {
                List<String> variants = getSuggestions(word, threshold);
                res[i++] = variants;
            }
            index = end;
        }


        int counter[] = new int[i];
        int size = 1;
        for (int j = 0; j < i; j++) {
            size *= res[j].size();
        }
        String[] all = new String[size];

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
        return result;
    }

    @NotNull
    public List<String> getVariants(@NotNull String prefix) {
        if (prefix.length() > 0) {
            List<String> variants = new ArrayList<String>();
            userDictionary.appendWordsStartsWith(prefix, variants);
            Set<Character> index = new HashSet<Character>();
            indexWord(prefix, index);
            for (SpellDictionaryImpl dictionary : dictionaries.keySet()) {
                Set<Character> dictionaryIndex = dictionaries.get(dictionary);
                if (isSame(index, dictionaryIndex)) {
                    dictionary.appendWordsStartsWith(prefix, variants);
                }
            }
            Collections.sort(variants);
            return variants;
        }
        return Collections.emptyList();
    }

    private static boolean isSame(@NotNull Set<Character> i1, @NotNull Set<Character> i2) {
        if (i1.equals(i2)) {
            return true;
        }
        for (Character c : i1) {
            if (!i2.contains(c)) {
                return false;
            }
        }
        return true;
    }

    public void reset() {
        delegate.reset();
        allowed.clear();
        Collection<Set<Character>> sets = dictionaries.values();
        for (Set<Character> set : sets) {
            allowed.addAll(set);
        }
        setUserDictionary();
    }

    private void setUserDictionary() {
        try {
            userDictionary = new SpellDictionaryImpl(Locale.getDefault());
            delegate.setUserDictionary(userDictionary);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SpellDictionaryImpl extends SpellDictionaryHashMap {
        private final Locale locale;

        public SpellDictionaryImpl(Locale locale) throws IOException {
            this.locale = locale;
        }

        private SpellDictionaryImpl(Reader wordList, Locale locale) throws IOException {
            super(wordList);
            this.locale = locale;
        }

        @SuppressWarnings({"unchecked"})
        public Set<Character> getIndexedChars() {
            Set<Character> index = new HashSet<Character>(64);
            Collection<List<String>> values = mainDictionary.values();
            for (List<String> wordList : values) {
                if (wordList != null) {
                    for (String word : wordList) {
                        if (word != null) {
                            indexWord(word, index);
                        }
                    }
                }
            }
            return Collections.unmodifiableSet(index);
        }

        @SuppressWarnings({"unchecked"})
        public void appendWordsStartsWith(@NotNull String prefix, @NotNull Collection<String> buffer) {
            String prefixLowerCase = prefix.toLowerCase(locale);
            Collection<List<String>> values = mainDictionary.values();
            int prefixLength = prefix.length();
            StringBuilder builder = new StringBuilder();
            for (List<String> wordList : values) {
                if (wordList != null) {
                    for (String word : wordList) {
                        if (word != null) {
                            String lowerCased = word.toLowerCase(locale);
                            int length = lowerCased.length();
                            if (lowerCased.startsWith(prefixLowerCase) && length > prefixLength) {
                                builder.setLength(0);
                                builder.append(prefix);
                                builder.append(lowerCased, prefixLength, length);
                                String value = builder.toString();
                                if (!buffer.contains(value)) {
                                    buffer.add(value);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class SpellCheckerWrapper extends com.swabunga.spell.event.SpellChecker {
        private SpellCheckerWrapper() {
            // Disable caching
            setCache(0);
        }

        public List getSuggestions(String word, int threshold) {
            return super.getSuggestions(word, threshold);
        }
    }
}
