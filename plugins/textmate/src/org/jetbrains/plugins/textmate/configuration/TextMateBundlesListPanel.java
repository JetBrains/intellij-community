package org.jetbrains.plugins.textmate.configuration;

import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateBundle;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.bundles.Bundle;

import javax.swing.*;
import java.util.*;

import static com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createMultipleFoldersDescriptor;
import static com.intellij.ui.ToolbarDecorator.createDecorator;
import static org.jetbrains.plugins.textmate.TextMateServiceImpl.BUNDLED_BUNDLES_PATH;

public class TextMateBundlesListPanel implements Disposable {
  private static final String TEXTMATE_LAST_ADDED_BUNDLE = "textmate.last.added.bundle";

  private final CheckBoxList<BundleConfigBean> myBundlesList;
  private Collection<TextMateBundlesChangeStateListener> myListeners = new ArrayList<>();

  public TextMateBundlesListPanel() {
    myBundlesList = new CheckBoxList<>() {
      @Override
      protected @Nullable String getSecondaryText(int index) {
        BundleConfigBean bean = myBundlesList.getItemAt(index);
        if (isBuiltin(bean)) {
          return TextMateBundle.message("title.built.in");
        }
        return bean != null ? FileUtil.toSystemDependentName(bean.getPath()) : null;
      }
    };
    myBundlesList.setCheckBoxListListener((index, value) -> {
      BundleConfigBean itemAt = myBundlesList.getItemAt(index);
      if (itemAt != null) {
        itemAt.setEnabled(value);
      }
    });
    myBundlesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    new ListSpeedSearch<>(myBundlesList, box -> box.getText());
  }

  private static boolean isBuiltin(BundleConfigBean bean) {
    String path = bean != null ? bean.getPath() : null;
    return path != null && path.startsWith(BUNDLED_BUNDLES_PATH);
  }

  public @NotNull Collection<BundleConfigBean> getState() {
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

  public JPanel createMainComponent() {
    return createDecorator(myBundlesList)
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          List<JCheckBox> bundlesToDelete = ContainerUtil.findAll(myBundlesList.getSelectedValuesList(), JCheckBox.class);
          if (bundlesToDelete.isEmpty()) {
            return;
          }
          String message = StringUtil.join(bundlesToDelete, JCheckBox::getText, "\n");
          if (MessageDialogBuilder.yesNo(TextMateBundle.message("textmate.remove.title", bundlesToDelete.size()), message)
            .yesText(CommonBundle.message("button.remove"))
            .noText(CommonBundle.getCancelButtonText())
            .icon(null)
            .ask(myBundlesList)) {
            ListUtil.removeSelectedItems(myBundlesList);
            fireStateChanged();
          }
        }
      })
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          FileChooserDialog fileChooser = FileChooserFactory.getInstance().createFileChooser(createMultipleFoldersDescriptor(), null, myBundlesList);

          VirtualFile fileToSelect = null;
          int itemsCount = myBundlesList.getItemsCount();
          if (itemsCount > 0) {
            String lastAddedBundle = PropertiesComponent.getInstance().getValue(TEXTMATE_LAST_ADDED_BUNDLE);
            if (StringUtil.isNotEmpty(lastAddedBundle)) {
              fileToSelect = LocalFileSystem.getInstance().findFileByPath(lastAddedBundle);
            }
          }

          VirtualFile[] bundleDirectories = fileChooser.choose(null, fileToSelect);
          if (bundleDirectories.length > 0) {
            String errorMessage = null;
            for (VirtualFile bundleDirectory : bundleDirectories) {
              PropertiesComponent.getInstance().setValue(TEXTMATE_LAST_ADDED_BUNDLE, bundleDirectory.getPath());
              ThrowableComputable<Bundle, Exception> readBundleProcess = () -> TextMateService.getInstance().createBundle(bundleDirectory);
              Bundle bundle = null;
              try {
                bundle = ProgressManager.getInstance().runProcessWithProgressSynchronously(readBundleProcess, TextMateBundle.message("button.add.bundle"), true, null);
              }
              catch (Exception ignore) { }
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
                  BundleConfigBean item = new BundleConfigBean(bundle.getName(), bundleDirectoryPath, true);
                  myBundlesList.addItem(item, item.getName(), true);
                  fireStateChanged();
                }
              }
              else {
                errorMessage = TextMateBundle.message("message.textmate.bundle.error", bundleDirectory.getPresentableUrl());
              }
            }
            if (errorMessage != null) {
              Messages.showErrorDialog(errorMessage, TextMateBundle.message("title.textmate.bundle.error"));
            }
          }
        }
      })
      .setRemoveActionUpdater(e -> {
        for (int index : myBundlesList.getSelectedIndices()) {
          if (isBuiltin(myBundlesList.getItemAt(index))) {
            return false;
          }
        }
        return true;
      })
      .disableUpDownActions()
      .createPanel();
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
