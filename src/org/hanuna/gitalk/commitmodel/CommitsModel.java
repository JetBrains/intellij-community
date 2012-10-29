package org.hanuna.gitalk.commitmodel;

import org.hanuna.gitalk.common.HashMultiMap;
import org.hanuna.gitalk.common.MultiMap;
import org.hanuna.gitalk.common.readonly.AbstractReadOnlyList;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class CommitsModel extends AbstractReadOnlyList<Commit> {
    private static final int HIDE_LIMIT = 25;
    private static final int COUNT_SHOW = 3;

    @NotNull
    public static CommitsModel buildModel(@NotNull ReadOnlyList<Commit> commits, boolean fullList) {
        CommitsModel model = new CommitsModel(commits, fullList);
        model.buildHideShowMap();
        return model;
    }

    private final ReadOnlyList<Commit> commits;
    private final int size;
    private final boolean fullModel;
    private final MultiMap<Integer, Commit> commitsHide = new HashMultiMap<Integer, Commit>();
    private final MultiMap<Integer, Commit> commitsShow = new HashMultiMap<Integer, Commit>();

    private CommitsModel(@NotNull ReadOnlyList<Commit> commits, boolean fullModel) {
        this.commits = commits;
        this.fullModel = fullModel;
        if (fullModel) {
            this.size = commits.size();
        } else {
            assert commits.size() > 2 * HIDE_LIMIT : "small size";
            this.size = commits.size() - 2 * HIDE_LIMIT;
        }
    }

    private void buildHideShowMap() {
        for (int i = 0; i < size; i++) {
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

    public boolean isFullModel() {
        return fullModel;
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
        return size;
    }

    @Override
    public Commit get(int index) {
        return commits.get(index);
    }

}
