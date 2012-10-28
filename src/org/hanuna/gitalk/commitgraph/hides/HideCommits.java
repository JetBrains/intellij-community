package org.hanuna.gitalk.commitgraph.hides;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.calculatemodel.calculator.Indexed;

/**
 * @author erokhins
 */
public interface HideCommits extends ReadOnlyList<Commit>, Indexed {
}
