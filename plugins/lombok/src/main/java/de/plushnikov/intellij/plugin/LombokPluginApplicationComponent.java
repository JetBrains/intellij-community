package de.plushnikov.intellij.plugin;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.BuildNumber;
import de.plushnikov.intellij.lombok.patcher.inject.ClassRootFinder;
import de.plushnikov.intellij.lombok.patcher.inject.LiveInjector;
import de.plushnikov.intellij.plugin.agent.IdeaPatcher;
import de.plushnikov.intellij.plugin.inspection.LombokExplicitTypeCanBeDiamondInspection;
import de.plushnikov.intellij.plugin.settings.LombokSettings;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Main application component, that loads Lombok support
 */
public class LombokPluginApplicationComponent implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(LombokPluginApplicationComponent.class.getName());

  /**
   * Plugin has been updated with the current run.
   */
  private boolean updated;

  /**
   * Plugin update notification has been shown.
   */
  private boolean updateNotificationShown;

  /**
   * Checks if plugin was updated in the current run.
   *
   * @return plugin was updated
   */
  public boolean isUpdated() {
    return updated;
  }

  public boolean isUpdateNotificationShown() {
    return updateNotificationShown;
  }

  public void setUpdateNotificationShown(boolean shown) {
    this.updateNotificationShown = shown;
  }

  /**
   * Get LombokPlugin Application Component
   *
   * @return LombokPlugin Application Component
   */
  public static LombokPluginApplicationComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(LombokPluginApplicationComponent.class);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "Lombok plugin for IntelliJ";
  }

  @Override
  public void initComponent() {
    LOG.info("Lombok plugin initialized for IntelliJ");

    final LombokSettings settings = LombokSettings.getInstance();
    updated = !Version.PLUGIN_VERSION.equals(settings.getVersion());
    if (updated) {
      settings.setVersion(Version.PLUGIN_VERSION);
    }

    final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (unitTestMode || settings.isEnableRuntimePatch()) {
      LOG.info("Runtime path support is enabled");
      injectAgent();
    } else {
      LOG.info("Runtime path support is disabled");
    }

    final BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    if (currentBuild.getBaselineVersion() < 173) {
//    Overwrite IntelliJ Diamond inspection, to filter out val declarations only for IntelliJ < 2017.3
      addCustomDiamondInspectionExtension();
    }
  }

  private void addCustomDiamondInspectionExtension() {
    final LocalInspectionEP localInspectionEP = new LocalInspectionEP() {
      @NotNull
      @Override
      public Object getInstance() {
        return new LombokExplicitTypeCanBeDiamondInspection();
      }
    };
    localInspectionEP.groupPath = "Java";
    localInspectionEP.language = "JAVA";
    localInspectionEP.shortName = "Convert2Diamond";
    localInspectionEP.displayName = "Explicit type can be replaced with &lt;&gt;";
    localInspectionEP.groupDisplayName = "Java language level migration aids";
    localInspectionEP.enabledByDefault = true;
    localInspectionEP.level = "WARNING";
    localInspectionEP.implementationClass = "de.plushnikov.intellij.plugin.inspection.LombokExplicitTypeCanBeDiamondInspection";

    final ExtensionPoint<LocalInspectionEP> extensionPoint = Extensions.getRootArea().getExtensionPoint(LocalInspectionEP.LOCAL_INSPECTION);
    extensionPoint.registerExtension(localInspectionEP);
  }

  private void injectAgent() {

    LOG.info("Starting injection of IntelliJ-Patch");

    LiveInjector liveInjector = new LiveInjector();

    // Quick environment validation
    if (!liveInjector.isSupportedEnvironment()) {
      LOG.warn("Unsupported environment - agent injection only works on a sun-derived 1.6 or higher VM\"");
      return;
    }

    String agentSourceFile = ClassRootFinder.findClassRootOfClass(IdeaPatcher.class);
    LOG.info("Injector use agentSourceFile: " + agentSourceFile);
    if (!liveInjector.isInjectable(agentSourceFile)) {
      LOG.warn("Unable to inject Lombok Idea Patcher Agent as agent source is not valid");
      return;
    }

    final BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();

    Map<String, String> options = new HashMap<String, String>();
    options.put("ideaBuild", currentBuild.asStringWithoutProductCode());

    try {
      liveInjector.inject(agentSourceFile, options);
    } catch (Exception ex) {
      LOG.error("Error injecting Lombok Agent", ex);
    }


  }

  @Override
  public void disposeComponent() {
    LOG.info("Lombok plugin disposed for IntelliJ");
  }

}
