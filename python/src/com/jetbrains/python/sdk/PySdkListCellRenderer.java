package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.util.Map;

public class PySdkListCellRenderer extends HtmlListCellRenderer<Sdk> {
  private final Map<Sdk, SdkModificator> mySdkModificators;

  public PySdkListCellRenderer(ListCellRenderer listCellRenderer, Map<Sdk, SdkModificator> sdkModificators) {
    super(listCellRenderer);
    mySdkModificators = sdkModificators;
  }

  @Override
  protected void doCustomize(final JList list, final Sdk sdk, final int index, final boolean selected, final boolean hasFocus) {
    if (sdk != null) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getPlatformIndependentFlavor(sdk.getHomePath());
      final Icon icon = flavor != null ? flavor.getIcon() : ((SdkType) sdk.getSdkType()).getIcon();
      final String name;
      if (mySdkModificators != null && mySdkModificators.containsKey(sdk)) {
        name = mySdkModificators.get(sdk).getName();
      }
      else {
        name = sdk.getName();
      }
      if (PythonSdkType.isInvalid(sdk)) {
        append("[invalid] " + name);
        final LayeredIcon layered = new LayeredIcon(2);
        layered.setIcon(icon, 0);
        // TODO: Create a separate invalid SDK overlay icon
        final Icon overlay = IconLoader.findIcon("/actions/cancel.png");
        layered.setIcon(overlay, 1);
        setIcon(layered);
      }
      else {
        append(name);
        setIcon(icon);
      }
    }
  }
}
