package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.bundles.Bundle;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createMultipleFoldersDescriptor;
import static com.intellij.ui.ToolbarDecorator.createDecorator;
import static org.jetbrains.plugins.textmate.TextMateServiceImpl.INSTALLED_BUNDLES_PATH;
import static org.jetbrains.plugins.textmate.TextMateServiceImpl.PREINSTALLED_BUNDLES_PATH;

public class TextMateBundlesListPanel implements Disposable {
  private final CheckBoxList<BundleConfigBean> myBundlesList;
  private Collection<TextMateBundlesChangeStateListener> myListeners = new ArrayList<>();

  public TextMateBundlesListPanel() {
    myBundlesList = new CheckBoxList<BundleConfigBean>(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        BundleConfigBean itemAt = myBundlesList.getItemAt(index);
        if (itemAt != null) {
          itemAt.setEnabled(value);
        }
      }
    }) {
      @Nullable
      @Override
      protected String getSecondaryText(int index) {
        BundleConfigBean bean = myBundlesList.getItemAt(index);
        if (isBuiltin(bean)) {
          return "Built-in";
        }
        return bean != null ? bean.getPath() : null;
      }
    };
    myBundlesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    //noinspection unchecked
    new ListSpeedSearch(myBundlesList, (Function<Object, String>)o -> ((JCheckBox)o).getText());
  }

  private static boolean isBuiltin(BundleConfigBean bean) {
    String path = bean != null ? bean.getPath() : null;
    return path != null && (path.startsWith(PREINSTALLED_BUNDLES_PATH) || path.startsWith(INSTALLED_BUNDLES_PATH));
  }

  @NotNull
  public Collection<BundleConfigBean> getState() {
    Set<BundleConfigBean> result = new HashSet<>();
    for (int i = 0; i < myBundlesList.getItemsCount(); i++) {
      result.add(myBundlesList.getItemAt(i));
    }
    return result;
  }

  public void setState(@NotNull Collection<BundleConfigBean> configBeans) {
    myBundlesList.clear();
    for (BundleConfigBean bean : ContainerUtil.sorted(configBeans, Comparator.comparing(BundleConfigBean::getName))) {
      myBundlesList.addItem(bean.copy(), bean.getName(), bean.isEnabled());
    }
  }

  public void addChangeStateListener(TextMateBundlesChangeStateListener listener) {
    myListeners.add(listener);
  }

  public JPanel createMainComponent() {
    return createDecorator(myBundlesList).setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        List<JCheckBox> bundlesToDelete = ContainerUtil.findAll(myBundlesList.getSelectedValuesList(), JCheckBox.class);
        if (bundlesToDelete.isEmpty()) {
          return;
        }
        String title = bundlesToDelete.size() > 1 ? "Remove Bundles?" : "Remove Bundle?";
        String message = StringUtil.join(bundlesToDelete, JCheckBox::getText, "\n");
        if (Messages.showYesNoDialog(message, title, "Remove", "Cancel", null) != Messages.YES) {
          return;
        }
        ListUtil.removeSelectedItems(myBundlesList);
        fireStateChanged();
      }
    }).setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final FileChooserDialog fileChooser = FileChooserFactory.getInstance()
          .createFileChooser(createMultipleFoldersDescriptor(), null, myBundlesList);

        VirtualFile fileToSelect = null;
        final int itemsCount = myBundlesList.getItemsCount();
        if (itemsCount > 0) {
          BundleConfigBean lastAddedBundle = myBundlesList.getItemAt(itemsCount - 1);
          if (lastAddedBundle != null) {
            fileToSelect = LocalFileSystem.getInstance().findFileByPath(lastAddedBundle.getPath());
          }
        }

        final VirtualFile[] bundleDirectories = fileChooser.choose(null, fileToSelect);
        if (bundleDirectories.length > 0) {
          StringBuilder errorMessage = new StringBuilder();
          for (final VirtualFile bundleDirectory : bundleDirectories) {
            final ThrowableComputable<Bundle, Exception> readBundleProcess =
              () -> TextMateService.getInstance().createBundle(bundleDirectory);
            Bundle bundle = null;
            try {
              bundle = ProgressManager.getInstance().runProcessWithProgressSynchronously(readBundleProcess, "Add Bundle", true, null);
            }
            catch (Exception ignore) {
            }
            final String bundleDirectoryPath = bundleDirectory.getPath();
            if (bundle != null) {
              boolean alreadyAdded = false;
              for (int i = 0; i < myBundlesList.getItemsCount(); i++) {
                BundleConfigBean item = myBundlesList.getItemAt(i);
                if (item != null && FileUtil.toSystemIndependentName(bundleDirectoryPath).equals(item.getPath())) {
                  myBundlesList.clearSelection();
                  myBundlesList.setSelectedIndex(i);
                  UIUtil.scrollListToVisibleIfNeeded(myBundlesList);
                  alreadyAdded = true;
                  break;
                }
              }

              if (!alreadyAdded) {
                myBundlesList.addItem(new BundleConfigBean(bundle.getName(), bundleDirectoryPath, true), bundle.getName(), true);
                fireStateChanged();
              }
            }
            else {
              if (errorMessage.length() == 0) {
                errorMessage.append("Can't read following bundles:");
              }
              errorMessage.append('\n').append(bundleDirectoryPath);
            }
          }
          if (errorMessage.length() > 0) {
            Messages.showErrorDialog(errorMessage.toString(), "TextMate Bundle Error");
          }
        }
      }
    }).setRemoveActionUpdater(e -> {
      for (int index : myBundlesList.getSelectedIndices()) {
        if (isBuiltin(myBundlesList.getItemAt(index))) {
          return false;
        }
      }
      return true;
    }).disableUpDownActions().createPanel();
  }

  private void fireStateChanged() {
    for (TextMateBundlesChangeStateListener listener : myListeners) {
      listener.stateChanged();
    }
  }

  public boolean isModified(@NotNull Collection<BundleConfigBean> bundles) {
    return !getState().equals(new HashSet<>(bundles));
  }

  @Override
  public void dispose() {
    myListeners.clear();
    myListeners = null;
  }

  interface TextMateBundlesChangeStateListener {
    void stateChanged();
  }
}
