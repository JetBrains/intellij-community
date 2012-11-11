package org.hanuna.gitalk.common.rows;

import org.hanuna.gitalk.common.readonly.ReadOnlyList;

/**
 * @author erokhins
 */
public interface Row<T> extends ReadOnlyList<T> {

    public int getRowIndex();
}
