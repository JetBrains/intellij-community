package com.jetbrains.python.console.pydev;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

import javax.swing.*;

public class PyCodeCompletionImages {

    /**
     * Returns an image for the given type
     * @param type
     * @return
     */
    public static Icon getImageForType(int type){
      switch (type) {
/*        case IToken.TYPE_IMPORT:
          return imageCache.get(UIConstants.COMPLETION_IMPORT_ICON);*/

        case IToken.TYPE_CLASS:
          return Icons.CLASS_ICON;

        case IToken.TYPE_FUNCTION:
          return Icons.METHOD_ICON;

/*        case IToken.TYPE_BUILTIN:
          return imageCache.get(UIConstants.BUILTINS_ICON);

        case IToken.TYPE_PARAM:
        case IToken.TYPE_LOCAL:
        case IToken.TYPE_OBJECT_FOUND_INTERFACE:
          return imageCache.get(UIConstants.COMPLETION_PARAMETERS_ICON);

        case IToken.TYPE_PACKAGE:
          return imageCache.get(UIConstants.COMPLETION_PACKAGE_ICON);

        case IToken.TYPE_RELATIVE_IMPORT:
          return imageCache.get(UIConstants.COMPLETION_RELATIVE_IMPORT_ICON);

        case IToken.TYPE_EPYDOC:
          return imageCache.get(UIConstants.COMPLETION_EPYDOC);*/

        default:
          return null;
      }
    }

}