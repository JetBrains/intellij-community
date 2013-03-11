package org.hanuna.gitalk.swing_ui.frame.refs;

import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class RefTreeModelImpl implements RefTreeModel {
    private final RefsModel refsModel;
    private final CommitSelectManager selectManager = new CommitSelectManager();
    private final RefTreeTableNode rootNode = new RefTreeTableNode("root");

    private final Map<String, RefTreeTableNode> nodeMap = new HashMap<String, RefTreeTableNode>();


    public RefTreeModelImpl(RefsModel refsModel) {
        this.refsModel = refsModel;
        selectAll();
        createTree();
    }

    private void selectAll() {
        for (Ref ref : refsModel.getAllRefs()) {
            selectManager.setSelectCommit(ref.getCommitHash(), true);
        }
    }

    private void createTree() {
        nodeMap.put("", rootNode);

        addCategory(Ref.RefType.LOCAL_BRANCH, "local");
        addCategory(Ref.RefType.REMOTE_BRANCH, "remotes");
        addCategory(Ref.RefType.STASH, "stash");
        addCategory(Ref.RefType.ANOTHER, "another");
    }

    private void addCategory(@NotNull Ref.RefType refType, @NotNull String categoryName) {
        for (Ref ref : refsModel.getAllRefs()) {
            if (ref.getType() == refType) {
                addNewNode(categoryName + '/' + ref.getName(), ref);
            }
        }
    }

    private void addNewNode(@NotNull String fullPatch, @NotNull Ref ref) {
        String[] folders = fullPatch.split("/");
        StringBuilder currentPatch  = new StringBuilder(fullPatch.length());
        RefTreeTableNode currentNode = rootNode;

        for (int i = 0; i < folders.length - 1; i++) {
            if (i == 0) {
                currentPatch.append(folders[0]);
            } else {
                currentPatch.append('/').append(folders[i]);
            }
            RefTreeTableNode node = nodeMap.get(currentPatch.toString());
            if (node != null) {
                currentNode = node;
            } else {
                RefTreeTableNode newNode = new RefTreeTableNode(folders[i]);
                currentNode.add(newNode);
                nodeMap.put(currentPatch.toString(), newNode);
                currentNode = newNode;
            }
            assert !currentNode.isRefNode() : "it is not leaf Ref Node!";
        }

        currentPatch.append('/').append(folders[folders.length - 1]);
        RefTreeTableNode refNode = new RefTreeTableNode(ref, selectManager);
        currentNode.add(refNode);
    }

    @Override
    public RefTreeTableNode getRootNode() {
        return rootNode;
    }
}
