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
        sealed class SelectionState {
            abstract val background: Color
            abstract val foreground: Color

            object Unselected : SelectionState() {
                override val background: Color get() = UIUtil.getListBackground()
                override val foreground: Color get() = UIUtil.getListForeground()
            }

            object SelectedUnfocused : SelectionState() {
                override val background: Color get() = UIUtil.getListUnfocusedSelectionBackground()
                override val foreground: Color get() = UIUtil.getListSelectionForeground()

            }

            object SelectedFocused : SelectionState() {
                override val background: Color get() = UIUtil.getListSelectionBackground()
                override val foreground: Color get() = UIUtil.getListSelectionForeground()
            }
        }

        val component: JComponent

        var selectionState: SelectionState

        fun onLookAndFeelChanged()
    }

    private val panel = JPanel(VerticalFlowLayout(0, 0))

    val component: Component = ScrollPaneFactory.createScrollPane(
        panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    private val itemKey = "${JComponentBasedList::class.qualifiedName}.ITEM_KEY-$ourIndex"

    var selectedItem: T?
        get() = _selectedItem?.item
        set(value) {
            select(value)
        }

    private var _selectedItem: MyItem<T>? = null

    private val mouseAdapter = MyMouseAdapter()
    private val focusAdapter = MyFocusAdapter()
    private val keyAdapter = MyKeyAdapter()

    init {
        ourIndex++

        LafManager.getInstance().addLafManagerListener(
            LafManagerListener { onLookAndFeelChanged() }, DisposableOnLifetime(lifetime)
        )

        updateOwnLookAndFeel()
    }

    private val Component.item: MyItem<T>? get() {
        @Suppress("UNCHECKED_CAST")
        return ((this as? JComponent)?.getClientProperty(itemKey) as MyItem<T>?) ?: parent?.item
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

        itemComponent.putClientProperty(itemKey, MyItem(item, panel.components.size))
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

    fun select(newSelectedItem: T?, newSelectionState: Item.SelectionState? = null) {
        if (newSelectedItem != null) {
            select(panel.components.firstOrNull { it.item?.item === newSelectedItem }, newSelectionState)
        }
    }

    private fun select(
        newSelectedComponent: Component?,
        newSelectionState: Item.SelectionState? = null,
        onSelectNew: (Component) -> Unit = {}
    ) {
        newSelectedComponent?.item?.let { newSelectedItem ->
            if (newSelectedItem !== _selectedItem) {
                _selectedItem?.selectionState = Item.SelectionState.Unselected
                newSelectedItem.selectionState = newSelectionState ?: Item.SelectionState.SelectedFocused
                _selectedItem = newSelectedItem

                val bounds = newSelectedItem.component.bounds

                if (!panel.visibleRect.contains(bounds)) {
                    panel.scrollRectToVisible(bounds)
                }

                onSelectNew(newSelectedComponent)
            }
        }
    }

    private fun selectPrevious() {
        selectAndFocus(getPrevious())
    }

    private fun selectAndFocus(newSelectedComponent: Component?) {
        select(newSelectedComponent, onSelectNew = ::requestFocus)
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
        selectAndFocus(getNext())
    }

    private fun getNext(): Component? = getAdjacent(1)

    private fun selectFirst() {
        selectAndFocus(panel.components.firstOrNull())
    }

    private fun selectLast() {
        selectAndFocus(panel.components.lastOrNull())
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
                val component = e.component

                select(component)
                component?.let { requestFocus(it) }
            }
        }
    }

    private inner class MyFocusAdapter : FocusAdapter() {
        override fun focusGained(e: FocusEvent) {
            select(e.component)

            _selectedItem?.selectionState = Item.SelectionState.SelectedFocused
        }

        override fun focusLost(e: FocusEvent) {
            if (e.oppositeComponent?.item == null) {
                _selectedItem?.selectionState = Item.SelectionState.SelectedUnfocused
            }
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
        private var ourIndex = 0
    }
}

fun <T : JComponentBasedList.Item, U: Any> JComponentBasedList<T>.reload(
    newData: Iterable<U>,
    transform: (U) -> T,
    isSame: (T, T) -> Boolean
) {
    val oldSelectedItem = selectedItem

    removeAll()

    var newSelectedItem: T? = null

    newData.forEach {
        val item = transform(it)

        add(item)

        if (oldSelectedItem != null && newSelectedItem == null && isSame(item, oldSelectedItem)) {
            newSelectedItem = item
        }
    }

    revalidate()

    select(newSelectedItem, oldSelectedItem?.selectionState)
}

fun <T : Any> isSameBy(selector: (T) -> Any): (T, T) -> Boolean = { t1, t2 -> selector(t1) == selector(t2) }

private fun requestFocus(component: Component) {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        IdeFocusManager.getGlobalInstance().requestFocus(component, true)
    }
}
