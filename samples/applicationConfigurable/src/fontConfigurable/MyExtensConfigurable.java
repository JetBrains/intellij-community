package fontConfigurable;


import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.UIUtil;


import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Jul 30, 2010
 * Time: 5:59:43 PM
 */
public class MyExtensConfigurable implements Configurable {
    private JComponent myComponent;
    private JComboBox myFontName;
    private JComboBox myFontSize;
    private JButton MyDefaultFontButton;
    private JPanel myPanel;

    public String getDisplayName() {
        return "Menu Font";
    }

    public boolean isModified() {


        return true;
    }

    public JComponent createComponent() {
        // Add listener to the Default Font button
        MyButtonListener actionListener = new MyButtonListener();
        actionListener.myFontName = myFontName;
        actionListener.myFontSize = myFontSize;
        MyDefaultFontButton.addActionListener(actionListener);
        // Define a set of possible values for combo boxes.
        UISettings settings = UISettings.getInstance();
        myFontName.setModel(new DefaultComboBoxModel(UIUtil.getValidFontNames(false)));
        myFontSize.setModel(new DefaultComboBoxModel(UIUtil.getStandardFontSizes()));
        myFontName.setSelectedItem(settings.FONT_FACE);
        myFontSize.setSelectedItem(String.valueOf(settings.FONT_SIZE));


        myComponent = (JComponent) myPanel;
        return myComponent;

    }

    public Icon getIcon() {
        return null;

    }

    public void apply() {
        UISettings settings = UISettings.getInstance();
        LafManager lafManager = LafManager.getInstance();
        String _fontFace = (String) myFontName.getSelectedItem();
        String _fontSize_STR = (String) myFontSize.getSelectedItem();
        int _fontSize = Integer.parseInt(_fontSize_STR);

        if (_fontSize != settings.FONT_SIZE || !settings.FONT_FACE.equals(_fontFace)) {
            settings.FONT_SIZE = _fontSize;
            settings.FONT_FACE = _fontFace;
            settings.fireUISettingsChanged();
            lafManager.updateUI();
        }

    }

    public void disposeUIResources() {

    }

    public String getHelpTopic() {
        return "preferences.lookFeel";
    }

    public void reset() {

    }


}
