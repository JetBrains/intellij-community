package org.hanuna.gitalk.commitmodel;

import java.util.Collection;

/**
 * @author erokhins
 */
public class NotFinalise extends RuntimeException {
    private final Collection<Hash> hashCollection;

    public NotFinalise(Collection<Hash> hashCollection) {
        this.hashCollection = hashCollection;
    }
}
