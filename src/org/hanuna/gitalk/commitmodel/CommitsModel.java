package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.HashMultiMap;
import org.hanuna.gitalk.common.MultiMap;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * @author erokhins
 */
public class CommitsModel implements ReadOnlyList<Commit> {
    private static final int HIDE_LIMIT = 25;
    private static final int COUNT_SHOW = 3;

    public static CommitsModel buildModel(ReadOnlyList<Commit> commits) {
        CommitsModel model = new CommitsModel(commits);
        model.buildHideShowMap();
        return model;
    }

    private final ReadOnlyList<Commit> commits;
    private final MultiMap<Integer, Commit> commitsHide = new HashMultiMap<Integer, Commit>();
    private final MultiMap<Integer, Commit> commitsShow = new HashMultiMap<Integer, Commit>();

    private CommitsModel(ReadOnlyList<Commit> commits) {
        this.commits = commits;
    }

    private void buildHideShowMap() {
        for (int i = 0; i < commits.size(); i++) {
            Commit commit = commits.get(i);
            ReadOnlyList<Commit> currentChildrens = commit.getChildren();
            if (currentChildrens.size() > 0) {
                Commit lastChildren = currentChildrens.get(currentChildrens.size() - 1);
                assert lastChildren.wasRead() : "last children not read";
                if (i - lastChildren.index() > HIDE_LIMIT) {
                    commitsShow.add(i - COUNT_SHOW, commit);
                }
            }
            for (Commit parent : commit.getParents()) {
                int childrenIndex = -1;
                ReadOnlyList<Commit> childrens = parent.getChildren();
                for (int  j = 0; j < childrens.size(); j++) {
                    if (commit == childrens.get(j)) {
                        childrenIndex = j;
                    }
                }
                assert childrenIndex != -1 : "bad commits model";
                if (childrenIndex > 0) {
                    Commit prevChildren = childrens.get(childrenIndex - 1);
                    assert prevChildren.wasRead() : "prev children not read";
                    if (i - prevChildren.index() > HIDE_LIMIT) {
                        commitsShow.add(i - COUNT_SHOW, parent);
                    }
                }
                if (childrenIndex < childrens.size() - 1) {
                    Commit nextChildren = childrens.get(childrenIndex + 1);
                    assert nextChildren.wasRead() : "next children not read";
                    if (nextChildren.index() - i > HIDE_LIMIT) {
                        commitsHide.add(i + COUNT_SHOW, parent);
                    }
                } else {
                    if (parent.wasRead()) {
                        if (parent.index() - i > HIDE_LIMIT) {
                            commitsHide.add(i + COUNT_SHOW, parent);
                        }
                    } else {
                        commitsHide.add(i + COUNT_SHOW, parent);
                    }
                }
            }
        }
    }


    @NotNull
    public ReadOnlyList<Commit> hidesCommits(int index) {
        return commitsHide.get(index);
    }

    @NotNull
    public ReadOnlyList<Commit> showsCommits(int index) {
        return commitsShow.get(index);
    }


    @Override
    public int size() {
        return commits.size();
    }

    @Override
    public Commit get(int index) {
        return commits.get(index);
    }

    @Override
    public Iterator<Commit> iterator() {
        return commits.iterator();
    }
}
