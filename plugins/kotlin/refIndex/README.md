# Compiler Reference Index

Compiler reference index service – is a service that allows to reduce the search scope of a declaration usages for compiled projects.
  
**Basis**: a large codebase with many declarations and usages with common names like `getInstance`  
**Action**: find usages of some `getInstance` declaration  
**Problem**: a lot of false positive candidate files caused by text search  
**Solution**: extract usage information from the compiler to reduce the search scope 

## What should I do to get the maximum benefit of the index?
Compile project before searching.

## How can I check the benefit of the index?
We have [`Show Compiler Index Status`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexVerifierAction.kt#L30) internal action to check the difference between [`Use Scope`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/platform/indexing-api/src/com/intellij/psi/search/PsiSearchHelper.java#L119) and [`Code Usage Scope`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/platform/indexing-api/src/com/intellij/psi/search/PsiSearchHelper.java#L130).  
**Q: How to use it?**  
**A: Put the caret at the declaration and call the action** 

## Top level API
* [`PsiSearchHelper#getCodeUsageScope`](https://github.com/JetBrains/intellij-community/blob/db51da3b80ff9db7eb3f32664fa3d269019783c8/platform/indexing-api/src/com/intellij/psi/search/PsiSearchHelper.java#L130)
  * [`KotlinScopeOptimizer`](https://github.com/JetBrains/intellij-community/blob/e17a19b441ef0b2abf616b7241f713074056532c/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinScopeOptimizer.kt#L8) and [`JavaCompilerReferencesCodeUsageScopeOptimizer`](https://github.com/JetBrains/intellij-community/blob/7a7996a1cc1d9486278d48492b9847e405bc33d5/java/java-indexing-impl/src/com/intellij/compiler/JavaCompilerReferencesCodeUsageScopeOptimizer.java#L10) under the hood

## Structure questions

### Who writes the index?
* Kotlin – the Kotlin compiler by [`IncrementalJvmCache`](https://github.com/JetBrains/kotlin/blob/5d6e2b57a7a36ad179ebca89bd3be2fb74bff898/build-common/src/org/jetbrains/kotlin/incremental/IncrementalJvmCache.kt#L48)
* Java – the JPS [javac extension](https://github.com/JetBrains/jps-javac-extension) by [`JavaBackwardReferenceRegistrar`](https://github.com/JetBrains/intellij-community/blob/eb750d0cac9fbf13620ad1d4d008fbe1848bad00/jps/jps-builders/src/org/jetbrains/jps/backwardRefs/JavaBackwardReferenceRegistrar.java#L13)

### Who reads the index?
* Kotlin – [`KotlinCompilerReferenceIndexStorage`](https://github.com/JetBrains/intellij-community/blob/27ccdafb63454f4ebc354b0a53822ac6404e9af9/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexStorage.kt#L31)
* Java – [`BackwardReferenceReader`](https://github.com/JetBrains/intellij-community/blob/98b1604874a3b3963bcfd42734c628ed93bd7ab5/java/compiler/impl/src/com/intellij/compiler/backwardRefs/JavaBackwardReferenceIndexReaderFactory.java#L61)

### Which module is compiled?

#### The producer on the JPS plugin side
* Kotlin – [`KotlinCompilerReferenceIndexBuilder`](https://github.com/JetBrains/kotlin/blob/6b327d23808dc69c85c4b080df53f1fc70950cdc/jps/jps-plugin/src/org/jetbrains/kotlin/jps/incremental/KotlinCompilerReferenceIndexBuilder.kt#L21)
* Java – [`JavaBackwardReferenceIndexBuilder`](https://github.com/JetBrains/intellij-community/blob/fe4202b3fd26573a8e119ccb5622d85ae8ad3f73/jps/jps-builders/src/org/jetbrains/jps/backwardRefs/JavaBackwardReferenceIndexBuilder.java#L20)

#### The consumer on the IDE side
* Kotlin – [`DirtyScopeHolder`](https://github.com/JetBrains/intellij-community/blob/223ff19ba4e69df53a193c07f7a784355e8021ac/java/compiler/impl/src/com/intellij/compiler/backwardRefs/DirtyScopeHolder.java#L92) by [`KotlinCompilerReferenceIndexService`](https://github.com/JetBrains/intellij-community/blob/27ccdafb63454f4ebc354b0a53822ac6404e9af9/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexService.kt#L79)
* Java – [`DirtyScopeHolder`](https://github.com/JetBrains/intellij-community/blob/223ff19ba4e69df53a193c07f7a784355e8021ac/java/compiler/impl/src/com/intellij/compiler/backwardRefs/DirtyScopeHolder.java#L92) by [`CompilerReferenceServiceImpl`](https://github.com/JetBrains/intellij-community/blob/55086726aab18aa74fbbf168d82b18dfa38523ee/java/compiler/impl/src/com/intellij/compiler/backwardRefs/CompilerReferenceServiceImpl.java#L34)

## Service lifecycle

### Project startup
1. Preload [`CompilerReferenceServiceImpl`](https://github.com/JetBrains/intellij-community/blob/d95a516cc238034ad940f1dada4491287331a08b/java/java-impl/src/META-INF/JavaPlugin.xml#L316) and [`KotlinCompilerReferenceIndexService`](https://github.com/JetBrains/intellij-community/blob/1812072b842bb808d84eb1b71fbd1ab6bd84cdf6/plugins/kotlin/refIndex/resources/META-INF/refIndex.xml#L5) on the project startup
   1. Subscribe on the VFS events to [track](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/java/compiler/impl/src/com/intellij/compiler/backwardRefs/DirtyScopeHolder.java#L299) changed files
   2. Subscribe on [`BuildManagerListener`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/java/compiler/impl/src/com/intellij/compiler/server/BuildManagerListener.java#L13) to handle the start and the end of a build
   3. Subscribe on [`PortableCachesLoadListener`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/java/compiler/impl/src/com/intellij/compiler/server/PortableCachesLoadListener.java#L8) to drop the index to avoid JPS cache corruption
2. Schedule [`CompilerManager#isUpToDate`](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/java/compiler/openapi/src/com/intellij/openapi/compiler/CompilerManager.java#L197) check by [`IsUpToDateCheckStartupActivity`](https://github.com/JetBrains/intellij-community/blob/1812072b842bb808d84eb1b71fbd1ab6bd84cdf6/java/compiler/impl/src/com/intellij/compiler/backwardRefs/IsUpToDateCheckStartupActivity.kt#L15) as [`backgroundPostStartupActivity`](https://github.com/JetBrains/intellij-community/blob/d95a516cc238034ad940f1dada4491287331a08b/java/java-impl/src/META-INF/JavaPlugin.xml#L313)
   1. The check will be performed only if [Kotlin](https://github.com/JetBrains/intellij-community/blob/688bd4aa8d5c35ef514007244f76a5d30fd7b4d0/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexService.kt#L142) and/or [Java](https://github.com/JetBrains/intellij-community/blob/1812072b842bb808d84eb1b71fbd1ab6bd84cdf6/java/compiler/impl/src/com/intellij/compiler/backwardRefs/CompilerReferenceServiceBase.java#L129) can already have the index on the filesystem
   2. If the check returns `true` then the corresponding service will be marked as `up to date` ([Kotlin](https://github.com/JetBrains/intellij-community/blob/688bd4aa8d5c35ef514007244f76a5d30fd7b4d0/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexService.kt#L185), [Java](https://github.com/JetBrains/intellij-community/blob/1812072b842bb808d84eb1b71fbd1ab6bd84cdf6/java/compiler/impl/src/com/intellij/compiler/backwardRefs/CompilerReferenceServiceBase.java#L565)) 

### Build started
* Increase `activeBuildCount`
* Close the index storage if it is open

### Build finished
* Decrease `activeBuildCount`
* Remove compiled modules from the [dirty](#dirty-scope) scope
* If `activeBuildCount == 0` then open the index storage

## Dirty scope
**The dirty scope operates with modules**. All modules are dirty be default (except for [`up to date`](#project-startup) case).  
We should track changed files to provide the correct search scope. A change in the file causes the current and all dependent modules to be marked as "dirty".  
The scope also contains modules excluded from compilation.

## How to calculate the search scope?
The result search scope should contain files from:
1. the compiler index
2. the [dirty](#dirty-scope) scope
3. other languages
4. libraries if we should search in the libraries scope

The sources: [Kotlin](https://github.com/JetBrains/intellij-community/blob/52f97a3a5d9cfc9edf9675c1789447fb19831949/plugins/kotlin/refIndex/src/org/jetbrains/kotlin/idea/search/refIndex/KotlinCompilerReferenceIndexService.kt#L399) / [Java](https://github.com/JetBrains/intellij-community/blob/ecd9907190b8153404d523f32495d134f10438fb/java/compiler/impl/src/com/intellij/compiler/backwardRefs/CompilerReferenceServiceBase.java#L339).

## What is next?
* [KTIJ-19973](https://youtrack.jetbrains.com/issue/KTIJ-19973) – the optimized scope should include the places of declaration definitions as well
  * Motivation: this is required to optimize the search of declaration definitions like overrides
* [IDEA-281133](https://youtrack.jetbrains.com/issue/IDEA-281133) – Rewrite `CompilerReferenceService` and `KotlinCompilerReferenceIndexService` into one service
  * Problem: we have two related and similar services