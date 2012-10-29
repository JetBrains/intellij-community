package org.hanuna.gitalk.commitgraph.hidecommits;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.Indexed;

/**
 * @author erokhins
 */
public interface HideCommits extends ReadOnlyList<Commit>, Indexed {
}
