package com.jetbrains.packagesearch.intellij.plugin.ui

import com.intellij.ide.plugins.newui.TagComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts.Checkbox
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.*
import java.io.File
import javax.swing.*
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import javax.swing.tree.MutableTreeNode
import kotlin.math.max

@Suppress("TooManyFunctions", "MagicNumber", "unused") // Adopted code...
class RiderUI {

    companion object {

        val MAIN_BG_COLOR: Color =
            JBColor.namedColor("Plugins.background", JBColor { if (JBColor.isBright()) UIUtil.getListBackground() else Color(0x313335) })
        val GRAY_COLOR: Color = JBColor.namedColor("Label.infoForeground", JBColor(Gray._120, Gray._135))

        val HeaderBackgroundColor = MAIN_BG_COLOR
        val SectionHeaderBackgroundColor = JBColor.namedColor("Plugins.SectionHeader.background", JBColor(0xF7F7F7, 0x3C3F41))
        val UsualBackgroundColor = MAIN_BG_COLOR

        private val HeaderFont: Font = UIUtil.getListFont().let { Font(it.family, Font.BOLD, it.size) }
        val BigFont: Font = UIUtil.getListFont().let { Font(it.family, Font.BOLD, (it.size * 1.3).toInt()) }

        const val BigHeaderHeight = 40
        const val MediumHeaderHeight = 30
        const val SmallHeaderHeight = 24

        fun headerPanel(init: BorderLayoutPanel.() -> Unit) = object : BorderLayoutPanel() {
            init {
                border = JBEmptyBorder(2, 0, 2, 12)
            }

            override fun getBackground() = HeaderBackgroundColor
        }.apply(init)

        fun borderPanel(backgroundColor: Color = UsualBackgroundColor, init: BorderLayoutPanel.() -> Unit) = object : BorderLayoutPanel() {
            override fun getBackground() = backgroundColor
        }.apply(init)

        fun boxPanel(backgroundColor: Color = UsualBackgroundColor, init: JPanel.() -> Unit) = object : JPanel() {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }

            override fun getBackground() = backgroundColor
        }.apply(init)

        fun flowPanel(backgroundColor: Color = UsualBackgroundColor, init: JPanel.() -> Unit) = object : JPanel() {
            init {
                layout = FlowLayout(FlowLayout.LEFT)
            }

            override fun getBackground() = backgroundColor
        }.apply(init)

        fun checkBox(@Checkbox title: String) = object : JCheckBox(title) {
            override fun getBackground() = UsualBackgroundColor
        }

        fun menuItem(@Nls title: String, icon: Icon?, handler: () -> Unit): JMenuItem {
            if (icon != null) {
                return JMenuItem(title, icon).apply { addActionListener { handler() } }
            }
            return JMenuItem(title).apply { addActionListener { handler() } }
        }

        fun <TItem> comboBox(items: Array<TItem>) = ComboBox(items).apply {
            background = UsualBackgroundColor
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, JBUI.CurrentTheme.CustomFrameDecorations.paneBackground())
        }

        fun <TItem> comboBox(comboBoxModel: ComboBoxModel<TItem>) = ComboBox(comboBoxModel).apply {
            background = UsualBackgroundColor
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, JBUI.CurrentTheme.CustomFrameDecorations.paneBackground())
        }

        fun updateParentHeight(component: JComponent) {
            if (component.parent != null) {
                component.parent.maximumSize = Dimension(Int.MAX_VALUE, component.maximumSize.height)
            }
        }

        fun createLabel() = JLabel().apply { font = UIUtil.getLabelFont() }
        fun createHeaderLabel(@Label text: String = "") = JLabel(text).apply { font = HeaderFont }
        fun createBigLabel(@Label text: String = "") = JLabel(text).apply { font = BigFont }

        fun createPlatformTag(@Nls text: String = "") = object : TagComponent(text.toLowerCase()) {
            override fun isInClickableArea(pt: Point?) = false
        }.apply {
            RelativeFont.TINY.install(this)
        }

        fun toHtml(color: Color) = String.format("#%02x%02x%02x", color.red, color.green, color.blue)

        fun getTextColor(isSelected: Boolean) = when {
            isSelected -> RiderColor(UIUtil.getListSelectionForeground(true))
            else -> RiderColor(UIUtil.getListForeground())
        }

        fun getTextColor2(isSelected: Boolean) = when {
            isSelected -> getTextColor(isSelected)
            else -> RiderColor(Color.GRAY, Color.GRAY)
        }

        fun setHeight(component: JComponent, height: Int, keepWidth: Boolean = false, scale: Boolean = true) {
            val scaledHeight = if (scale) JBUI.scale(height) else height

            component.apply {
                preferredSize = Dimension(if (keepWidth) preferredSize.width else 0, scaledHeight)
                minimumSize = Dimension(if (keepWidth) minimumSize.width else 0, scaledHeight)
                maximumSize = Dimension(if (keepWidth) maximumSize.width else Int.MAX_VALUE, scaledHeight)
            }
        }

        fun setWidth(component: JComponent, width: Int) {
            val scaledWidth = JBUI.scale(width)

            component.preferredSize = Dimension(scaledWidth, 0)
            component.minimumSize = Dimension(scaledWidth, 0)
            component.maximumSize = Dimension(scaledWidth, Int.MAX_VALUE)
        }

        inline fun <reified T> addPopupHandler(list: JBList<T>, crossinline createPopup: (T) -> JPopupMenu) {
            list.addMouseListener(object : PopupHandler() {
                override fun invokePopup(comp: Component?, x: Int, y: Int) {
                    val index = list.locationToIndex(Point(x, y - 1))
                    if (index != -1) {
                        val element = (list.model as DefaultListModel<T>).get(index)
                        if (element != null) {
                            val popup = createPopup(element)
                            popup.show(list, x, y)
                        }
                    }
                }
            })
        }

        inline fun <reified T : MutableTreeNode> addClickHandler(tree: Tree, count: Int = 1, crossinline clickHandler: (T) -> Unit) {
            tree.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == count || count == 0) {
                        val path = tree.getPathForLocation(e.x, e.y)
                        if (path != null) {
                            val node = path.lastPathComponent
                            if (node is T) clickHandler(node)
                        }
                    }
                }
            })
        }

        inline fun <reified T : MutableTreeNode> addDoubleClickHandler(tree: Tree, crossinline clickHandler: (T) -> Unit) {
            addClickHandler(tree, 2, clickHandler)
        }

        inline fun <reified T : JComponent> onMouseClicked(comp: T, crossinline handler: T.(MouseEvent) -> Unit) {
            comp.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) = handler(comp, e)
            })
        }

        inline fun <reified T : JComponent> onMouseDoubleClicked(comp: T, crossinline handler: T.(MouseEvent) -> Unit) {
            comp.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        handler(comp, e)
                    }
                }
            })
        }

        inline fun <reified T : JComponent> onFocusGained(comp: T, crossinline handler: T.(FocusEvent) -> Unit) {
            comp.addFocusListener(object : FocusAdapter() {
                override fun focusGained(e: FocusEvent) = handler(comp, e)
            })
        }

        fun verticalScrollPane(c: Component) = object : JScrollPane(
            VerticalScrollPanelWrapper(c),
            VERTICAL_SCROLLBAR_AS_NEEDED,
            HORIZONTAL_SCROLLBAR_NEVER
        ) {
            init {
                border = BorderFactory.createEmptyBorder()
                viewport.background = UsualBackgroundColor
            }
        }

        fun overrideKeyStroke(c: JComponent, stroke: String, action: () -> Unit) = overrideKeyStroke(c, stroke, stroke, action)

        fun overrideKeyStroke(c: JComponent, key: String, stroke: String, action: () -> Unit) {
            val inputMap = c.getInputMap(JComponent.WHEN_FOCUSED)
            inputMap.put(KeyStroke.getKeyStroke(stroke), key)
            c.actionMap.put(key, object : AbstractAction() {
                override fun actionPerformed(arg: ActionEvent) {
                    action()
                }
            })
        }

        fun <E> addKeyboardPopupHandler(list: JBList<E>, stroke: String, createPopup: (List<E>) -> JPopupMenu?) {
            val action = {
                if (list.selectedIndex >= 0) {
                    val popup = createPopup(list.selectedValuesList)
                    if (popup != null && popup.subElements.any()) {
                        val location = JBPopupFactory.getInstance().guessBestPopupLocation(list).getPoint(list)
                        popup.show(list, location.x, location.y)
                        ApplicationManager.getApplication().invokeLater {
                            MenuSelectionManager.defaultManager().selectedPath = arrayOf<MenuElement>(popup, popup.subElements.first())
                        }
                    }
                }
            }
            overrideKeyStroke(list, "jlist:$stroke", stroke, action)
        }

        fun updateScroller(comp: JComponent) {
            val scrollPane = UIUtil.getParentOfType(JScrollPane::class.java, comp)
            if (scrollPane != null) {
                scrollPane.revalidate()
                scrollPane.repaint()
                scrollPane.parent?.apply {
                    revalidate()
                    repaint()
                }
            }
        }

        fun combineIcons(icon1: Icon?, icon2: Icon?, horizontal: Boolean): Icon? = when {
            icon1 == null && icon2 == null -> null
            icon1 == null || icon2 == null -> icon1 ?: icon2
            horizontal -> RowIcon(icon1, icon2)
            else -> CompositeIcon(icon1, icon2, horizontal)
        }

        fun openFile(project: Project, path: String) {
            val file = VfsUtil.findFileByIoFile(File(path), true)
            if (file == null) {
                Notifications.Bus.notify(
                    Notification(
                        Notifications.SYSTEM_MESSAGES_GROUP_ID,
                        PackageSearchBundle.message("packagesearch.ui.error.noSuchFile.title"),
                        PackageSearchBundle.message("packagesearch.ui.error.noSuchFile.message", path),
                        NotificationType.ERROR
                    ), project
                )
            } else {
                FileEditorManager.getInstance(project).openFile(file, true, true)
            }
        }
    }

    private class CompositeIcon(val icon1: Icon, val icon2: Icon, val horizontal: Boolean) : Icon {

        override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
            icon1.paintIcon(c, g, x, y)
            if (horizontal) {
                icon2.paintIcon(c, g, x + icon1.iconWidth, y)
            } else {
                icon2.paintIcon(c, g, x, y + icon2.iconWidth)
            }
        }

        override fun getIconWidth() = if (horizontal) icon1.iconWidth + icon2.iconWidth else max(icon1.iconWidth, icon2.iconWidth)
        override fun getIconHeight() = if (horizontal) max(icon1.iconHeight, icon2.iconHeight) else icon1.iconHeight + icon2.iconHeight
    }

    private class VerticalScrollPanelWrapper(content: Component) : JPanel(), Scrollable {
        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(content)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 10
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 100
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
        override fun getBackground() = UsualBackgroundColor
    }
}

abstract class ButtonColumn(private val table: JTable, private val buttonSize: Int) :
    AbstractCellEditor(), TableCellRenderer, TableCellEditor, ActionListener, MouseListener {

    private fun JButton.prepare(): JButton {
        isBorderPainted = false
        isFocusPainted = false
        preferredSize = JBDimension(buttonSize, buttonSize)
        minimumSize = JBDimension(buttonSize, buttonSize)
        maximumSize = JBDimension(buttonSize, buttonSize)
        return this
    }

    protected val renderButton = JButton().prepare()
    protected val editButton = JButton().prepare()

    private var editorValue: Any? = null
    private var isButtonColumnEditor: Boolean = false
    private val emptyRenderer = JPanel(BorderLayout())
    private val rendererWrapper = JPanel(BorderLayout())
    private val editorWrapper = JPanel(BorderLayout())

    abstract fun getIcon(row: Int, column: Int): Icon?
    abstract fun actionPerformed(row: Int, column: Int)

    init {
        @Suppress("LeakingThis") // Adopted code
        editButton.addActionListener(this)
    }

    fun install(vararg columns: Int) {
        val self = this
        for (column in columns) {
            table.columnModel.getColumn(column).apply {
                cellRenderer = self
                cellEditor = self
                resizable = false
                minWidth = JBUI.scale(buttonSize + 2)
                maxWidth = JBUI.scale(buttonSize + 2)
                preferredWidth = JBUI.scale(buttonSize + 2)
            }
        }
        table.addMouseListener(this)
    }

    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        val cellIcon = getIcon(row, column)

        return buildComponent(table, isSelected, cellIcon, rendererWrapper, renderButton)
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val cellIcon = getIcon(row, column)
        editorValue = value

        return buildComponent(table, true, cellIcon, editorWrapper, editButton)
    }

    private fun buildComponent(
        table: JTable,
        isSelected: Boolean,
        cellIcon: Icon?,
        wrapperComponent: JPanel,
        button: JButton
    ): Component {
        if (cellIcon == null) {
            return emptyRenderer.colored(table, isSelected)
        }

        val component = button.apply {
            icon = cellIcon
            disabledIcon = IconLoader.getDisabledIcon(cellIcon)
        }.colored(table, isSelected)

        wrapperComponent.removeAll()
        wrapperComponent.add(component)
        wrapperComponent.revalidate()

        return wrapperComponent.colored(table, isSelected)
    }

    override fun getCellEditorValue() = editorValue

    override fun actionPerformed(e: ActionEvent) {
        val row = table.convertRowIndexToModel(table.editingRow)
        val column = table.convertColumnIndexToModel(table.editingColumn)

        fireEditingStopped()

        if (row != -1) {
            actionPerformed(row, column)
        }
    }

    override fun mousePressed(e: MouseEvent) {
        if (table.isEditing && table.cellEditor === this) {
            isButtonColumnEditor = true
        }
    }

    override fun mouseReleased(e: MouseEvent) {
        if (isButtonColumnEditor && table.isEditing) {
            table.cellEditor.stopCellEditing()
        }
        isButtonColumnEditor = false
    }

    override fun mouseClicked(e: MouseEvent) {
        // No-op
    }

    override fun mouseEntered(e: MouseEvent) {
        // No-op
    }

    override fun mouseExited(e: MouseEvent) {
        // No-op
    }
}

abstract class ComboBoxColumn(
    private val table: JTable,
    private val comboBoxSize: Int
) : AbstractCellEditor(), TableCellRenderer, TableCellEditor, ActionListener, MouseListener {

    private fun prepareComboBox() = RiderUI.comboBox(CollectionComboBoxModel(emptyList<Any>()))
        .apply {
            setMinimumAndPreferredWidth(JBUI.scale(comboBoxSize))
            maximumSize.width = JBUI.scale(comboBoxSize)
        }

    private val renderComboBox = prepareComboBox()
    private val editComboBox = prepareComboBox().apply {
        putClientProperty("JComboBox.isTableCellEditor", true)
    }

    private var editorValue: Any? = null
    private var editingRow: Int = -1
    private var editingColumn: Int = -1
    private var isComboBoxEditor: Boolean = false

    init {
        editComboBox.addActionListener(this)
    }

    fun install(vararg columns: Int) {
        val self = this

        for (column in columns) {
            table.columnModel.getColumn(column).apply {
                cellRenderer = self
                cellEditor = self
                resizable = false
                minWidth = JBUI.scale(comboBoxSize)
                maxWidth = JBUI.scale(comboBoxSize)
                preferredWidth = JBUI.scale(comboBoxSize)
            }
        }

        table.addMouseListener(this)
    }

    @Suppress("LongParameterList")
    open fun customizeComboBox(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
        comboBox: ComboBox<*>
    ): ComboBox<*> = comboBox

    open fun actionPerformed(row: Int, column: Int, selectedValue: Any?) = Unit

    override fun getTableCellRendererComponent(table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
        return buildComponent(table, isSelected, customizeComboBox(table, value, isSelected, hasFocus, row, column, renderComboBox))
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        val notCurrentlyEditedRow = editingRow >= 0 && editingRow != row
        val notCurrentlyEditedColumn = editingColumn >= 0 && editingColumn != column
        if (editComboBox.isEditable && (notCurrentlyEditedRow || notCurrentlyEditedColumn)) {
            // Fire action performed event with previous value, if we were manually editing a different row/column.
            val editorValue = (editComboBox.editor.editorComponent as JTextField).text
            actionPerformed(editingRow, editingColumn, editorValue)
        }

        // Time to edit the new one
        editingRow = row
        editingColumn = column
        editorValue = value

        return buildComponent(table, true, customizeComboBox(table, value, isSelected, isSelected, row, column, editComboBox))
    }

    private fun buildComponent(table: JTable, isSelected: Boolean, comboBox: ComboBox<*>): Component =
        comboBox.colored(table, isSelected)

    override fun getCellEditorValue() = editorValue

    override fun actionPerformed(e: ActionEvent) {
        val row = max(table.convertRowIndexToModel(table.editingRow), editingRow)
        val column = max(table.convertColumnIndexToModel(table.editingColumn), editingColumn)

        fireEditingStopped()

        if (row != -1) {
            actionPerformed(row, column, editComboBox.selectedItem)
        }
    }

    override fun mousePressed(e: MouseEvent) {
        if (table.isEditing && table.cellEditor === this) {
            isComboBoxEditor = true
        }
    }

    override fun mouseReleased(e: MouseEvent) {
        if (!table.isEditing) {
            editingRow = -1
            editingColumn = -1
            editorValue = null
        }
    }

    override fun mouseClicked(e: MouseEvent) {
        // No-op
    }

    override fun mouseEntered(e: MouseEvent) {
        // No-op
    }

    override fun mouseExited(e: MouseEvent) {
        // No-op
    }
}

class RiderColor : JBColor {
    constructor(regular: Color, dark: Color) : super(regular, dark)
    constructor(regular: Color) : super(regular, regular)
}

class ComponentActionWrapper(private val myComponentCreator: () -> JComponent) : DumbAwareAction(),
    CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation) = myComponentCreator()
    override fun actionPerformed(e: AnActionEvent) {
        // No-op
    }
}

fun JBColor.toHtml() = RiderUI.toHtml(this)

fun Component.colored(table: JTable, isSelected: Boolean) = this.apply {
    foreground = if (isSelected) table.selectionForeground else table.foreground
    background = if (isSelected) table.selectionBackground else RiderUI.UsualBackgroundColor
}

fun Component.updateAndRepaint() {
    this.invalidate()
    this.repaint()
}
