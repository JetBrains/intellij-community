// "Suppress 'FINAL_UPPER_BOUND' for class Some" "true"

class ReferenceInClassWhereConstraint
class Some<T> where T: ReferenceInClas<caret>sWhereConstraint
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressIntentionAction