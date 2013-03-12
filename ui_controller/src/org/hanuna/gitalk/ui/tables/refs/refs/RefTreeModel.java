package org.hanuna.gitalk.ui.tables.refs.refs;

import org.hanuna.gitalk.commit.Hash;

import java.util.Set;

/**
 * @author erokhins
 */
public interface RefTreeModel {

    public RefTreeTableNode getRootNode();

    public Set<Hash> getCheckedCommits();
}
