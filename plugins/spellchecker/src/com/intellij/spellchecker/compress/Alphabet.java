package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;

public final class Alphabet {

    private final Character[] letters;
    private int lastIndexUsed = 0;
    private int maxIndex = 1023;

    public Character getLetter(int position) {
        assert position < maxIndex;
        return letters[position];
    }

    /*
      @param forceAdd - if set to true - letter will be added to the alphabet if not present yet
      @return index of the letter or -1 if letter was not found and could not be added (due to forceAdd property value)
    */

    public int getIndex(@NotNull Character letter, boolean forceAdd) {
        return getNextIndex(0, letter, forceAdd);
    }

    /*
      @param forceAdd - if set to true - letter will be added to the alphabet if not present yet
      @return index of the letter or -1 if letter was not found and could not be added (due to forceAdd property value)
     */
    public int getNextIndex(int startFrom, @NotNull Character letter, boolean forceAdd) {
        for (int i = startFrom; i <= lastIndexUsed; i++) {
            if (letters[i] != null && letters[i].equals(letter)) {
                return i;
            }
        }
        if (!forceAdd) {
            return -1;
        }
        return add(letter);
    }

    public int getLastIndexUsed() {
        return lastIndexUsed;
    }

    public int getMaxIndex() {
        return maxIndex;
    }

    public int add(@NotNull Character c) {
        lastIndexUsed++;
        letters[lastIndexUsed] = c;
        return lastIndexUsed;
    }


    Alphabet() {
        letters = new Character[maxIndex];
    }

    Alphabet(int maxIndex) {
        this.maxIndex = maxIndex;
        letters = new Character[maxIndex];
    }

    public static Alphabet create(@NotNull CharSequence alphabet) {
        Alphabet result = new Alphabet(alphabet.length() + 1);
        for (int i = 0; i < alphabet.length(); i++) {
            result.add(alphabet.charAt(i));
        }
        return result;
    }
}
