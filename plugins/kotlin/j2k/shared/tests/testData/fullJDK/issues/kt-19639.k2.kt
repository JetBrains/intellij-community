// ERROR: Initializer type mismatch: expected 'Enumeration<DefaultMutableTreeNode>', actual 'Enumeration<TreeNode!>!'.
import java.util.Enumeration
import javax.swing.tree.DefaultMutableTreeNode

class TestJavaExpectedTypeInference {
    fun test(node: DefaultMutableTreeNode) {
        val e: Enumeration<DefaultMutableTreeNode> = node.children()
        while (e.hasMoreElements()) {
            val child = e.nextElement()
            val name = child.getUserObject() as String?
            println(name)
        }
    }
}
