package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.refs.Ref;

/**
 * @author erokhins
 */
public class CommitCell {
    public static final int HEIGHT_CELL = 22;

    private final String text;
    private final ReadOnlyList<Ref> refsToThisCommit;

    public CommitCell(String text, ReadOnlyList<Ref> refsToThisCommit) {
        this.text = text;
        this.refsToThisCommit = refsToThisCommit;
    }

    public String getText() {
        return text;
    }

    public ReadOnlyList<Ref> getRefsToThisCommit() {
        return refsToThisCommit;
    }

}
