package org.hanuna.gitalk.commitgraph.builder;

/**
 * @author erokhins
 *
 * Example of structire data:
 * input log is:
 * 0|-3 1|-
 * 1|-2 4|-
 * 2|-3 5|-
 * 3|-6|-
 * 4|-7|-
 * 5|-7|-
 * 6|-7|-
 * 7|-|-
 *
 * Lines was:
 *  0:0         1
 *  3:0 1:1     2
 *  3:0 2:1 4:2 3
 *  3:0 5:3 4:2 -1
 *  6:0 5:3 4:2 -1
 *  6:0 5:3 7:2 -1
 *  6:0 7:3     -1
 *  7:0         -1
 *
 */
public interface LineOfNode extends Iterable<GraphNode> {

    public int size();
    public int getMainPosition();
    public int getAdditionColor();

}
