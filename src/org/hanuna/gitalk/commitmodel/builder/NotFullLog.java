package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Hash;

import java.util.Collection;

/**
 * @author erokhins
 */
public class NotFullLog extends RuntimeException {
    private final Collection<Hash> hashCollection;

    public NotFullLog(Collection<Hash> hashCollection) {
        this.hashCollection = hashCollection;
    }

    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("not found commit:");
        for (Hash hash : hashCollection) {
            sb.append(hash.toStrHash() + "|");
        }
        return sb.toString();
    }

}
