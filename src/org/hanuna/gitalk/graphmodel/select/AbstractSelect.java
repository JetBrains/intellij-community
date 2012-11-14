package org.hanuna.gitalk.graphmodel.select;

import org.hanuna.gitalk.graphmodel.select.Select;

/**
 * @author erokhins
 */
public class AbstractSelect implements Select {
    private boolean select = false;

    @Override
    public boolean isSelect() {
        return select;
    }

    @Override
    public void select() {
        select = true;
    }

    @Override
    public void unSelect() {
        select = false;
    }
}
