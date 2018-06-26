package circlet.ui

import com.intellij.openapi.ui.*
import com.intellij.ui.*
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

class ComponentBasedList {
    interface Item {
        val component: Component
    }

    private val panel = JPanel(VerticalFlowLayout(0, 0))

    val component: Component = ScrollPaneFactory.createScrollPane(
        panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    )

    init {
        panel.background = UIUtil.getListBackground()
    }

    fun add(item: Item) {
        panel.add(item.component)
    }

    fun removeAll() {
        panel.removeAll()
    }

    fun revalidate() {
        panel.revalidate()
    }
}
