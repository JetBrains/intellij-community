package circlet.ui

import circlet.utils.*
import com.intellij.ide.ui.*
import com.intellij.openapi.ui.*
import com.intellij.openapi.wm.*
import com.intellij.ui.*
import com.intellij.util.ui.*
import runtime.reactive.*
import runtime.utils.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import kotlin.math.*

class JComponentBasedList<T : JComponentBasedList.Item>(parentLifetime: Lifetime) :
    Lifetimed by NestedLifetimed(parentLifetime) {

    interface Item {
        val component: JComponent

        var selected: Boolean

        fun onLookAndFeelChanged()
    }

    private val panel = JPanel(VerticalFlowLayout(0, 0))

    val component: Component = ScrollPaneFactory.createScrollPane(
        panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    var selectedItem: T?
        get() = _selectedItem?.item
        set(value) {
            if (value != null) {
                select(panel.components.firstOrNull { it.item?.item === value })
            }
        }

    private var _selectedItem: MyItem<T>? = null

    private val mouseAdapter = MyMouseAdapter()
    private val focusAdapter = MyFocusAdapter()
    private val keyAdapter = MyKeyAdapter()

    init {
        LafManager.getInstance().addLafManagerListener(
            LafManagerListener { onLookAndFeelChanged() }, DisposableOnLifetime(lifetime)
        )

        updateOwnLookAndFeel()
    }

    private val Component.item: MyItem<T>? get() {
        @Suppress("UNCHECKED_CAST")
        return ((this as? JComponent)?.getClientProperty(ITEM_KEY) as MyItem<T>?) ?: parent?.item
    }

    private fun onLookAndFeelChanged() {
        updateOwnLookAndFeel()

        panel.components.forEach { it.item?.onLookAndFeelChanged() }
    }

    private fun updateOwnLookAndFeel() {
        panel.background = UIUtil.getListBackground()
    }

    fun add(item: T) {
        val itemComponent = item.component

        itemComponent.putClientProperty(ITEM_KEY, MyItem(item, panel.components.size))
        itemComponent.addListeners()

        panel.add(itemComponent)
    }

    private fun Component.addListeners() {
        addMouseListener(mouseAdapter)
        addFocusListener(focusAdapter)
        addKeyListener(keyAdapter)

        UIUtil.uiChildren(this).forEach { it.addListeners() }
    }

    fun removeAll() {
        _selectedItem = null

        panel.removeAll()
    }

    fun revalidate() {
        panel.revalidate()
    }

    private fun select(event: ComponentEvent) {
        select(event.component)
    }

    private fun select(newSelectedComponent: Component?) {
        newSelectedComponent?.item?.let { newSelectedItem ->
            if (newSelectedItem !== _selectedItem) {
                _selectedItem?.selected = false
                newSelectedItem.selected = true
                _selectedItem = newSelectedItem

                val bounds = newSelectedItem.component.bounds

                if (!panel.visibleRect.contains(bounds)) {
                    panel.scrollRectToVisible(bounds)
                }

                IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
                    IdeFocusManager.getGlobalInstance().requestFocus(newSelectedComponent, true)
                }
            }
        }
    }

    private fun selectPrevious() {
        select(getPrevious())
    }

    private fun getPrevious(): Component? = getAdjacent(-1)

    private fun getAdjacent(shift: Int): Component? {
        val components = panel.components

        if (components.isEmpty()) {
            return null
        }

        if (_selectedItem == null) {
            return components[0]
        }

        return (_selectedItem!!.index + shift).takeIf { it in 0 until components.size  }?.let { components[it] }
    }

    private fun selectNext() {
        select(getNext())
    }

    private fun getNext(): Component? = getAdjacent(1)

    private fun selectFirst() {
        select(panel.components.firstOrNull())
    }

    private fun selectLast() {
        select(panel.components.lastOrNull())
    }

    private fun scrollPageUp() {
        scrollPage { visibleRect ->
            -min(visibleRect.height, visibleRect.y)
        }
    }

    private fun scrollPage(getShift: (visibleRect: Rectangle) -> Int) {
        val visibleRect = panel.visibleRect
        val shift = getShift(visibleRect)

        if (shift != 0) {
            visibleRect.translate(0, shift)
            panel.scrollRectToVisible(visibleRect)
        }
    }

    private fun scrollPageDown() {
        scrollPage { visibleRect ->
            val visibleRectHeight = visibleRect.height

            min(visibleRectHeight, panel.height - visibleRect.y - visibleRectHeight)
        }
    }

    private class MyItem<T : Item>(val item: T, val index: Int) : Item by item

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

    private inner class MyKeyAdapter : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            when (e.keyCode) {
                KeyEvent.VK_UP -> handle(e, ::selectPrevious)
                KeyEvent.VK_DOWN -> handle(e, ::selectNext)
                KeyEvent.VK_HOME -> handle(e, ::selectFirst)
                KeyEvent.VK_END -> handle(e, ::selectLast)
                KeyEvent.VK_PAGE_UP -> handle(e, ::scrollPageUp)
                KeyEvent.VK_PAGE_DOWN -> handle(e, ::scrollPageDown)
            }
        }

        private fun handle(e: KeyEvent, handler: () -> Unit) {
            e.consume()

            handler()
        }
    }

    private companion object {
        private val ITEM_KEY = "${JComponentBasedList::class.qualifiedName}.ITEM_KEY"
    }
}
