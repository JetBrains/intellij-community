package MyPack;


import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.Messages;


import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Jul 30, 2010
 * Time: 5:59:43 PM

 */
public class MyExtensConfigurable implements Configurable {
    private JComponent myComponent;
    private IDEdialog Mydialog;

    public String getDisplayName() {
        return "Menu Font";
    }

    public boolean isModified() {


           return true;
    }

    public JComponent createComponent() {
        Mydialog = new IDEdialog();
        myComponent= (JComponent) Mydialog.getComponent(0);
        return myComponent ;

    }

    public Icon getIcon() {
        return null;

    }

    public void apply() {
        UISettings settings = UISettings.getInstance();
        LafManager lafManager = LafManager.getInstance();
        String _fontFace = (String)Mydialog.myFontCombo.getSelectedItem();
        String _fontSize_STR = (String)Mydialog.myFontSize.getSelectedItem();
        int _fontSize= Integer.parseInt(_fontSize_STR);
       
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
