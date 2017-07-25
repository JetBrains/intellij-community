package training.ui.views

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.util.containers.HashMap
import com.intellij.util.ui.UIUtil
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.CourseManager
import training.learn.LearnBundle
import training.ui.LearnUIManager
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.BoxLayout
import javax.swing.border.EmptyBorder
import javax.swing.text.BadLocationException
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants


/**
* @author Sergey Karashevich
*/
class LanguageChoosePanel(opaque: Boolean = true, addButton: Boolean = true) : JPanel() {

    private var caption: JLabel? = null
    private var description: MyJTextPane? = null

    private var mainPanel: JPanel? = null
    private var gotoModulesViewButton: JButton? = null
    private val myAddButton: Boolean = addButton

    private val myRadioButtonMap = HashMap<JRadioButton, LanguageExtensionPoint<LangSupport>>()
    private val buttonGroup = ButtonGroup()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isFocusable = false

        init()
        isOpaque = opaque
        background = background
        initMainPanel()
        add(mainPanel)

        //set LearnPanel UI
        this.preferredSize = Dimension(LearnUIManager.getInstance().width, 100)
        this.border = LearnUIManager.getInstance().emptyBorder

        revalidate()
        repaint()
    }


    private fun init() {

        caption = JLabel()
        caption!!.isOpaque = false
        caption!!.font = LearnUIManager.getInstance().moduleNameFont

        description = MyJTextPane(LearnUIManager.getInstance().width)
        description!!.isOpaque = false
        description!!.isEditable = false
        description!!.alignmentX = Component.LEFT_ALIGNMENT
        description!!.margin = Insets(0, 0, 0, 0)
        description!!.border = EmptyBorder(0, 0, 0, 0)

        if (myAddButton) {
            gotoModulesViewButton = JButton()
            gotoModulesViewButton!!.action = object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent) {
                    gotoModulesViewButton!!.isEnabled = false
                }
            }
            gotoModulesViewButton!!.isOpaque = false
            gotoModulesViewButton!!.action = object: AbstractAction(LearnBundle.message("learn.choose.language.button")) {
                override fun actionPerformed(e: ActionEvent?) {
                    val activeLangSupport = getActiveLangSupport()
                    LangManager.getInstance().updateLangSupport(activeLangSupport)

                    val action = ActionManager.getInstance().getAction("learn.open.lesson")
                    val context = com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
                    val event = AnActionEvent.createFromAnAction(action, null, "LearnToolWindow.ChooseLanguageView", context)

                    ActionUtil.performActionDumbAware(action, event)
                }
            }
        }


        StyleConstants.setFontFamily(REGULAR, LearnUIManager.getInstance().plainFont.family)
        StyleConstants.setFontSize(REGULAR, LearnUIManager.getInstance().fontSize)
        StyleConstants.setForeground(REGULAR, LearnUIManager.getInstance().questionColor)

        StyleConstants.setFontFamily(REGULAR_GRAY, LearnUIManager.getInstance().plainFont.family)
        StyleConstants.setFontSize(REGULAR_GRAY, LearnUIManager.getInstance().fontSize)
        StyleConstants.setForeground(REGULAR_GRAY, LearnUIManager.getInstance().descriptionColor)

        StyleConstants.setLeftIndent(PARAGRAPH_STYLE, 0.0f)
        StyleConstants.setRightIndent(PARAGRAPH_STYLE, 0f)
        StyleConstants.setSpaceAbove(PARAGRAPH_STYLE, 0.0f)
        StyleConstants.setSpaceBelow(PARAGRAPH_STYLE, 0.0f)
        StyleConstants.setLineSpacing(PARAGRAPH_STYLE, 0.0f)
    }


    private fun initMainPanel() {

        mainPanel = JPanel()
        mainPanel!!.layout = BoxLayout(mainPanel, BoxLayout.PAGE_AXIS)
        mainPanel!!.isOpaque = false
        mainPanel!!.isFocusable = false

        mainPanel!!.add(caption)
        mainPanel!!.add(Box.createVerticalStrut(LearnUIManager.getInstance().afterCaptionGap))
        mainPanel!!.add(description)
        mainPanel!!.add(Box.createVerticalStrut(LearnUIManager.getInstance().groupGap))

        try {
            initSouthPanel()
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }


        caption!!.text = LearnBundle.message("learn.choose.language.caption")
        try {
            description!!.document.insertString(0, LearnBundle.message("learn.choose.language.description"), REGULAR)
        } catch (e: BadLocationException) {
            e.printStackTrace()
        }

    }


    @Throws(BadLocationException::class)
    private fun initSouthPanel() {
        val radioButtonPanel = JPanel()
        radioButtonPanel.isOpaque = false
        radioButtonPanel.border = EmptyBorder(0, 12, 0, 0)
        radioButtonPanel.layout = BoxLayout(radioButtonPanel, BoxLayout.PAGE_AXIS)

        val sortedLangSupportExtensions = LangManager.getInstance().supportedLanguagesExtensions.sortedBy {it.language}

        for (langSupportExt in sortedLangSupportExtensions) {

            val lessonsCount = CourseManager.getInstance().calcLessonsForLanguage(langSupportExt.instance)
            val jrb = JRadioButton("${Language.findLanguageByID(langSupportExt.language)!!.displayName} ($lessonsCount lesson${if (lessonsCount != 1) "s" else ""}) ")
            jrb.isOpaque = false
            buttonGroup.add(jrb)
            //add radio buttons
            myRadioButtonMap.put(jrb, langSupportExt)
            radioButtonPanel.add(jrb, Component.LEFT_ALIGNMENT)
        }
        buttonGroup.setSelected(buttonGroup.elements.nextElement().model, true)
        mainPanel!!.add(radioButtonPanel)
        mainPanel!!.add(Box.createVerticalStrut(LearnUIManager.getInstance().groupGap))
        if (myAddButton) mainPanel!!.add(gotoModulesViewButton)
    }


    fun updateMainPanel() {
        mainPanel!!.removeAll()
    }

    private inner class MyJTextPane internal constructor(widthOfText: Int) : JTextPane() {

        private var myWidth = 314

        init {
            myWidth = widthOfText
        }

        override fun getPreferredSize(): Dimension {
            return Dimension(myWidth, super.getPreferredSize().height)
        }

        override fun getMaximumSize(): Dimension {
            return preferredSize
        }

    }

    override fun getPreferredSize(): Dimension {
        return Dimension(mainPanel!!.minimumSize.getWidth().toInt() + (LearnUIManager.getInstance().westInset + LearnUIManager.getInstance().eastInset),
                mainPanel!!.minimumSize.getHeight().toInt() + (LearnUIManager.getInstance().northInset + LearnUIManager.getInstance().southInset))
    }


    override fun getBackground(): Color {
        if (!UIUtil.isUnderDarcula())
            return LearnUIManager.getInstance().backgroundColor
        else
            return UIUtil.getPanelBackground()
    }

    fun getActiveLangSupport(): LangSupport {
        val activeButton: AbstractButton = buttonGroup.elements.toList().find { button -> button.isSelected } ?: throw Exception("Unable to get active language")
        assert (activeButton is JRadioButton)
        assert (myRadioButtonMap.containsKey(activeButton))
        return myRadioButtonMap[activeButton]!!.instance
    }

    companion object {

        private val REGULAR = SimpleAttributeSet()
        private val REGULAR_GRAY = SimpleAttributeSet()
        private val PARAGRAPH_STYLE = SimpleAttributeSet()
    }

}