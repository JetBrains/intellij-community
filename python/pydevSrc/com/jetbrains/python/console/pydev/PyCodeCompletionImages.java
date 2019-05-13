package com.jetbrains.python.console.pydev;

import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PyCodeCompletionImages {

    /**
     * Returns an image for the given type
     * @param type
     * @return
     */
    @Nullable
    public static Icon getImageForType(int type){
      switch (type) {
        case IToken.TYPE_CLASS:
          return PlatformIcons.CLASS_ICON;
        case IToken.TYPE_FUNCTION:
          return PlatformIcons.METHOD_ICON;
        default:
          return null;
      }
    }

}
