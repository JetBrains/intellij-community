# K2/K1 feature parity report


Generated on Thu Oct 03 11:27:07 CEST 2024

## K2 Success rate per category

Success rate is ratio of 
number of files successfully passed in a category 
to total number of files in this category.

| Status | Category | Success rate, % | Success files | Total files |
| -- | -- | --  | -- | -- |
 | :white_check_mark: | UNCATEGORIZED | 100 | 225 | 225 | 0 | 
 | :white_check_mark: | HIGHLIGHTING | 100 | 265 | 266 | 0 | 
 | :white_check_mark: | COMPLETION | 85 | 1456 | 1708 | 0 | 
 | :white_check_mark: | CODE_INSIGHT | 96 | 1982 | 2064 | 0 | 
 | :white_check_mark: | NAVIGATION | 100 | 91 | 91 | 0 | 
 | :white_check_mark: | FIND_USAGES | 100 | 458 | 458 | 0 | 
 | :white_check_mark: | REFACTORING | 99 | 363 | 365 | 0 | 
 | :white_check_mark: | RENAME_REFACTORING | 98 | 432 | 443 | 0 | 
 | :white_check_mark: | INLINE_REFACTORING | 98 | 456 | 463 | 0 | 
 | :x: | MOVE_REFACTORING | 76 | 175 | 229 | 0 | 
 | :white_check_mark: | EXTRACT_REFACTORING | 95 | 900 | 951 | 0 | 
 | :white_check_mark: | INSPECTIONS | 100 | 653 | 656 | 0 | 
 | :x: | INTENTIONS | 64 | 2254 | 3501 | 0 | 
 | :x: | QUICKFIXES | 73 | 2051 | 2791 | 0 | 
 | :white_check_mark: | DEBUGGER | 97 | 923 | 956 | 0 | 
 | :white_check_mark: | J2K | 89 | 1080 | 1220 | 0 | 
 | :white_check_mark: | ANALYSIS_API | 100 | 2 | 2 | 0 | 

## K2/K1 parity per category 

Success rate is ratio of 
number of files successfully passed in K2 in a category 
to number of files successfully passed in K1 in the same category.

| Status | Category | Success rate, % | K2 files | K1 files |
| -- | -- | --  | -- | -- |
 | :x: | UNCATEGORIZED | 14 | 225 | 1558 | 0 | 
 | :white_check_mark: | HIGHLIGHTING | 92 | 265 | 288 | 0 | 
 | :x: | COMPLETION | 66 | 1456 | 2192 | 0 | 
 | :x: | CODE_INSIGHT | 75 | 1982 | 2657 | 0 | 
 | :x: | NAVIGATION | 58 | 91 | 157 | 0 | 
 | :white_check_mark: | FIND_USAGES | 117 | 458 | 393 | 0 | 
 | :white_check_mark: | REFACTORING | 94 | 363 | 388 | 0 | 
 | :white_check_mark: | RENAME_REFACTORING | 104 | 432 | 414 | 0 | 
 | :white_check_mark: | INLINE_REFACTORING | 98 | 456 | 465 | 0 | 
 | :x: | MOVE_REFACTORING | 83 | 175 | 210 | 0 | 
 | :white_check_mark: | EXTRACT_REFACTORING | 136 | 900 | 662 | 0 | 
 | :x: | INSPECTIONS | 17 | 653 | 3850 | 0 | 
 | :x: | INTENTIONS | 59 | 2254 | 3826 | 0 | 
 | :x: | QUICKFIXES | 57 | 2051 | 3580 | 0 | 
 | :x: | SCRIPTS | 0 | 0 | 20 | 0 | 
 | :white_check_mark: | DEBUGGER | 91 | 923 | 1017 | 0 | 
 | :white_check_mark: | J2K | 89 | 1080 | 1220 | 0 | 
 | :white_check_mark: | ANALYSIS_API | 100 | 2 | 0 | 0 | 

## Shared cases
shared 15509 files out of 1140 cases

| Status | Case name | Success rate, % | K2 files | K1 files | Total files |
| -- | -- | --  | -- | -- | -- |
 | :x: | K2CodeFragmentCompletionHandlerTestGenerated | 0 | 0 | 6 | 6 | 
 | :white_check_mark: | [K2CodeFragmentHighlightingTestGenerated] | 89 | 25 | 28 | 28 | 
 | :x: | K2CodeFragmentHighlightingTestGenerated$Imports | 0 | 0 | 1 | 1 | 
 | :white_check_mark: | K2CodeFragmentHighlightingTestGenerated$CodeFragments | 93 | 25 | 27 | 27 | 
 | :white_check_mark: | [FirParameterInfoTestGenerated] | 94 | 119 | 127 | 127 | 
 | :x: | FirParameterInfoTestGenerated$WithLib3 | 0 | 0 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$ArrayAccess | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$FunctionCall | 93 | 84 | 90 | 90 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$Annotations | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$TypeArguments | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib1 | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib2 | 100 | 1 | 1 | 1 | 
 | :x: | [K2PsiUnifierTestGenerated] | 76 | 85 | 112 | 117 | 
 | :x: | K2PsiUnifierTestGenerated$CallableReferences | 0 | 0 | 3 | 3 | 
 | :x: | K2PsiUnifierTestGenerated$ClassesAndObjects | 0 | 0 | 6 | 6 | 
 | :x: | K2PsiUnifierTestGenerated$TypeParameters | 0 | 0 | 1 | 1 | 
 | :x: | K2PsiUnifierTestGenerated$Super | 25 | 1 | 4 | 4 | 
 | :x: | K2PsiUnifierTestGenerated$Assignments | 60 | 3 | 5 | 5 | 
 | :x: | K2PsiUnifierTestGenerated$Blocks | 67 | 2 | 3 | 3 | 
 | :x: | K2PsiUnifierTestGenerated$Types | 67 | 4 | 6 | 6 | 
 | :x: | K2PsiUnifierTestGenerated$Conventions | 69 | 9 | 13 | 13 | 
 | :x: | K2PsiUnifierTestGenerated$Uncategorized | 69 | 9 | 13 | 13 | 
 | :x: | K2PsiUnifierTestGenerated$Misc | 75 | 3 | 4 | 4 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Lambdas | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$This | 90 | 9 | 10 | 10 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Calls | 100 | 15 | 15 | 17 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Casts | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Invoke | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$LocalCallables | 100 | 5 | 5 | 6 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Expressions | 122 | 11 | 9 | 11 | 
 | :x: | [K2JavaToKotlinConverterSingleFileFullJDKTestGenerated] | 38 | 3 | 8 | 8 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Enum | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Collections | 40 | 2 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Issues | 50 | 1 | 2 | 2 | 
 | :white_check_mark: | [K2JavaToKotlinConverterSingleFileTestGenerated] | 89 | 1038 | 1165 | 1165 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Lambda | 0 | 0 | 2 | 2 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Projections | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ToArray | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$JavaStreamsApi | 11 | 1 | 9 | 9 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ToKotlinClasses | 17 | 1 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$RawGenerics | 25 | 1 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$AnonymousClass | 33 | 1 | 3 | 3 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TypeParameters | 40 | 6 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$MutableCollections | 46 | 6 | 13 | 13 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Types | 50 | 3 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayType | 55 | 6 | 11 | 11 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Inheritance | 60 | 3 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$IsOperator | 67 | 2 | 3 | 3 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayAccessExpression | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ClassExpression | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Identifier | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Enum | 76 | 19 | 25 | 25 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayInitializerExpression | 77 | 10 | 13 | 13 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Function | 78 | 35 | 45 | 45 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$BoxedType | 80 | 12 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$DocComments | 80 | 12 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ObjectLiteral | 80 | 4 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$MethodCallExpression | 83 | 19 | 23 | 23 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TryWithResource | 83 | 10 | 12 | 12 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Comments | 84 | 16 | 19 | 19 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$DetectProperties | 86 | 66 | 77 | 77 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Issues | 86 | 71 | 83 | 83 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$FunctionalInterfaces | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PostProcessing | 88 | 28 | 32 | 32 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Visibility | 92 | 12 | 13 | 13 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$AnonymousBlock | 93 | 14 | 15 | 15 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ImplicitCasts | 93 | 13 | 14 | 14 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$NewClassExpression | 93 | 14 | 15 | 15 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Field | 94 | 15 | 16 | 16 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$For | 94 | 51 | 54 | 54 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$TypeCastExpression | 94 | 17 | 18 | 18 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$KotlinApiAccess | 95 | 18 | 19 | 19 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$AssignmentExpression | 96 | 26 | 27 | 27 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Nullability | 98 | 52 | 53 | 53 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Annotations | 100 | 36 | 36 | 36 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$AssertStatement | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$BinaryExpression | 100 | 26 | 26 | 26 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Blocks | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$BreakStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$CallChainExpression | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$CaseConversion | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Class | 100 | 37 | 37 | 37 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ConditionalExpression | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Constructors | 100 | 45 | 45 | 45 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ContinueStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$DeclarationStatement | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$DoWhileStatement | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$EnhancedSwitchStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Equals | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ExplicitApiMode | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ForeachStatement | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Formatting | 100 | 13 | 13 | 13 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$IfStatement | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ImportStatement | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Interface | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$InvalidCode | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$JavaStandardMethods | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LabelStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LibraryUsage | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$List | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LiteralExpression | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LocalVariable | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Misc | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$NullabilityGenerics | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Overloads | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PackageStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ParenthesizedExpression | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PatternMatching | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PolyadicExpression | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PostfixOperator | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PreAndPostprocessorExtensions | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PrefixOperator | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Protected | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$RecordClass | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ReturnStatement | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Settings | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$SimplifyNegatedBinaryExpression | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$StaticMembers | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Strings | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$SuperExpression | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Switch | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$SwitchExpression | 100 | 17 | 17 | 17 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$SynchronizedStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$TextBlocks | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ThisExpression | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ThrowStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$TryStatement | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Uncategorized | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$VarArg | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$WhileStatement | 100 | 6 | 6 | 6 | 
 | :x: | [K2CodeFragmentCompletionTestGenerated] | 72 | 13 | 18 | 19 | 
 | :x: | K2CodeFragmentCompletionTestGenerated$RuntimeType | 17 | 1 | 6 | 6 | 
 | :white_check_mark: | K2CodeFragmentCompletionTestGenerated$Uncategorized | 100 | 12 | 12 | 13 | 
 | :x: | [HighLevelWeigherTestGenerated] | 69 | 85 | 123 | 125 | 
 | :x: | HighLevelWeigherTestGenerated$ExpectedInfo | 13 | 2 | 15 | 15 | 
 | :x: | HighLevelWeigherTestGenerated$TypesWithInstances | 29 | 2 | 7 | 7 | 
 | :x: | HighLevelWeigherTestGenerated$WithReturnType | 65 | 11 | 17 | 17 | 
 | :x: | HighLevelWeigherTestGenerated$Uncategorized | 81 | 46 | 57 | 59 | 
 | :x: | HighLevelWeigherTestGenerated$NoReturnType | 82 | 9 | 11 | 11 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ExpectedType | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ParameterNameAndType | 100 | 8 | 8 | 8 | 
 | :x: | K2MoveNestedTestGenerated | 50 | 29 | 58 | 58 | 
 | :x: | K2MultiModuleMoveTestGenerated | 50 | 13 | 26 | 31 | 
 | :x: | K2IdeK2MultiplatformCodeKotlinEvaluateExpressionTestGenerated | 67 | 8 | 12 | 12 | 
 | :x: | K2AutoImportTestGenerated | 69 | 22 | 32 | 32 | 
 | :x: | K2JvmBasicCompletionTestGenerated$Java | 70 | 43 | 61 | 66 | 
 | :x: | K2JavaAgainstKotlinBinariesCheckerTestGenerated | 76 | 29 | 38 | 38 | 
 | :x: | HighLevelBasicCompletionHandlerTestGenerated$Basic | 78 | 240 | 307 | 314 | 
 | :white_check_mark: | [K2MultiFileLocalInspectionTestGenerated] | 95 | 18 | 19 | 19 | 
 | :x: | K2MultiFileLocalInspectionTestGenerated$RedundantQualifierName | 80 | 4 | 5 | 5 | 
 | :white_check_mark: | K2MultiFileLocalInspectionTestGenerated$ReconcilePackageWithDirectory | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2MultiFileLocalInspectionTestGenerated$UnusedSymbol | 100 | 7 | 7 | 7 | 
 | :x: | K2CompletionCharFilterTestGenerated | 83 | 29 | 35 | 35 | 
 | :white_check_mark: | [FirJvmOptimizeImportsTestGenerated] | 91 | 147 | 162 | 162 | 
 | :white_check_mark: | FirJvmOptimizeImportsTestGenerated$Jvm | 86 | 49 | 57 | 57 | 
 | :white_check_mark: | FirJvmOptimizeImportsTestGenerated$Common | 93 | 98 | 105 | 105 | 
 | :white_check_mark: | K2ChangePackageTestGenerated | 86 | 6 | 7 | 7 | 
 | :white_check_mark: | FirWithLibBasicCompletionTestGenerated | 88 | 15 | 17 | 17 | 
 | :white_check_mark: | FirShortenRefsTestGenerated$This | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | [InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated] | 94 | 356 | 380 | 380 | 
 | :white_check_mark: | InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$MultipleBreakpoints | 90 | 46 | 51 | 51 | 
 | :white_check_mark: | InlineScopesAndK2IdeK2CodeEvaluateExpressionTestGenerated$SingleBreakpoint | 94 | 310 | 329 | 329 | 
 | :white_check_mark: | HighLevelMultiFileJvmBasicCompletionTestGenerated | 92 | 88 | 96 | 102 | 
 | :x: | [K2JavaToKotlinConverterPartialTestGenerated] | 83 | 39 | 47 | 47 | 
 | :x: | K2JavaToKotlinConverterPartialTestGenerated$Function | 79 | 27 | 34 | 34 | 
 | :white_check_mark: | K2JavaToKotlinConverterPartialTestGenerated$Field | 92 | 12 | 13 | 13 | 
 | :white_check_mark: | K2KDocCompletionTestGenerated | 93 | 28 | 30 | 30 | 
 | :white_check_mark: | K2JsBasicCompletionLegacyStdlibTestGenerated$Common | 94 | 601 | 637 | 672 | 
 | :white_check_mark: | [K2SelectExpressionForDebuggerTestGenerated] | 99 | 68 | 69 | 69 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$DisallowMethodCalls | 95 | 20 | 21 | 21 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$Uncategorized | 100 | 48 | 48 | 48 | 
 | :white_check_mark: | FirKeywordCompletionTestGenerated$Keywords | 98 | 137 | 140 | 140 | 
 | :white_check_mark: | FirQuickDocTestGenerated | 98 | 83 | 85 | 85 | 
 | :white_check_mark: | FirRenameTestGenerated | 98 | 273 | 278 | 278 | 
 | :white_check_mark: | [FirLegacyUastValuesTestGenerated] | 100 | 79 | 79 | 79 | 
 | :white_check_mark: | [FirUastDeclarationTestGenerated] | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | [FirUastTypesTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [FirUastValuesTestGenerated] | 100 | 2 | 2 | 2 | 
 | :x: | [K2AddImportActionTestGenerated] | 65 | 26 | 40 | 41 | 
 | :white_check_mark: | [K2BytecodeToolWindowTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [FirDumbCompletionTestGenerated] | 100 | 45 | 45 | 45 | 
 | :white_check_mark: | [FirKeywordCompletionHandlerTestGenerated] | 100 | 49 | 49 | 49 | 
 | :white_check_mark: | [HighLevelJavaCompletionHandlerTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2CompletionIncrementalResolveTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [FirLiteralKotlinToKotlinCopyPasteTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirLiteralTextToKotlinCopyPasteTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [K2CodeFragmentAutoImportTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2ExternalAnnotationTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [FindUsagesFirTestGenerated] | 100 | 300 | 299 | 300 | 
 | :white_check_mark: | [FindUsagesWithDisableComponentSearchFirTestGenerated] | 100 | 21 | 21 | 21 | 
 | :white_check_mark: | [KotlinFindUsagesWithLibraryFirTestGenerated] | 100 | 52 | 52 | 52 | 
 | :white_check_mark: | [KotlinFindUsagesWithStdlibFirTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFeaturesFirTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFirTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinScriptFindUsagesFirTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirFoldingTestGenerated] | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | [K2FilteringAutoImportTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [FirGotoTestGenerated] | 100 | 32 | 32 | 32 | 
 | :white_check_mark: | [K2ProjectViewTestGenerated] | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | [FirReferenceResolveInJavaTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirReferenceResolveTestGenerated] | 101 | 167 | 165 | 167 | 
 | :white_check_mark: | [FirReferenceToCompiledKotlinResolveInJavaTestGenerated] | 100 | 33 | 33 | 33 | 
 | :white_check_mark: | [ReferenceResolveInLibrarySourcesFirTestGenerated] | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | [KotlinCompilerReferenceFirTestGenerated] | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | [K2ExpressionTypeTestGenerated] | 100 | 37 | 37 | 37 | 
 | :white_check_mark: | [K2JavaAgainstKotlinSourceCheckerTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [KotlinFirBreadcrumbsTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [KotlinFirJoinLinesTestGenerated] | 100 | 89 | 89 | 89 | 
 | :white_check_mark: | [KotlinFirPairMatcherTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [SharedK2InspectionTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [SharedK2LocalInspectionTestGenerated] | 100 | 441 | 439 | 441 | 
 | :white_check_mark: | [SharedK2MultiFileQuickFixTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [SharedK2KDocHighlightingTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [SharedK2IntentionTestGenerated] | 100 | 484 | 484 | 484 | 
 | :white_check_mark: | [LineMarkersK2TestGenerated] | 102 | 47 | 46 | 47 | 
 | :x: | [K2PostfixTemplateTestGenerated] | 83 | 49 | 59 | 59 | 
 | :x: | [HighLevelQuickFixMultiFileTestGenerated] | 74 | 134 | 181 | 190 | 
 | :x: | [HighLevelQuickFixTestGenerated] | 72 | 1634 | 2279 | 2292 | 
 | :white_check_mark: | [K2InsertImportOnPasteTestGenerated] | 100 | 104 | 104 | 108 | 
 | :white_check_mark: | [FirUpdateKotlinCopyrightTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2BreakpointApplicabilityTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [K2ClassNameCalculatorTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2IdeK2CodeAsyncStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeContinuationStackTraceTestGenerated] | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | [K2IdeK2CodeCoroutineDumpTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeFileRankingTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [K2IdeK2CodeKotlinVariablePrintingTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [K2IdeK2CodeXCoroutinesStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IndyLambdaKotlinSteppingTestGenerated] | 99 | 346 | 350 | 350 | 
 | :white_check_mark: | [K2KotlinExceptionFilterTestGenerated] | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | [K2PositionManagerTestGenerated] | 100 | 20 | 20 | 20 | 
 | :white_check_mark: | [K2SmartStepIntoTestGenerated] | 100 | 47 | 47 | 47 | 
 | :white_check_mark: | [FirGenerateHashCodeAndEqualsActionTestGenerated] | 100 | 32 | 32 | 32 | 
 | :white_check_mark: | [FirGenerateSecondaryConstructorActionTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [FirGenerateTestSupportMethodActionTestGenerated] | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | [FirGenerateToStringActionTestGenerated] | 100 | 26 | 26 | 26 | 
 | :white_check_mark: | [K2HighlightExitPointsTestGenerated] | 100 | 53 | 53 | 53 | 
 | :white_check_mark: | [K2HighlightUsagesTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KtCallChainHintsProviderTestGenerated] | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | [KtLambdasHintsProviderGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [KtParameterHintsProviderTestGenerated] | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | [KtRangesHintsProviderTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KtReferenceTypeHintsProviderTestGenerated] | 100 | 42 | 42 | 42 | 
 | :white_check_mark: | [K2InspectionTestGenerated] | 94 | 16 | 17 | 17 | 
 | :white_check_mark: | [K2MultiFileInspectionTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2GotoTestOrCodeActionTestGenerated] | 100 | 11 | 11 | 11 | 
 | :x: | [K2IntentionTestGenerated] | 60 | 1750 | 2923 | 2997 | 
 | :white_check_mark: | [K2MultiFileIntentionTestGenerated] | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | [FirMoveLeftRightTestGenerated] | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | [KotlinFirMoveStatementTestGenerated] | 99 | 282 | 284 | 284 | 
 | :white_check_mark: | [KotlinGotoImplementationMultifileTestGenerated] | 109 | 12 | 11 | 12 | 
 | :white_check_mark: | [KotlinGotoImplementationTestGenerated] | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | [FirRenderingKDocTestGenerated] | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | [K2MultiFileQuickFixTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [K2QuickFixTestGenerated] | 92 | 249 | 271 | 275 | 
 | :white_check_mark: | [K2CopyTestGenerated] | 100 | 43 | 43 | 45 | 
 | :white_check_mark: | [K2MultiModuleCopyTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [KotlinFirInlineTestGenerated] | 100 | 456 | 456 | 463 | 
 | :white_check_mark: | [K2IntroduceFunctionTestGenerated] | 101 | 158 | 156 | 164 | 
 | :white_check_mark: | [K2IntroduceParameterTestGenerated] | 102 | 132 | 130 | 134 | 
 | :white_check_mark: | [K2IntroducePropertyTestGenerated] | 100 | 55 | 55 | 56 | 
 | :white_check_mark: | [K2IntroduceVariableTestGenerated] | 103 | 200 | 194 | 203 | 
 | :white_check_mark: | [K2MoveDirectoryTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [K2MovePackageTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2MoveTopLevelToInnerTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [FirMultiModuleRenameTestGenerated] | 100 | 26 | 26 | 29 | 
 | :white_check_mark: | [FirMultiModuleSafeDeleteTestGenerated] | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | [K2SafeDeleteTestGenerated] | 100 | 198 | 198 | 198 | 
 | :white_check_mark: | [FirAnnotatedMembersSearchTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinFirFileStructureTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [KotlinFirSurroundWithTestGenerated] | 100 | 77 | 77 | 77 | 
 | :white_check_mark: | [KotlinFirUnwrapRemoveTestGenerated] | 100 | 63 | 63 | 63 | 
 | :white_check_mark: | [ParcelizeK2QuickFixTestGenerated] | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | K2MoveTopLevelTestGenerated | 105 | 88 | 84 | 92 | 
 | :white_check_mark: | [K2UnusedSymbolHighlightingTestGenerated] | 108 | 154 | 142 | 155 | 
 | :white_check_mark: | K2UnusedSymbolHighlightingTestGenerated$Uncategorized | 108 | 130 | 120 | 131 | 
 | :white_check_mark: | K2UnusedSymbolHighlightingTestGenerated$Multifile | 109 | 24 | 22 | 24 | 
 | :white_check_mark: | FirGotoTypeDeclarationTestGenerated | 111 | 20 | 18 | 20 | 
 | :white_check_mark: | FirGotoDeclarationTestGenerated | 113 | 17 | 15 | 17 | 
 | :white_check_mark: | K2MoveFileOrDirectoriesTestGenerated | 115 | 31 | 27 | 33 | 
 | :white_check_mark: | K2SharedQuickFixTestGenerated$Quickfix | 117 | 7 | 6 | 7 | 
 | :white_check_mark: | K2InplaceRenameTestGenerated | 121 | 133 | 110 | 136 | 
 | :white_check_mark: | K2InplaceIntroduceFunctionTestGenerated | 125 | 15 | 12 | 15 | 
 | :white_check_mark: | [K2IntroduceConstantTestGenerated] | 94 | 44 | 47 | 48 | 
 | :x: | K2IntroduceConstantTestGenerated$BinaryExpression | 83 | 19 | 23 | 23 | 
 | :white_check_mark: | K2IntroduceConstantTestGenerated$DotQualifiedExpression | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2IntroduceConstantTestGenerated$StringTemplates | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2IntroduceConstantTestGenerated$Uncategorized | 125 | 5 | 4 | 5 | 
 | :white_check_mark: | [K2HighlightingMetaInfoTestGenerated] | 103 | 61 | 59 | 62 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Diagnostics | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Dsl | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$SmartCasts | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Uncategorized | 100 | 23 | 23 | 24 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Unresolved | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Jvm | 109 | 12 | 11 | 12 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$FocusMode | 150 | 3 | 2 | 3 | 

### Extensions

kt, test, before.Main.kt, before.Main.java, kts, main.java, main.kt, option1.kt, kt.kt, java, 0.kt, 0.java, 0.properties, txt, 0.kts, gradle.kts

---
## Total 
 * K1: 15232 rate: 98 % of 15509 files
 * K2: 12897 rate: 83 % of 15509 files
---

