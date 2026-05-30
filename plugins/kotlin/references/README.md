# intellij.kotlin.references

Public reference API for Kotlin PSI — `KtReference` and its subtypes (`KtSimpleNameReference`, `KDocReference`,
`KtInvokeFunctionReference`, `SyntheticPropertyAccessorReference`, …), together with related contracts such as
`ReadWriteAccessChecker`, `KtReferenceMutateService`, and the `SimpleNameReferenceExtension` extension point.

## Why this module exists

`KtReference` historically lived in the Kotlin Analysis API (shipped via `kotlinc.kotlin-compiler-common`). That
coupling forced every consumer of Kotlin references to depend on the full Analysis API even when they only needed the
reference shape. As part of [KT-84925](https://youtrack.jetbrains.com/issue/KT-84925) the public reference hierarchy is
being moved out of the Analysis API and into the IntelliJ Kotlin plugin — this module is its new home.

## Migration state

This is a transitional setup:

- **In the plugin (here):** the full public hierarchy plus the only real implementations
  (`org.jetbrains.kotlin.idea.references.impl.KaBase*`, the `psiReferenceProvider` registrations, the
  `ReadWriteAccessChecker` service, etc.). All production behavior lives in the plugin.
- **In the Analysis API:** a byte-for-byte identical `KtReference` declaration is kept temporarily as a bridge. It is
  there only so existing API signatures keep compiling — there are no implementations on that side anymore.

The Analysis API copy cannot be deleted yet because several public Analysis API entry points still accept or return
`KtReference` (or its subtypes) in their signatures. Removing it would be a breaking change for downstream consumers
that haven't migrated. The duplicate will be dropped once those signatures are updated and clients have been moved off
the Analysis API declaration.

## Consumer guidance

- Depend on `intellij.kotlin.references` (Bazel: `//plugins/kotlin/references`) instead of pulling references in
  through `kotlinc.kotlin-compiler-common`.
- Treat the Analysis API `KtReference` as deprecated-on-arrival: new code should reference the type from this module.
- Implementations of `KtReference` are not part of the public surface — they live in
  `intellij.kotlin.base.analysis.platform` under `org.jetbrains.kotlin.idea.references.impl` and are wired through
  extension points (`psiReferenceProvider`, `simpleNameReferenceExtension`, …). Do not depend on them directly.
