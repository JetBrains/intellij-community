// Difference with K1: K2 renderer can handle enhanced nullability annotations
// "Add constructor parameters from DataInputStream(InputStream)" "true"
// RUNTIME_WITH_FULL_JDK
import java.io.DataInputStream

class C : DataInputStream<caret>

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix