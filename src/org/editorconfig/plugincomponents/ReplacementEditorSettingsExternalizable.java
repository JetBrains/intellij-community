package org.editorconfig.plugincomponents;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ReplacementEditorSettingsExternalizable extends EditorSettingsExternalizable {
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener){
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener){
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    @NonNls
    public static final String STRIP_TRAILING_SPACES_NONE = "None";
    @NonNls public static final String STRIP_TRAILING_SPACES_CHANGED = "Changed";
    @NonNls public static final String STRIP_TRAILING_SPACES_WHOLE = "Whole";

    @MagicConstant(stringValues = {STRIP_TRAILING_SPACES_NONE, STRIP_TRAILING_SPACES_CHANGED, STRIP_TRAILING_SPACES_WHOLE})
    @interface StripTrailingSpaces {}

    @Override
    public void setEnsureNewLineAtEOF(boolean ensure) {
        boolean oldEnsure = isEnsureNewLineAtEOF();
        super.setEnsureNewLineAtEOF(ensure);
        propertyChangeSupport.firePropertyChange("ensureNewLineAtEOF", oldEnsure, ensure);
    }

    @Override
    public void setStripTrailingSpaces(@StripTrailingSpaces String stripTrailingSpaces) {
        String oldStripTrailingSpaces = getStripTrailingSpaces();
        super.setStripTrailingSpaces(stripTrailingSpaces);
        propertyChangeSupport.firePropertyChange("stripTrailingSpaces", oldStripTrailingSpaces, stripTrailingSpaces);
    }
}
