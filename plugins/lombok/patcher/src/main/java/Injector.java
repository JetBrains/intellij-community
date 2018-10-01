public class Injector {
//  private void injectAgent() {
//
//    LOG.info("Starting injection of IntelliJ-Patch");
//
//    LiveInjector liveInjector = new LiveInjector();
//
//    // Quick environment validation
//    if (!liveInjector.isSupportedEnvironment()) {
//      LOG.warn("Unsupported environment - agent injection only works on a sun-derived 1.6 or higher VM\"");
//      return;
//    }
//
//    String agentSourceFile = ClassRootFinder.findClassRootOfClass(IdeaPatcher.class);
//    LOG.info("Injector use agentSourceFile: " + agentSourceFile);
//    if (!liveInjector.isInjectable(agentSourceFile)) {
//      LOG.warn("Unable to inject Lombok Idea Patcher Agent as agent source is not valid");
//      return;
//    }
//
//    final BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
//
//    Map<String, String> options = new HashMap<String, String>();
//    options.put("ideaBuild", currentBuild.asStringWithoutProductCode());
//
//    try {
//      liveInjector.inject(agentSourceFile, options);
//    } catch (Exception ex) {
//      LOG.error("Error injecting Lombok Agent", ex);
//    }
//  }

//  /**
//   * Support method required by patcher project and {@link ModifierVisibilityClassFileTransformer}.
//   * Provides a simple way to inject modifiers into older versions of IntelliJ. Return of the null value is dictated by legacy IntelliJ API.
//   *
//   * @param modifierList PsiModifierList that is being queried
//   * @param name         String name of the PsiModifier
//   * @return {@code Boolean.TRUE} if modifier exists (explicitly set by modifier transformers of the plugin), {@code null} otherwise.
//   */
//  public Boolean hasModifierProperty(@NotNull PsiModifierList modifierList, @NotNull final String name) {
//    if (DumbService.isDumb(modifierList.getProject())) {
//      return null;
//    }
//
//    final Set<String> modifiers = this.transformModifiers(modifierList, Collections.<String>emptySet());
//    if (modifiers.contains(name)) {
//      return Boolean.TRUE;
//    }
//
//    return null;
//  }
//


}
