package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
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
import de.plushnikov.intellij.plugin.util.DumbIncompleteModeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public final class LombokProcessorManager {
  private final Map<String, Collection<Processor>> PROCESSOR_CACHE = new ConcurrentHashMap<>();

  private Collection<Processor> getWithCache(String key, Supplier<Collection<Processor>> function) {
    return PROCESSOR_CACHE.computeIfAbsent(key, s -> function.get());
  }

  private final AllArgsConstructorProcessor myAllArgsConstructorProcessor = new AllArgsConstructorProcessor();
  private final NoArgsConstructorProcessor myNoArgsConstructorProcessor = new NoArgsConstructorProcessor();
  private final RequiredArgsConstructorProcessor myRequiredArgsConstructorProcessor = new RequiredArgsConstructorProcessor();
  private final LogProcessor myLogProcessor = new LogProcessor();
  private final Log4jProcessor myLog4jProcessor = new Log4jProcessor();
  private final Log4j2Processor myLog4j2Processor = new Log4j2Processor();
  private final Slf4jProcessor mySlf4jProcessor = new Slf4jProcessor();
  private final XSlf4jProcessor myXSlf4jProcessor = new XSlf4jProcessor();
  private final CommonsLogProcessor myCommonsLogProcessor = new CommonsLogProcessor();
  private final JBossLogProcessor myJBossLogProcessor = new JBossLogProcessor();
  private final FloggerProcessor myFloggerProcessor = new FloggerProcessor();
  private final CustomLogProcessor myCustomLogProcessor = new CustomLogProcessor();
  private final DataProcessor myDataProcessor = new DataProcessor();
  private final EqualsAndHashCodeProcessor myEqualsAndHashCodeProcessor = new EqualsAndHashCodeProcessor();
  private final GetterProcessor myGetterProcessor = new GetterProcessor();
  private final SetterProcessor mySetterProcessor = new SetterProcessor();
  private final ToStringProcessor myToStringProcessor = new ToStringProcessor();
  private final WitherProcessor myWitherProcessor = new WitherProcessor();
  private final WithByProcessor myWithByProcessor = new WithByProcessor();
  private final WithByFieldProcessor myWithByFieldProcessor = new WithByFieldProcessor();
  private final BuilderPreDefinedInnerClassFieldProcessor myBuilderPreDefinedInnerClassFieldProcessor =
    new BuilderPreDefinedInnerClassFieldProcessor();
  private final BuilderPreDefinedInnerClassMethodProcessor myBuilderPreDefinedInnerClassMethodProcessor =
    new BuilderPreDefinedInnerClassMethodProcessor();
  private final BuilderClassProcessor myBuilderClassProcessor = new BuilderClassProcessor();
  private final BuilderProcessor myBuilderProcessor = new BuilderProcessor();
  private final BuilderClassMethodProcessor myBuilderClassMethodProcessor = new BuilderClassMethodProcessor();
  private final BuilderMethodProcessor myBuilderMethodProcessor = new BuilderMethodProcessor();
  private final SuperBuilderPreDefinedInnerClassFieldProcessor mySuperBuilderPreDefinedInnerClassFieldProcessor =
    new SuperBuilderPreDefinedInnerClassFieldProcessor();
  private final SuperBuilderPreDefinedInnerClassMethodProcessor mySuperBuilderPreDefinedInnerClassMethodProcessor =
    new SuperBuilderPreDefinedInnerClassMethodProcessor();
  private final SuperBuilderClassProcessor mySuperBuilderClassProcessor = new SuperBuilderClassProcessor();
  private final SuperBuilderProcessor mySuperBuilderProcessor = new SuperBuilderProcessor();
  private final ValueProcessor myValueProcessor = new ValueProcessor();
  private final UtilityClassProcessor myUtilityClassProcessor = new UtilityClassProcessor();
  private final StandardExceptionProcessor myStandardExceptionProcessor = new StandardExceptionProcessor();
  private final FieldNameConstantsOldProcessor myFieldNameConstantsOldProcessor = new FieldNameConstantsOldProcessor();
  private final FieldNameConstantsFieldProcessor myFieldNameConstantsFieldProcessor = new FieldNameConstantsFieldProcessor();
  private final FieldNameConstantsProcessor myFieldNameConstantsProcessor = new FieldNameConstantsProcessor();
  private final FieldNameConstantsPredefinedInnerClassFieldProcessor myFieldNameConstantsPredefinedInnerClassFieldProcessor =
    new FieldNameConstantsPredefinedInnerClassFieldProcessor();
  private final DelegateFieldProcessor myDelegateFieldProcessor = new DelegateFieldProcessor();
  private final GetterFieldProcessor myGetterFieldProcessor = new GetterFieldProcessor();
  private final SetterFieldProcessor mySetterFieldProcessor = new SetterFieldProcessor();
  private final WitherFieldProcessor myWitherFieldProcessor = new WitherFieldProcessor();
  private final DelegateMethodProcessor myDelegateMethodProcessor = new DelegateMethodProcessor();
  private final CleanupProcessor myCleanupProcessor = new CleanupProcessor();
  private final SynchronizedProcessor mySynchronizedProcessor = new SynchronizedProcessor();
  private final JacksonizedProcessor myJacksonizedProcessor = new JacksonizedProcessor();

  private final MultiMap<String, String> ourSupportedShortNames = createSupportedShortNames();

  private @NotNull MultiMap<String, String> createSupportedShortNames() {
    MultiMap<String, String> map = new MultiMap<>();
    for (Processor processor : getAllProcessors()) {
      for (String annotationClass : processor.getSupportedAnnotationClasses()) {
        map.putValue(StringUtil.getShortName(annotationClass), annotationClass);
      }
    }
    return map;
  }

  public WithByFieldProcessor getWithByFieldProcessor() {
    return myWithByFieldProcessor;
  }

  public static LombokProcessorManager getInstance() {
    return ApplicationManager.getApplication().getService(LombokProcessorManager.class);
  }

  public AllArgsConstructorProcessor getAllArgsConstructorProcessor() {
    return myAllArgsConstructorProcessor;
  }

  public NoArgsConstructorProcessor getNoArgsConstructorProcessor() {
    return myNoArgsConstructorProcessor;
  }

  public RequiredArgsConstructorProcessor getRequiredArgsConstructorProcessor() {
    return myRequiredArgsConstructorProcessor;
  }

  public LogProcessor getLogProcessor() {
    return myLogProcessor;
  }

  public Log4jProcessor getLog4jProcessor() {
    return myLog4jProcessor;
  }

  public Log4j2Processor getLog4j2Processor() {
    return myLog4j2Processor;
  }

  public Slf4jProcessor getSlf4jProcessor() {
    return mySlf4jProcessor;
  }

  public XSlf4jProcessor getXSlf4jProcessor() {
    return myXSlf4jProcessor;
  }

  public CommonsLogProcessor getCommonsLogProcessor() {
    return myCommonsLogProcessor;
  }

  public JBossLogProcessor getJBossLogProcessor() {
    return myJBossLogProcessor;
  }

  public FloggerProcessor getFloggerProcessor() {
    return myFloggerProcessor;
  }

  public CustomLogProcessor getCustomLogProcessor() {
    return myCustomLogProcessor;
  }

  public DataProcessor getDataProcessor() {
    return myDataProcessor;
  }

  public EqualsAndHashCodeProcessor getEqualsAndHashCodeProcessor() {
    return myEqualsAndHashCodeProcessor;
  }

  public GetterProcessor getGetterProcessor() {
    return myGetterProcessor;
  }

  public SetterProcessor getSetterProcessor() {
    return mySetterProcessor;
  }

  public ToStringProcessor getToStringProcessor() {
    return myToStringProcessor;
  }

  public WitherProcessor getWitherProcessor() {
    return myWitherProcessor;
  }

  public BuilderPreDefinedInnerClassFieldProcessor getBuilderPreDefinedInnerClassFieldProcessor() {
    return myBuilderPreDefinedInnerClassFieldProcessor;
  }

  public BuilderPreDefinedInnerClassMethodProcessor getBuilderPreDefinedInnerClassMethodProcessor() {
    return myBuilderPreDefinedInnerClassMethodProcessor;
  }

  public BuilderClassProcessor getBuilderClassProcessor() {
    return myBuilderClassProcessor;
  }

  public BuilderProcessor getBuilderProcessor() {
    return myBuilderProcessor;
  }

  public BuilderClassMethodProcessor getBuilderClassMethodProcessor() {
    return myBuilderClassMethodProcessor;
  }

  public BuilderMethodProcessor getBuilderMethodProcessor() {
    return myBuilderMethodProcessor;
  }

  public SuperBuilderPreDefinedInnerClassFieldProcessor getSuperBuilderPreDefinedInnerClassFieldProcessor() {
    return mySuperBuilderPreDefinedInnerClassFieldProcessor;
  }

  public SuperBuilderPreDefinedInnerClassMethodProcessor getSuperBuilderPreDefinedInnerClassMethodProcessor() {
    return mySuperBuilderPreDefinedInnerClassMethodProcessor;
  }

  public SuperBuilderClassProcessor getSuperBuilderClassProcessor() {
    return mySuperBuilderClassProcessor;
  }

  public SuperBuilderProcessor getSuperBuilderProcessor() {
    return mySuperBuilderProcessor;
  }

  public ValueProcessor getValueProcessor() {
    return myValueProcessor;
  }

  public UtilityClassProcessor getUtilityClassProcessor() {
    return myUtilityClassProcessor;
  }

  public StandardExceptionProcessor getStandardExceptionProcessor() {
    return myStandardExceptionProcessor;
  }

  public FieldNameConstantsOldProcessor getFieldNameConstantsOldProcessor() {
    return myFieldNameConstantsOldProcessor;
  }

  public FieldNameConstantsFieldProcessor getFieldNameConstantsFieldProcessor() {
    return myFieldNameConstantsFieldProcessor;
  }

  public FieldNameConstantsProcessor getFieldNameConstantsProcessor() {
    return myFieldNameConstantsProcessor;
  }

  public FieldNameConstantsPredefinedInnerClassFieldProcessor getFieldNameConstantsPredefinedInnerClassFieldProcessor() {
    return myFieldNameConstantsPredefinedInnerClassFieldProcessor;
  }

  public DelegateFieldProcessor getDelegateFieldProcessor() {
    return myDelegateFieldProcessor;
  }

  public GetterFieldProcessor getGetterFieldProcessor() {
    return myGetterFieldProcessor;
  }

  public SetterFieldProcessor getSetterFieldProcessor() {
    return mySetterFieldProcessor;
  }

  public WitherFieldProcessor getWitherFieldProcessor() {
    return myWitherFieldProcessor;
  }

  public DelegateMethodProcessor getDelegateMethodProcessor() {
    return myDelegateMethodProcessor;
  }

  public WithByProcessor getWithByProcessor() {
    return myWithByProcessor;
  }

  public CleanupProcessor getCleanupProcessor() {
    return myCleanupProcessor;
  }

  public SynchronizedProcessor getSynchronizedProcessor() {
    return mySynchronizedProcessor;
  }

  public JacksonizedProcessor getJacksonizedProcessor() {
    return myJacksonizedProcessor;
  }

  public MultiMap<String, String> getOurSupportedShortNames() {
    return ourSupportedShortNames;
  }

  private @NotNull Collection<Processor> getAllProcessors() {
    return Arrays.asList(
      myAllArgsConstructorProcessor,
      myNoArgsConstructorProcessor,
      myRequiredArgsConstructorProcessor,

      myLogProcessor,
      myLog4jProcessor,
      myLog4j2Processor,
      mySlf4jProcessor,
      myXSlf4jProcessor,
      myCommonsLogProcessor,
      myJBossLogProcessor,
      myFloggerProcessor,
      myCustomLogProcessor,

      myDataProcessor,
      myEqualsAndHashCodeProcessor,
      myGetterProcessor,
      mySetterProcessor,
      myToStringProcessor,
      myWitherProcessor,
      myWithByProcessor,

      myBuilderPreDefinedInnerClassFieldProcessor,
      myBuilderPreDefinedInnerClassMethodProcessor,
      myBuilderClassProcessor,
      myBuilderProcessor,
      myBuilderClassMethodProcessor,
      myBuilderMethodProcessor,

      mySuperBuilderPreDefinedInnerClassFieldProcessor,
      mySuperBuilderPreDefinedInnerClassMethodProcessor,
      mySuperBuilderClassProcessor,
      mySuperBuilderProcessor,

      myValueProcessor,

      myUtilityClassProcessor,
      myStandardExceptionProcessor,

      myFieldNameConstantsOldProcessor,
      myFieldNameConstantsFieldProcessor,

      myFieldNameConstantsProcessor,
      myFieldNameConstantsPredefinedInnerClassFieldProcessor,

      myDelegateFieldProcessor,
      myGetterFieldProcessor,
      mySetterFieldProcessor,
      myWitherFieldProcessor,
      myWithByFieldProcessor,

      myDelegateMethodProcessor,

      myCleanupProcessor,
      mySynchronizedProcessor,
      myJacksonizedProcessor
    );
  }

  public static @NotNull Collection<ModifierProcessor> getLombokModifierProcessors() {
    return Arrays.asList(new FieldDefaultsModifierProcessor(),
                         new UtilityClassModifierProcessor(),
                         new ValModifierProcessor(),
                         new ValueModifierProcessor());
  }

  public static @NotNull Collection<Processor> getProcessors(@NotNull Class<? extends PsiElement> supportedClass) {
    LombokProcessorManager manager = getInstance();
    return manager.getWithCache("bySupportedClass_" + supportedClass.getName(),
                                () -> ContainerUtil.filter(manager.getAllProcessors(), p -> p.isSupportedClass(supportedClass))
    );
  }

  public static @NotNull Collection<Processor> getProcessors(@NotNull PsiAnnotation psiAnnotation) {
    LombokProcessorManager manager = getInstance();

    PsiJavaCodeReferenceElement nameReferenceElement = psiAnnotation.getNameReferenceElement();
    if (nameReferenceElement == null) {
      return Collections.emptyList();
    }
    String referenceName = nameReferenceElement.getReferenceName();
    if (referenceName == null || !manager.ourSupportedShortNames.containsKey(referenceName)) {
      return Collections.emptyList();
    }
    String qualifiedName = psiAnnotation.getQualifiedName();
    if (DumbIncompleteModeUtil.isDumbOrIncompleteMode(psiAnnotation)) {
      qualifiedName = DumbIncompleteModeUtil.findLombokAnnotationQualifiedNameInDumbIncompleteMode(psiAnnotation);
    }
    if (StringUtil.isEmpty(qualifiedName) || !qualifiedName.contains("lombok")) {
      return Collections.emptyList();
    }
    String finalQualifiedName = qualifiedName;
    return manager.getWithCache("byAnnotationFQN_" + qualifiedName,
                                () -> ContainerUtil.filter(manager.getAllProcessors(), p -> p.isSupportedAnnotationFQN(finalQualifiedName))
    );
  }
}
