package org.hanuna.gitalk.gittree;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitsLines {
    private List<List<Integer>> listOfLines = new ArrayList<List<Integer>>();
    private final CommitsTree commitsTree;

    public CommitsLines(CommitsTree commitsTree) {
        this.commitsTree = commitsTree;
        List<Integer> firstLine = new ArrayList<Integer>(1);
        firstLine.add(0);
        listOfLines.add(firstLine);
        for (int i = 1; i < commitsTree.size(); i++) {
            step(i);
        }
    }

    public List<Integer> getLine(int indexOfLine) {
        return listOfLines.get(indexOfLine);
    }

    public int size() {
        return listOfLines.size();
    }


    private int getMainParentIndex(int commitIndex) {
        CommitNode cn = commitsTree.getNode(commitIndex).getMainParent();
        if (cn != null) {
            return cn.getLogIndex();
        } else {
            return -1;
        }
    }

    private int getSecondParentIndex(int commitIndex) {
        CommitNode cn = commitsTree.getNode(commitIndex).getSecondParent();
        if (cn != null) {
            return cn.getLogIndex();
        } else {
            return -1;
        }
    }

    public void step(int numberOfLine) {
        List<Integer> prevLine = listOfLines.get(numberOfLine - 1);
        List<Integer> nextLine = new ArrayList<Integer>();
        for (int i = 0; i < prevLine.size(); i++) {
            int currentCommitNumber = prevLine.get(i);
            if (currentCommitNumber == numberOfLine - 1) {
                int mainParent = getMainParentIndex(currentCommitNumber);
                int secondParent = getSecondParentIndex(currentCommitNumber);

                if (mainParent != -1 && !nextLine.contains(mainParent)) {
                    nextLine.add(mainParent);
                }
                if (secondParent != -1 && !nextLine.contains(secondParent)) {
                    nextLine.add(secondParent);
                }
            } else {
                if (!nextLine.contains(currentCommitNumber)){
                    nextLine.add(currentCommitNumber);
                }
            }
        }
        if (!nextLine.contains(numberOfLine)) {
            nextLine.add(numberOfLine);
        }
        listOfLines.add(nextLine);
    }



}
