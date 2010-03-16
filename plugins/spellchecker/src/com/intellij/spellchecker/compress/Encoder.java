package com.intellij.spellchecker.compress;

import com.intellij.spellchecker.compress.Alphabet;
import com.intellij.spellchecker.compress.EncodingException;
import com.intellij.spellchecker.compress.UnitBitSet;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;

public final class Encoder {

    private final Alphabet alphabet;
    private final int offset = 2;

    public Encoder() {
        alphabet = new Alphabet();
    }

    public Encoder(Alphabet alphabet) {
        this.alphabet = alphabet;
    }

    public Alphabet getAlphabet() {
        return alphabet;
    }

    public UnitBitSet encode(@NotNull CharSequence letters, boolean force) throws EncodingException {
        UnitBitSet bs = new UnitBitSet(alphabet.getMaxIndex(), false);
        bs.setUnitValue(0, letters.length());
        bs.setUnitValue(1, alphabet.getIndex(letters.charAt(0), force));
        for (int i = 0; i < letters.length(); i++) {
            Character letter = letters.charAt(i);
            int index = alphabet.getIndex(letter, force);
            bs.setUnitValue(i + offset, index);
        }
        return bs;
    }

    public String decode(@NotNull UnitBitSet bitSet) throws EncodingException {
        int wordLength = bitSet.getUnitValue(0);
        Character firstLetter = alphabet.getLetter(bitSet.getUnitValue(1));
        final StringBuffer result = new StringBuffer();
        bitSet.iterateParUnits(new Consumer<Integer>() {

            public void consume(Integer value) {
                if (value > 0 && value <= alphabet.getLastIndexUsed()) {
                    result.append(alphabet.getLetter(value));
                }
            }
        }, offset, true);

        final String word = result.toString();
        if (word.length() != wordLength || !word.startsWith(String.valueOf(firstLetter))) {
            throw new EncodingException(
                    new MessageFormat("Error during encoding: required length - {0}, starts with {1}, but decoded: {2}")
                            .format(new Object[]{wordLength, firstLetter, word}));
        }
        return word;
    }
}