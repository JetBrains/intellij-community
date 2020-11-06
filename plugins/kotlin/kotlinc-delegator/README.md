This directory contains number of `kotlinc-delegator` modules.

Basically, `kotlinc-delegator` modules can be in one of two states:
1. These modules are used in regular intellij branches (`master`, release/EAP branches like `203`, `203.1234`). In such a mode,
   these modules declare one transitive dependency on `kotlinc` Maven artifact.
2. These modules are used in `kt-` branches (e.g. `kt-203-1.4.20`, `kt-203-master`). Then these modules declare two transitive
   dependencies: one is transitive [JPS-Provided dependency](https://www.jetbrains.com/help/idea/working-with-module-dependencies.html#dependency-scope)
   on gradle module of Kotlin compiler (in order to be able to resolve into Kotlin compiler sources instead of binaries), second is 
   transitive dependency on Maven artifact produced by Kotlin compiler repo 

Second mode allows easy compiler bootstraping for easier parallel development of compiler and Kotlin plugin. 

So `kotlinc-delegator` modules exist for simplifying control over dependencies on Kotlin compiler. When you keep "evil" 
in one place then it's easier to control it. Also, it's used on TC for automatic switching between 1. and 2. modes of compilation

Whenever you want to depend on new `kotlinc` artifact you must:
1. Setup proper artifact publishing in Kotlin repo ([example](https://github.com/JetBrains/kotlin/commit/419c88d1f25880e002624f9fca6cd5cdd9c2745e))
2. Create new `kotlinc-delegator` module

Q: Some modules are unused. Is it intended?  
A: Yes, unused modules exist in order to force IDEA to download dependencies into Maven local.
These Maven local artifacts are used in `org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts`.
If you faced with situation when you want to depend on `kotlinc` artifact and there is already unused
`kotlinc-delegator` module then feel free to depend on the module. You will make it "used" :)
