package circlet.ui

import circlet.utils.*
import com.intellij.ide.ui.*
import com.intellij.openapi.ui.*
import com.intellij.ui.*
import com.intellij.util.ui.*
import runtime.reactive.*
import runtime.utils.*
import java.awt.*
import java.awt.event.*
import javax.swing.*

class JComponentBasedList(parentLifetime: Lifetime) : Lifetimed by NestedLifetimed(parentLifetime) {
    interface Item {
        val component: JComponent

        var selected: Boolean

        fun onLookAndFeelChanged()
    }

    private val panel = JPanel(VerticalFlowLayout(0, 0))

    val component: Component = ScrollPaneFactory.createScrollPane(
        panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    private var selectedItem: Item? = null

    private val mouseAdapter = MyMouseAdapter()
    private val focusAdapter = MyFocusAdapter()

    init {
        LafManager.getInstance().addLafManagerListener(
            LafManagerListener { onLookAndFeelChanged() }, DisposableOnLifetime(lifetime)
        )

        updateOwnLookAndFeel()
    }

    private fun onLookAndFeelChanged() {
        updateOwnLookAndFeel()

        panel.components.forEach { it.item?.onLookAndFeelChanged() }
    }

    private fun updateOwnLookAndFeel() {
        panel.background = UIUtil.getListBackground()
    }

    fun add(item: Item) {
        val itemComponent = item.component

        itemComponent.putClientProperty(ITEM_KEY, item)
        itemComponent.addListeners()

        panel.add(itemComponent)
    }

    private fun Component.addListeners() {
        addMouseListener(mouseAdapter)
        addFocusListener(focusAdapter)

        UIUtil.uiChildren(this).forEach { it.addListeners() }
    }

    fun removeAll() {
        selectedItem = null

        panel.removeAll()
    }

    fun revalidate() {
        panel.revalidate()
    }

    private fun select(event: ComponentEvent) {
        event.item?.let { newSelectedItem ->
            if (newSelectedItem !== selectedItem) {
                selectedItem?.selected = false
                newSelectedItem.selected = true
                selectedItem = newSelectedItem
            }
        }
    }

    private inner class MyMouseAdapter : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                select(e)
            }
        }
    }

    private inner class MyFocusAdapter : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
            select(e)
        }
    }

    private companion object {
        private val ITEM_KEY = "${JComponentBasedList::class.qualifiedName}.ITEM_KEY"

        private val ComponentEvent.item: Item? get() = component?.item

        private val Component.item: Item?
            get() = ((this as? JComponent)?.getClientProperty(ITEM_KEY) as Item?) ?: parent?.item
    }
}
