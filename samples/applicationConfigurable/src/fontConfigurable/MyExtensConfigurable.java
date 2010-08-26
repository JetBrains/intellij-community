package fontConfigurable;


import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.options.Configurable;


import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Jul 30, 2010
 * Time: 5:59:43 PM
 */
public class MyExtensConfigurable implements Configurable {
    private JComponent myComponent;
    private IDEDialog myDialog;

    public String getDisplayName() {
        return "Menu Font";
    }

    public boolean isModified() {


        return true;
    }

    public JComponent createComponent() {
        myDialog = new IDEDialog();
        myComponent = (JComponent) myDialog.getComponent(0);
        return myComponent;

    }

    public Icon getIcon() {
        return null;

    }

    public void apply() {
        UISettings settings = UISettings.getInstance();
        LafManager lafManager = LafManager.getInstance();
        String _fontFace = (String) myDialog.myFontCombo.getSelectedItem();
        String _fontSize_STR = (String) myDialog.myFontSize.getSelectedItem();
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
