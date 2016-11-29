package circlet.login

import circlet.components.*
import java.awt.*
import javax.swing.*

class LoginDialog(val loginComponent: CircletLoginComponent) : JDialog(JOptionPane.getRootFrame(), true) {
    init {
        isModal = true
        val cs = GridBagConstraints()
        cs.fill = GridBagConstraints.HORIZONTAL

        val dataOnStart = loginComponent.loginData.value

        contentPane.add(
            JPanel(GridBagLayout()).apply {
                add(JLabel().apply {
                    cs.gridx = 0;
                    cs.gridy = 0;
                    cs.gridwidth = 1;
                    text = "Login:"
                }, cs)

                add(JTextField(20).apply {
                    cs.gridx = 1;
                    cs.gridy = 0;
                    cs.gridwidth = 2;
                    text = dataOnStart.login
                }, cs)
                add(JLabel().apply {
                    cs.gridx = 0;
                    cs.gridy = 1;
                    cs.gridwidth = 1;
                    text = "Password:"
                }, cs)
                add(JPasswordField(20).apply {
                    cs.gridx = 1;
                    cs.gridy = 1;
                    cs.gridwidth = 2;
                    text = dataOnStart.pass
                }, cs)

                pack()
                isResizable = false
                setLocationRelativeTo(parent)
            }, BorderLayout.CENTER)

        contentPane.add(JPanel().apply {
            add(JButton("Cancel").apply {
                addActionListener { cancel() }
            })
            add(JButton("Save").apply {
                addActionListener { login() }
            })
            add(JButton("Clear").apply {
                addActionListener { reset() }
            })
        }, BorderLayout.PAGE_END)
        pack()


    }

    private fun login() {
    }

    private fun cancel() {
    }

    private fun reset() {
    }

}
