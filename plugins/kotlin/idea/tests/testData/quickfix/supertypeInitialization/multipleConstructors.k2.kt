// "Add constructor parameters from ArrayList(kotlin.collections.(Mutable)Collection<out String!>)" "true"
// ACTION: Add constructor parameters from ArrayList((MutableCollection<out String!>..Collection<String!>?))
// ACTION: Add constructor parameters from ArrayList(Int)
// ACTION: Change to constructor invocation
// RUNTIME_WITH_FULL_JDK
// Issue: KTIJ-32887
import java.util.ArrayList

class C : ArrayList<String><caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.SuperClassNotInitializedFactories$AddParametersFix