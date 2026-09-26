// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structureView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.util.ObjectUtils.tryCast;


/**
 * Extracted from PlatformTestUtil to print JTree with location string.
 */
public final class StructureViewUtil {
    @Nullable
    private static String toString(@Nullable Object node, @Nullable Queryable.PrintInfo printInfo) {
        if (node instanceof AbstractTreeNode) {
            return ((AbstractTreeNode<?>) node).toTestString(printInfo);
        }

        FilteringTreeStructure.FilteringNode filteringNode = tryCast(node, FilteringTreeStructure.FilteringNode.class);
        if (filteringNode != null && filteringNode.getDelegate() instanceof AbstractTreeNode) {
            return ((AbstractTreeNode<?>) filteringNode.getDelegate()).toTestString(printInfo);
        }

        if (node instanceof Queryable) {
            return Queryable.Util.print((Queryable)node, printInfo);
        }

        if (node == null) {
            return "NULL";
        }

        return node.toString();
    }

    @NotNull
    public static String print(JTree tree, boolean withSelection) {
        return print(tree, withSelection, null, null);
    }

    @NotNull
    public static String print(
            JTree tree, boolean withSelection,
            @Nullable Queryable.PrintInfo printInfo,
            @Nullable Condition<String> nodePrintCondition) {
        StringBuilder buffer = new StringBuilder();
        Collection<String> strings = printAsList(tree, withSelection, printInfo, nodePrintCondition);
        for (String string : strings) {
            buffer.append(string).append("\n");
        }
        return buffer.toString();
    }

    public static Collection<String> printAsList(
            JTree tree, boolean withSelection,
            @Nullable Queryable.PrintInfo printInfo,
            @Nullable Condition<String> nodePrintCondition) {
        Collection<String> strings = new ArrayList<String>();
        Object root = tree.getModel().getRoot();
        printImpl(tree, new TreePath(root), strings, 0, withSelection, printInfo, nodePrintCondition);
        return strings;
    }

    private static void printImpl(JTree tree,
            TreePath path,
            Collection<String> strings,
            int level,
            boolean withSelection,
            @Nullable Queryable.PrintInfo printInfo,
            @Nullable Condition<String> nodePrintCondition) {
        Object pathComponent = path.getLastPathComponent();
        Object userObject = getUserObject(pathComponent);
        String nodeText;
        if (userObject != null) {
            nodeText = toString(userObject, printInfo);
        }
        else {
            nodeText = "null";
        }

        if (nodePrintCondition != null && !nodePrintCondition.value(nodeText)) return;

        StringBuilder buff = new StringBuilder();
        StringUtil.repeatSymbol(buff, ' ', level);

        boolean expanded = tree.isExpanded(path);
        int childCount = tree.getModel().getChildCount(pathComponent);
        if (childCount > 0) {
            buff.append(expanded ? "-" : "+");
        }

        boolean selected = tree.getSelectionModel().isPathSelected(path);
        if (withSelection && selected) {
            buff.append("[");
        }

        buff.append(nodeText);

        if (withSelection && selected) {
            buff.append("]");
        }

        strings.add(buff.toString());

        if (expanded) {
            for (int i = 0; i < childCount; i++) {
                Object child = tree.getModel().getChild(pathComponent, i);
                printImpl(tree, path.pathByAddingChild(child), strings, level + 1, withSelection, printInfo, nodePrintCondition);
            }
        }
    }

    private static Object getUserObject(Object node) {
        return node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode)node).getUserObject() : node;
    }
}
