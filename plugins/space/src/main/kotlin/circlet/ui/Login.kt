package circlet.ui

import java.awt.*
import javax.swing.*

class LoginDialog(val server: String) : JDialog(JOptionPane.getRootFrame(), true) {
    init {
        isModal = true
        val cs = GridBagConstraints()
        cs.fill = GridBagConstraints.HORIZONTAL

        contentPane.add(
            JPanel(GridBagLayout()).apply {
                add(JLabel().apply {
                    cs.gridx = 0;
                    cs.gridy = 0;
                    cs.gridwidth = 1;
                    text = "Server:"
                }, cs)

                add(JTextField(20).apply {
                    cs.gridx = 1;
                    cs.gridy = 0;
                    cs.gridwidth = 2;
                    text = server
                }, cs)

                pack()
                isResizable = false
                setLocationRelativeTo(parent)
            }, BorderLayout.CENTER)

        contentPane.add(JPanel().apply {
            add(JButton("Cancel").apply {
                addActionListener { cancel() }
            })
            add(JButton("Connect").apply {
                addActionListener { connect() }
            })
        }, BorderLayout.PAGE_END)
        pack()


    }

    private fun connect() {
    }

    private fun cancel() {
    }

}
