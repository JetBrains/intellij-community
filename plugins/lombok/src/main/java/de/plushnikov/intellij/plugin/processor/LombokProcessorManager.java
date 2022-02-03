package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.processor.clazz.*;
import de.plushnikov.intellij.plugin.processor.clazz.builder.*;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.AllArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.NoArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.constructor.RequiredArgsConstructorProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsOldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsPredefinedInnerClassFieldProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.fieldnameconstants.FieldNameConstantsProcessor;
import de.plushnikov.intellij.plugin.processor.clazz.log.*;
import de.plushnikov.intellij.plugin.processor.field.*;
import de.plushnikov.intellij.plugin.processor.method.BuilderClassMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.BuilderMethodProcessor;
import de.plushnikov.intellij.plugin.processor.method.DelegateMethodProcessor;
import de.plushnikov.intellij.plugin.processor.modifier.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class LombokProcessorManager {

  private static final Map<String, Collection<Processor>> PROCESSOR_CACHE = new ConcurrentHashMap<>();

  private static Collection<Processor> getWithCache(String key, Supplier<Collection<Processor>> function) {
    return PROCESSOR_CACHE.computeIfAbsent(key, s -> function.get());
  }

  private static final Set<String> ourSupportedShortNames = getAllProcessors()
    .stream().flatMap(p -> Arrays.stream(p.getSupportedAnnotationClasses()))
    .map(StringUtil::getShortName)
    .collect(Collectors.toSet());

  @NotNull
  private static Collection<Processor> getAllProcessors() {
    Application application = ApplicationManager.getApplication();
    return Arrays.asList(
      application.getService(AllArgsConstructorProcessor.class),
      application.getService(NoArgsConstructorProcessor.class),
      application.getService(RequiredArgsConstructorProcessor.class),

      application.getService(LogProcessor.class),
      application.getService(Log4jProcessor.class),
      application.getService(Log4j2Processor.class),
      application.getService(Slf4jProcessor.class),
      application.getService(XSlf4jProcessor.class),
      application.getService(CommonsLogProcessor.class),
      application.getService(JBossLogProcessor.class),
      application.getService(FloggerProcessor.class),
      application.getService(CustomLogProcessor.class),

      application.getService(DataProcessor.class),
      application.getService(EqualsAndHashCodeProcessor.class),
      application.getService(GetterProcessor.class),
      application.getService(SetterProcessor.class),
      application.getService(ToStringProcessor.class),
      application.getService(WitherProcessor.class),

      application.getService(BuilderPreDefinedInnerClassFieldProcessor.class),
      application.getService(BuilderPreDefinedInnerClassMethodProcessor.class),
      application.getService(BuilderClassProcessor.class),
      application.getService(BuilderProcessor.class),
      application.getService(BuilderClassMethodProcessor.class),
      application.getService(BuilderMethodProcessor.class),

      application.getService(SuperBuilderPreDefinedInnerClassFieldProcessor.class),
      application.getService(SuperBuilderPreDefinedInnerClassMethodProcessor.class),
      application.getService(SuperBuilderClassProcessor.class),
      application.getService(SuperBuilderProcessor.class),

      application.getService(ValueProcessor.class),

      application.getService(UtilityClassProcessor.class),
      application.getService(StandardExceptionProcessor.class),

      application.getService(FieldNameConstantsOldProcessor.class),
      application.getService(FieldNameConstantsFieldProcessor.class),

      application.getService(FieldNameConstantsProcessor.class),
      application.getService(FieldNameConstantsPredefinedInnerClassFieldProcessor.class),

      application.getService(DelegateFieldProcessor.class),
      application.getService(GetterFieldProcessor.class),
      application.getService(SetterFieldProcessor.class),
      application.getService(WitherFieldProcessor.class),

      application.getService(DelegateMethodProcessor.class),

      application.getService(CleanupProcessor.class)
    );
  }

  @NotNull
  public static Collection<ModifierProcessor> getLombokModifierProcessors() {
    return Arrays.asList(new FieldDefaultsModifierProcessor(),
                         new UtilityClassModifierProcessor(),
                         new ValModifierProcessor(),
                         new ValueModifierProcessor());
  }

  @NotNull
  public static Collection<Processor> getProcessors(@NotNull Class<? extends PsiElement> supportedClass) {
    return getWithCache("bySupportedClass_" + supportedClass.getName(),
                        () -> ContainerUtil.filter(getAllProcessors(), p -> p.isSupportedClass(supportedClass))
    );
  }

  @NotNull
  public static Collection<Processor> getProcessors(@NotNull PsiAnnotation psiAnnotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = psiAnnotation.getNameReferenceElement();
    if (nameReferenceElement == null) {
      return Collections.emptyList();
    }
    String referenceName = nameReferenceElement.getReferenceName();
    if (referenceName == null || !ourSupportedShortNames.contains(referenceName)) {
      return Collections.emptyList();
    }
    final String qualifiedName = psiAnnotation.getQualifiedName();
    if (StringUtil.isEmpty(qualifiedName) || !qualifiedName.contains("lombok")) {
      return Collections.emptyList();
    }
    return getWithCache("byAnnotationFQN_" + qualifiedName,
                        () -> ContainerUtil.filter(getAllProcessors(), p -> p.isSupportedAnnotationFQN(qualifiedName))
    );
  }
}
