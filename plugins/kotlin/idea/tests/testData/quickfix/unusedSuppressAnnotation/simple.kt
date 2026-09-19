// "Suppress unused warning if annotated by 'xxx.XXX'" "true"
package xxx

annotation class XXX

@XXX
class <caret>UnusedClass

// FUS_QUICKFIX_NAME: com.intellij.codeInspection.ex.EntryPointsManagerBase$AddAnnotation