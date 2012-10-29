package org.hanuna.gitalk.commitgraph.hidecommits;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.Row;

/**
 * @author erokhins
 */
public interface HideCommits extends ReadOnlyList<Commit>, Row {
}
