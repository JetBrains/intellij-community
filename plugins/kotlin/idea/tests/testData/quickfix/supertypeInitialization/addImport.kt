// "Add constructor parameters from DataInputStream(InputStream!)" "true"
// RUNTIME_WITH_FULL_JDK
import java.io.DataInputStream

class C : DataInputStream<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix