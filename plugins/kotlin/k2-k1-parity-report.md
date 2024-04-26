# K2/K1 feature parity report


Generated on Thu Apr 25 21:55:15 CEST 2024

## Categories

| Status | Category | Success rate, % | K2 files | K1 files |
| -- | -- | --  | -- | -- |
 | :x: | UNCATEGORIZED | 14 | 209 | 1519 | 0 | 
 | :x: | HIGHLIGHTING | 78 | 221 | 285 | 0 | 
 | :x: | COMPLETION | 64 | 1367 | 2147 | 0 | 
 | :x: | CODE_INSIGHT | 56 | 1416 | 2547 | 0 | 
 | :x: | NAVIGATION | 61 | 87 | 142 | 0 | 
 | :white_check_mark: | FIND_USAGES | 106 | 412 | 387 | 0 | 
 | :x: | REFACTORING | 80 | 308 | 386 | 0 | 
 | :white_check_mark: | RENAME_REFACTORING | 103 | 418 | 405 | 0 | 
 | :x: | INLINE_REFACTORING | 76 | 332 | 439 | 0 | 
 | :x: | MOVE_REFACTORING | 66 | 128 | 193 | 0 | 
 | :white_check_mark: | EXTRACT_REFACTORING | 123 | 724 | 588 | 0 | 
 | :x: | INSPECTIONS | 26 | 986 | 3796 | 0 | 
 | :x: | INTENTIONS | 47 | 1769 | 3778 | 0 | 
 | :x: | QUICKFIXES | 25 | 892 | 3532 | 0 | 
 | :x: | SCRIPTS | 0 | 0 | 40 | 0 | 
 | :white_check_mark: | DEBUGGER | 90 | 861 | 961 | 0 | 
 | :x: | J2K | 50 | 577 | 1151 | 0 | 

## Shared cases
shared 14368 files out of 1094 cases

| Status | Case name | Success rate, % | K2 files | K1 files | Total files |
| -- | -- | --  | -- | -- | -- |
 | :white_check_mark: | [FirParameterInfoTestGenerated] | 94 | 118 | 126 | 126 | 
 | :x: | FirParameterInfoTestGenerated$WithLib3 | 0 | 0 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$ArrayAccess | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$FunctionCall | 93 | 83 | 89 | 89 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$Annotations | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$TypeArguments | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib1 | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib2 | 100 | 1 | 1 | 1 | 
 | :x: | [HighLevelQuickFixTestGenerated] | 31 | 729 | 2339 | 2348 | 
 | :x: | HighLevelQuickFixTestGenerated$AddAnnotationTarget | 0 | 0 | 35 | 35 | 
 | :x: | HighLevelQuickFixTestGenerated$AddAnnotationUseSiteTarget | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConstructorParameter | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConstructorParameterFromSuperTypeCall | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConversionCall | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AddDefaultConstructor | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddElseBranchToIf | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$AddEmptyArgumentList | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddEqEqTrue | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$AddGenericUpperBound | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$AddIsToWhenCondition | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddJvmInline | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddJvmStaticAnnotation | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddNewLineAfterAnnotations | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReifiedToTypeParameterOfFunctionFix | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnExpression | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnToLastExpressionInFunction | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnToUnusedLastExpressionInFunction | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddRunBeforeLambda | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddSemicolonBeforeLambdaExpression | 0 | 0 | 13 | 13 | 
 | :x: | HighLevelQuickFixTestGenerated$AddStarProjections | 0 | 0 | 47 | 47 | 
 | :x: | HighLevelQuickFixTestGenerated$AddSuspend | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddTypeAnnotationToValueParameter | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$AddUnsafeVarianceAnnotation | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AssignToProperty | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$CallFromPublicInline | 0 | 0 | 16 | 16 | 
 | :x: | HighLevelQuickFixTestGenerated$CanBeParameter | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$CanBePrimaryConstructorProperty | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$CastDueToProgressionResolveChange | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeObjectToClass | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeSignature | 0 | 0 | 64 | 64 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeSuperTypeListEntryTypeArgument | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToFunctionInvocation | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToLabeledReturn | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToMutableCollection | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToPropertyAccess | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToUseSpreadOperator | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$CompilerError | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertCollectionLiteralToIntArrayOf | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertIllegalEscapeToUnicodeEscape | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertLateinitPropertyToNotNullDelegate | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertPropertyInitializerToGetter | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertToAnonymousObject | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertToIsArrayOfCall | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$CreateFromUsage | 0 | 0 | 106 | 106 | 
 | :x: | HighLevelQuickFixTestGenerated$CreateLabel | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$DeclarationCantBeInlined | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$DeclaringJavaClass | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$DecreaseVisibility | 0 | 0 | 14 | 14 | 
 | :x: | HighLevelQuickFixTestGenerated$DeprecatedJavaAnnotation | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$DeprecatedSymbolUsage | 0 | 0 | 176 | 176 | 
 | :x: | HighLevelQuickFixTestGenerated$EqualityNotApplicable | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$Final | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$FoldTryCatch | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$FunctionWithLambdaExpressionBody | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$Implement | 0 | 0 | 39 | 39 | 
 | :x: | HighLevelQuickFixTestGenerated$ImportAlias | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$IncreaseVisibility | 0 | 0 | 33 | 33 | 
 | :x: | HighLevelQuickFixTestGenerated$InitializeWithConstructorParameter | 0 | 0 | 17 | 17 | 
 | :x: | HighLevelQuickFixTestGenerated$InlineClass | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$InlineTypeParameterFix | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$InsertDelegationCall | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$IsEnumEntry | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$JavaClassOnCompanion | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$LeakingThis | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$Libraries | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$MakeConstructorParameterProperty | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$MakeUpperBoundNonNullable | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$Migration | 0 | 0 | 46 | 46 | 
 | :x: | HighLevelQuickFixTestGenerated$MissingConstructorBrackets | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$MoveMemberToCompanionObject | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$MoveReceiverAnnotation | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$MoveToConstructorParameters | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$MoveTypeAliasToTopLevel | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$ObsoleteKotlinJsPackages | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$OptIn | 0 | 0 | 57 | 62 | 
 | :x: | HighLevelQuickFixTestGenerated$OptimizeImports | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$PlatformClasses | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$PlatformTypesInspection | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$PrimitiveCastToConversion | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$Properties | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$ProtectedInFinal | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantConst | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantFun | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantIf | 0 | 0 | 16 | 16 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantInline | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantLateinit | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantModalityModifier | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantSuspend | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$RedundantVisibilityModifier | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveAnnotation | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveArgument | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveAtFromAnnotationArgument | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveDefaultParameterValue | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveFinalUpperBound | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveNoConstructor | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveRedundantAssignment | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveRedundantInitializer | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveRedundantLabel | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveSingleLambdaParameter | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveSuspend | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveToStringInStringTemplate | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveTypeVariance | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUnusedParameter | 0 | 0 | 24 | 24 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUnusedReceiver | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUseSiteTarget | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameToRem | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameToUnderscore | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameUnresolvedReference | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$ReorderParameters | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceJvmFieldWithConst | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$RestrictedRetentionForExpressionAnnotation | 0 | 0 | 16 | 16 | 
 | :x: | HighLevelQuickFixTestGenerated$SmartCastImpossibleInIfThen | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$SpecifySuperExplicitly | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$SpecifyTypeExplicitly | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$SuperTypeIsExtensionType | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$Suppress | 0 | 0 | 88 | 89 | 
 | :x: | HighLevelQuickFixTestGenerated$SurroundWithNullCheck | 0 | 0 | 24 | 24 | 
 | :x: | HighLevelQuickFixTestGenerated$SuspiciousCollectionReassignment | 0 | 0 | 35 | 35 | 
 | :x: | HighLevelQuickFixTestGenerated$TooLongCharLiteralToString | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeImports | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeInferenceExpectedTypeMismatch | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeOfAnnotationMember | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeParameters | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeProjection | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$UnnecessaryLateinit | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$UnusedSuppressAnnotation | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$UseFullyqualifiedCall | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$Variables | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$WrapArgumentWithParentheses | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$WrapWhenExpressionInParentheses | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$WrongLongSuffix | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$YieldUnsupported | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddValVar | 10 | 1 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$Lateinit | 33 | 2 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$SurroundWithArrayOfForNamedArgumentsToVarargs | 44 | 4 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$SupertypeInitialization | 50 | 16 | 32 | 32 | 
 | :x: | HighLevelQuickFixTestGenerated$ParameterTypeMismatch | 53 | 9 | 17 | 17 | 
 | :x: | HighLevelQuickFixTestGenerated$AutoImports | 57 | 55 | 96 | 96 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeMismatch | 59 | 47 | 80 | 81 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceWithDotCall | 67 | 6 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$When | 73 | 27 | 37 | 38 | 
 | :x: | HighLevelQuickFixTestGenerated$Abstract | 74 | 26 | 35 | 35 | 
 | :x: | HighLevelQuickFixTestGenerated$Modifiers | 75 | 52 | 69 | 69 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeAddition | 75 | 15 | 20 | 20 | 
 | :x: | HighLevelQuickFixTestGenerated$Suspend | 77 | 10 | 13 | 13 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceWithArrayCallInAnnotation | 80 | 4 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$Nullables | 81 | 13 | 16 | 16 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ReplaceInfixOrOperatorCall | 86 | 19 | 22 | 22 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ReplaceWithSafeCall | 86 | 25 | 29 | 29 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddExclExclCall | 88 | 45 | 51 | 51 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$RemoveUnused | 89 | 25 | 28 | 28 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Expressions | 90 | 36 | 40 | 40 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddFunModifier | 91 | 10 | 11 | 11 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Override | 92 | 23 | 25 | 25 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ChangeMutability | 93 | 14 | 15 | 15 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$WrapWithSafeLetCall | 94 | 32 | 34 | 34 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$TypeMismatchOnOverride | 95 | 19 | 20 | 20 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$TypeMismatchOnReturnedExpression | 97 | 36 | 37 | 37 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddCrossinline | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddDataModifier | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddInitializer | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddInline | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddInlineToReifiedFunctionFix | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddNoinline | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddOpenToClassDeclaration | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddPropertyAccessors | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$CheckArguments | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ComponentFunctionReturnTypeMismatch | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ConflictingImports | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ConvertToBlockBody | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$LocalVariableWithTypeParameters | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$MakeTypeParameterReified | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$RemoveRedundantSpreadOperator | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$RemoveValVarFromParameter | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SimplifyComparison | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SpecifyOverrideExplicitly | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SpecifySuperType | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SpecifyVisibilityInExplicitApiMode | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Supercalls | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ToString | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$WrongPrimitive | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Casts | 108 | 13 | 12 | 13 | 
 | :x: | [K2IntentionTestGenerated] | 41 | 1131 | 2726 | 2744 | 
 | :x: | K2IntentionTestGenerated$AddAnnotationUseSiteTarget | 0 | 0 | 32 | 32 | 
 | :x: | K2IntentionTestGenerated$AddForLoopIndices | 0 | 0 | 14 | 14 | 
 | :x: | K2IntentionTestGenerated$AddJvmOverloads | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$AddJvmStatic | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$AddLabeledReturnInLambda | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$AddMissingDestructuring | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$AddThrowsAnnotation | 0 | 0 | 29 | 29 | 
 | :x: | K2IntentionTestGenerated$AddValOrVar | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$AnonymousFunctionToLambda | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$Branched | 0 | 0 | 104 | 104 | 
 | :x: | K2IntentionTestGenerated$ConvertArgumentToSet | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$ConvertArrayParameterToVararg | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$ConvertBlockCommentToLineComment | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertCamelCaseTestFunctionToSpaced | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertCollectionConstructorToFunction | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertEnumToSealedClass | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$ConvertFilteringFunctionWithDemorgansLaw | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionToProperty | 0 | 0 | 32 | 32 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionTypeParameterToReceiver | 0 | 0 | 19 | 19 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionTypeReceiverToParameter | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$ConvertLateinitPropertyToNullable | 0 | 0 | 4 | 4 | 
 | :x: | K2IntentionTestGenerated$ConvertLazyPropertyToOrdinary | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$ConvertNullablePropertyToLateinit | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ConvertObjectLiteralToClass | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertOrdinaryPropertyToLazy | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ConvertParameterToReceiver | 0 | 0 | 22 | 22 | 
 | :x: | K2IntentionTestGenerated$ConvertPrimaryConstructorToSecondary | 0 | 0 | 40 | 40 | 
 | :x: | K2IntentionTestGenerated$ConvertPropertyInitializerToGetter | 0 | 0 | 16 | 16 | 
 | :x: | K2IntentionTestGenerated$ConvertPropertyToFunction | 0 | 0 | 23 | 23 | 
 | :x: | K2IntentionTestGenerated$ConvertRangeCheckToTwoComparisons | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$ConvertReceiverToParameter | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ConvertSealedClassToEnum | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$ConvertSnakeCaseTestFunctionToSpaced | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertToIndexedFunctionCall | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$ConvertToScope | 0 | 0 | 105 | 105 | 
 | :x: | K2IntentionTestGenerated$ConvertTrimIndentToTrimMargin | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$ConvertTrimMarginToTrimIndent | 0 | 0 | 10 | 10 | 
 | :x: | K2IntentionTestGenerated$ConvertUnsafeCastCallToUnsafeCast | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertUnsafeCastToUnsafeCastCall | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertVarargParameterToArray | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertVariableAssignmentToExpression | 0 | 0 | 4 | 4 | 
 | :x: | K2IntentionTestGenerated$CopyConcatenatedStringToClipboard | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$Declarations | 0 | 0 | 63 | 63 | 
 | :x: | K2IntentionTestGenerated$DestructuringInLambda | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$DestructuringVariables | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$EvaluateCompileTimeExpression | 0 | 0 | 16 | 16 | 
 | :x: | K2IntentionTestGenerated$ExpandBooleanExpression | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$ImplementAbstractMember | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$ImplementAsConstructorParameter | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$InsertCurlyBracesToTemplate | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$IntroduceBackingProperty | 0 | 0 | 15 | 15 | 
 | :x: | K2IntentionTestGenerated$IntroduceImportAlias | 0 | 0 | 30 | 30 | 
 | :x: | K2IntentionTestGenerated$IntroduceVariable | 0 | 0 | 14 | 14 | 
 | :x: | K2IntentionTestGenerated$IterateExpression | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$IterationOverMap | 0 | 0 | 40 | 40 | 
 | :x: | K2IntentionTestGenerated$JoinDeclarationAndAssignment | 0 | 0 | 48 | 48 | 
 | :x: | K2IntentionTestGenerated$MergeElseIf | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$MoveDeclarationToSeparateFile | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$MoveLambdaInsideParentheses | 0 | 0 | 19 | 19 | 
 | :x: | K2IntentionTestGenerated$MoveMemberToTopLevel | 0 | 0 | 10 | 10 | 
 | :x: | K2IntentionTestGenerated$MoveOutOfCompanion | 0 | 0 | 10 | 10 | 
 | :x: | K2IntentionTestGenerated$MovePropertyToClassBody | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$MoveToCompanion | 0 | 0 | 22 | 22 | 
 | :x: | K2IntentionTestGenerated$NullableBooleanEqualityCheckToElvis | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ObjectLiteralToLambda | 0 | 0 | 30 | 30 | 
 | :x: | K2IntentionTestGenerated$ReconstructTypeInCastOrIs | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$RemoveEmptyPrimaryConstructor | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$RemoveExplicitLambdaParameterTypes | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$RemoveExplicitSuperQualifier | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$RemoveForLoopIndices | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$RemoveSingleExpressionStringTemplate | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ReplaceAddWithPlusAssign | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ReplaceExplicitFunctionLiteralParamWithIt | 0 | 0 | 24 | 24 | 
 | :x: | K2IntentionTestGenerated$ReplaceItWithExplicitFunctionLiteralParam | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ReplaceMapGetOrDefault | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ReplaceSizeCheckWithIsNotEmpty | 0 | 0 | 21 | 21 | 
 | :x: | K2IntentionTestGenerated$ReplaceSizeZeroCheckWithIsEmpty | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ReplaceTypeArgumentWithUnderscore | 0 | 0 | 31 | 31 | 
 | :x: | K2IntentionTestGenerated$ReplaceUnderscoreWithParameterName | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$ReplaceUntilWithRangeTo | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ReplaceWithOrdinaryAssignment | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$SamConversionToAnonymousObject | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$SimplifyBooleanWithConstants | 0 | 0 | 39 | 39 | 
 | :x: | K2IntentionTestGenerated$SwapStringEqualsIgnoreCase | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ToInfixCall | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$ToOrdinaryStringLiteral | 0 | 0 | 28 | 28 | 
 | :x: | K2IntentionTestGenerated$UsePropertyAccessSyntax | 0 | 0 | 57 | 57 | 
 | :x: | K2IntentionTestGenerated$UseWithIndex | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ValToObject | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ConvertLambdaToReference | 71 | 96 | 136 | 136 | 
 | :white_check_mark: | K2IntentionTestGenerated$ImportMember | 95 | 21 | 22 | 22 | 
 | :white_check_mark: | K2IntentionTestGenerated$SpecifyExplicitLambdaSignature | 95 | 18 | 19 | 19 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertReferenceToLambda | 98 | 46 | 47 | 47 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToBlockBody | 98 | 40 | 41 | 41 | 
 | :white_check_mark: | K2IntentionTestGenerated$SpecifyTypeExplicitly | 98 | 47 | 48 | 48 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddFullQualifier | 100 | 51 | 51 | 51 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddMissingClassKeyword | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNameToArgument | 100 | 30 | 30 | 30 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesInCommentToJavaCallArguments | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesToCallArguments | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesToFollowingArguments | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddOpenModifier | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddPropertyAccessors | 100 | 46 | 46 | 46 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddWhenRemainingBranches | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2IntentionTestGenerated$Chop | 100 | 22 | 22 | 22 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertBinaryExpressionWithDemorgansLaw | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertConcatenationToBuildString | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertForEachToForLoop | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertPropertyGetterToInitializer | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertStringTemplateToBuildString | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToConcatenatedString | 100 | 37 | 37 | 37 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToRawStringTemplate | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2IntentionTestGenerated$ImportAllMembers | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | K2IntentionTestGenerated$InvertIfCondition | 100 | 59 | 59 | 59 | 
 | :white_check_mark: | K2IntentionTestGenerated$JoinArgumentList | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | K2IntentionTestGenerated$JoinParameterList | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | K2IntentionTestGenerated$LambdaToAnonymousFunction | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | K2IntentionTestGenerated$MergeIfs | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$MovePropertyToConstructor | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveAllArgumentNames | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveExplicitTypeArguments | 100 | 37 | 37 | 37 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveSingleArgumentName | 100 | 21 | 21 | 21 | 
 | :white_check_mark: | K2IntentionTestGenerated$ReplaceUnderscoreWithTypeArgument | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | K2IntentionTestGenerated$ToRawStringLiteral | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$TrailingComma | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2IntentionTestGenerated$ChangeVisibility | 101 | 90 | 89 | 90 | 
 | :white_check_mark: | K2IntentionTestGenerated$InsertExplicitTypeArguments | 103 | 31 | 30 | 31 | 
 | :white_check_mark: | K2IntentionTestGenerated$IfToWhen | 107 | 45 | 42 | 45 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveExplicitType | 115 | 62 | 54 | 66 | 
 | :white_check_mark: | K2IntentionTestGenerated$Flatten | 133 | 4 | 3 | 4 | 
 | :x: | [K2PsiUnifierTestGenerated] | 76 | 83 | 109 | 114 | 
 | :x: | K2PsiUnifierTestGenerated$CallableReferences | 0 | 0 | 3 | 3 | 
 | :x: | K2PsiUnifierTestGenerated$ClassesAndObjects | 0 | 0 | 6 | 6 | 
 | :x: | K2PsiUnifierTestGenerated$TypeParameters | 0 | 0 | 1 | 1 | 
 | :x: | K2PsiUnifierTestGenerated$Super | 25 | 1 | 4 | 4 | 
 | :x: | K2PsiUnifierTestGenerated$Assignments | 60 | 3 | 5 | 5 | 
 | :x: | K2PsiUnifierTestGenerated$Blocks | 67 | 2 | 3 | 3 | 
 | :x: | K2PsiUnifierTestGenerated$Types | 67 | 4 | 6 | 6 | 
 | :x: | K2PsiUnifierTestGenerated$Conventions | 69 | 9 | 13 | 13 | 
 | :x: | K2PsiUnifierTestGenerated$Uncategorized | 69 | 9 | 13 | 13 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Lambdas | 86 | 6 | 7 | 7 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$This | 90 | 9 | 10 | 10 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Calls | 100 | 15 | 15 | 17 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Casts | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Invoke | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$LocalCallables | 100 | 5 | 5 | 6 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Misc | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2PsiUnifierTestGenerated$Expressions | 125 | 10 | 8 | 10 | 
 | :x: | [K2JavaToKotlinConverterSingleFileFullJDKTestGenerated] | 13 | 1 | 8 | 8 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Collections | 0 | 0 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Enum | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileFullJDKTestGenerated$Issues | 50 | 1 | 2 | 2 | 
 | :x: | [K2JavaToKotlinConverterSingleFileTestGenerated] | 51 | 555 | 1096 | 1096 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$AnonymousClass | 0 | 0 | 3 | 3 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ExplicitApiMode | 0 | 0 | 2 | 2 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$JavaStandardMethods | 0 | 0 | 3 | 3 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$JavaStreamsApi | 0 | 0 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$LabelStatement | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Lambda | 0 | 0 | 2 | 2 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$List | 0 | 0 | 2 | 2 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ObjectLiteral | 0 | 0 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$PatternMatching | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Projections | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$RawGenerics | 0 | 0 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$RecordClass | 0 | 0 | 16 | 16 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Strings | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ToArray | 0 | 0 | 1 | 1 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ToKotlinClasses | 0 | 0 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TryStatement | 0 | 0 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Visibility | 0 | 0 | 12 | 12 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$DetectProperties | 8 | 6 | 74 | 74 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Overloads | 13 | 1 | 8 | 8 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Equals | 14 | 1 | 7 | 7 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$CallChainExpression | 17 | 1 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TypeParameters | 20 | 3 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$MutableCollections | 23 | 3 | 13 | 13 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$InvalidCode | 25 | 1 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TryWithResource | 25 | 3 | 12 | 12 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$TypeCastExpression | 26 | 5 | 19 | 19 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayType | 27 | 3 | 11 | 11 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$PostProcessing | 31 | 9 | 29 | 29 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Constructors | 33 | 15 | 45 | 45 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Types | 33 | 2 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Enum | 36 | 9 | 25 | 25 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Function | 38 | 17 | 45 | 45 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Issues | 38 | 32 | 84 | 84 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Inheritance | 40 | 2 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$SuperExpression | 43 | 3 | 7 | 7 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Nullability | 46 | 18 | 39 | 39 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$AnonymousBlock | 47 | 7 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayInitializerExpression | 50 | 6 | 12 | 12 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$FunctionalInterfaces | 50 | 4 | 8 | 8 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Identifier | 50 | 2 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Misc | 50 | 3 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$MethodCallExpression | 52 | 12 | 23 | 23 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$NewClassExpression | 53 | 8 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Switch | 56 | 9 | 16 | 16 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Comments | 58 | 11 | 19 | 19 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$AssertStatement | 60 | 3 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Field | 63 | 10 | 16 | 16 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$KotlinApiAccess | 63 | 12 | 19 | 19 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$SwitchExpression | 65 | 11 | 17 | 17 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Interface | 67 | 8 | 12 | 12 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$IsOperator | 67 | 2 | 3 | 3 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$SimplifyNegatedBinaryExpression | 67 | 4 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$AssignmentExpression | 70 | 19 | 27 | 27 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Annotations | 73 | 24 | 33 | 33 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$BinaryExpression | 73 | 19 | 26 | 26 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$BoxedType | 73 | 11 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$DocComments | 73 | 11 | 15 | 15 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Formatting | 73 | 8 | 11 | 11 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ArrayAccessExpression | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ClassExpression | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$IfStatement | 75 | 6 | 8 | 8 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$Settings | 75 | 3 | 4 | 4 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$StaticMembers | 78 | 7 | 9 | 9 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ImportStatement | 80 | 4 | 5 | 5 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ImplicitCasts | 82 | 9 | 11 | 11 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$DeclarationStatement | 83 | 5 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$For | 83 | 43 | 52 | 52 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ForeachStatement | 83 | 5 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$PrefixOperator | 83 | 5 | 6 | 6 | 
 | :x: | K2JavaToKotlinConverterSingleFileTestGenerated$ReturnStatement | 83 | 5 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Protected | 86 | 6 | 7 | 7 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Class | 89 | 33 | 37 | 37 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LocalVariable | 89 | 8 | 9 | 9 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$TextBlocks | 90 | 9 | 10 | 10 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LiteralExpression | 95 | 18 | 19 | 19 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Blocks | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$BreakStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$CaseConversion | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ConditionalExpression | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ContinueStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$DoWhileStatement | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$EnhancedSwitchStatement | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$LibraryUsage | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PackageStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ParenthesizedExpression | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PolyadicExpression | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$PostfixOperator | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$SynchronizedStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ThisExpression | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$ThrowStatement | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$Uncategorized | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$VarArg | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2JavaToKotlinConverterSingleFileTestGenerated$WhileStatement | 100 | 6 | 6 | 6 | 
 | :x: | FindUsagesWithDisableComponentSearchFirTestGenerated | 5 | 1 | 21 | 21 | 
 | :x: | K2MultiModuleMoveTestGenerated | 23 | 6 | 26 | 26 | 
 | :x: | [HighLevelWeigherTestGenerated] | 69 | 85 | 124 | 125 | 
 | :x: | HighLevelWeigherTestGenerated$ExpectedInfo | 13 | 2 | 15 | 15 | 
 | :x: | HighLevelWeigherTestGenerated$TypesWithInstances | 29 | 2 | 7 | 7 | 
 | :x: | HighLevelWeigherTestGenerated$WithReturnType | 65 | 11 | 17 | 17 | 
 | :x: | HighLevelWeigherTestGenerated$Uncategorized | 79 | 46 | 58 | 59 | 
 | :x: | HighLevelWeigherTestGenerated$NoReturnType | 82 | 9 | 11 | 11 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ExpectedType | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ParameterNameAndType | 100 | 8 | 8 | 8 | 
 | :x: | [FirJvmOptimizeImportsTestGenerated] | 71 | 85 | 120 | 120 | 
 | :x: | FirJvmOptimizeImportsTestGenerated$Jvm | 44 | 21 | 48 | 48 | 
 | :white_check_mark: | FirJvmOptimizeImportsTestGenerated$Common | 89 | 64 | 72 | 72 | 
 | :x: | [K2JavaToKotlinConverterPartialTestGenerated] | 45 | 21 | 47 | 47 | 
 | :x: | K2JavaToKotlinConverterPartialTestGenerated$Function | 44 | 15 | 34 | 34 | 
 | :x: | K2JavaToKotlinConverterPartialTestGenerated$Field | 46 | 6 | 13 | 13 | 
 | :x: | K2MoveNestedTestGenerated | 50 | 29 | 58 | 58 | 
 | :x: | K2AutoImportTestGenerated | 64 | 18 | 28 | 28 | 
 | :x: | SharedK2MultiFileQuickFixTestGenerated | 67 | 2 | 3 | 3 | 
 | :x: | K2JvmBasicCompletionTestGenerated$Java | 68 | 41 | 60 | 65 | 
 | :x: | HighLevelBasicCompletionHandlerTestGenerated$Basic | 76 | 214 | 283 | 289 | 
 | :white_check_mark: | [K2MultiFileLocalInspectionTestGenerated] | 95 | 18 | 19 | 19 | 
 | :x: | K2MultiFileLocalInspectionTestGenerated$RedundantQualifierName | 80 | 4 | 5 | 5 | 
 | :white_check_mark: | K2MultiFileLocalInspectionTestGenerated$ReconcilePackageWithDirectory | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2MultiFileLocalInspectionTestGenerated$UnusedSymbol | 100 | 7 | 7 | 7 | 
 | :x: | K2MoveFileTestGenerated | 81 | 17 | 21 | 22 | 
 | :white_check_mark: | [KotlinFirInlineTestGenerated] | 98 | 332 | 338 | 339 | 
 | :x: | KotlinFirInlineTestGenerated$ExplicateTypeArgument | 60 | 6 | 10 | 10 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$InlineVariableOrProperty | 97 | 33 | 34 | 34 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$NamedFunction | 99 | 87 | 88 | 89 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$AddParenthesis | 100 | 32 | 32 | 32 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$AnonymousFunction | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$ExplicateParameterTypes | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$ExpressionBody | 100 | 40 | 40 | 40 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$FromIntellij | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$LambdaExpression | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$Property | 100 | 28 | 28 | 28 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$ReturnAtEnd | 100 | 46 | 46 | 46 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$StringTemplates | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | FirWithLibBasicCompletionTestGenerated | 88 | 15 | 17 | 17 | 
 | :white_check_mark: | FirShortenRefsTestGenerated$This | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | K2ChangePackageTestGenerated | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | K2MoveTopLevelTestGenerated | 88 | 65 | 74 | 75 | 
 | :white_check_mark: | FirMultiModuleRenameTestGenerated | 88 | 23 | 26 | 26 | 
 | :white_check_mark: | K2CompletionCharFilterTestGenerated | 89 | 31 | 35 | 35 | 
 | :white_check_mark: | HighLevelMultiFileJvmBasicCompletionTestGenerated | 90 | 80 | 89 | 94 | 
 | :white_check_mark: | FirKeywordCompletionTestGenerated$Keywords | 91 | 127 | 139 | 139 | 
 | :white_check_mark: | K2JsBasicCompletionLegacyStdlibTestGenerated$Common | 92 | 573 | 626 | 655 | 
 | :white_check_mark: | [K2IdeK2CodeKotlinEvaluateExpressionTestGenerated] | 93 | 327 | 353 | 353 | 
 | :white_check_mark: | K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$MultipleBreakpoints | 92 | 33 | 36 | 36 | 
 | :white_check_mark: | K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint | 93 | 294 | 317 | 317 | 
 | :white_check_mark: | FirMultiModuleSafeDeleteTestGenerated | 92 | 23 | 25 | 25 | 
 | :white_check_mark: | K2KDocCompletionTestGenerated | 93 | 28 | 30 | 30 | 
 | :white_check_mark: | [K2SelectExpressionForDebuggerTestGenerated] | 99 | 68 | 69 | 69 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$DisallowMethodCalls | 95 | 20 | 21 | 21 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$Uncategorized | 100 | 48 | 48 | 48 | 
 | :white_check_mark: | FirQuickDocTestGenerated | 96 | 73 | 76 | 76 | 
 | :white_check_mark: | FirRenameTestGenerated | 98 | 273 | 278 | 278 | 
 | :white_check_mark: | [FirLegacyUastValuesTestGenerated] | 100 | 79 | 79 | 79 | 
 | :white_check_mark: | [FirUastDeclarationTestGenerated] | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | [FirUastTypesTestGenerated] | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | [FirUastValuesTestGenerated] | 100 | 2 | 2 | 2 | 
 | :x: | [K2AddImportActionTestGenerated] | 62 | 24 | 39 | 39 | 
 | :white_check_mark: | [K2BytecodeToolWindowTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [FirDumbCompletionTestGenerated] | 100 | 45 | 45 | 45 | 
 | :white_check_mark: | [FirKeywordCompletionHandlerTestGenerated] | 100 | 49 | 49 | 49 | 
 | :white_check_mark: | [HighLevelJavaCompletionHandlerTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2CompletionIncrementalResolveTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [FirLiteralKotlinToKotlinCopyPasteTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirLiteralTextToKotlinCopyPasteTestGenerated] | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | [K2ExternalAnnotationTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [FindUsagesFirTestGenerated] | 94 | 274 | 293 | 294 | 
 | :white_check_mark: | [KotlinFindUsagesWithLibraryFirTestGenerated] | 100 | 52 | 52 | 52 | 
 | :white_check_mark: | [KotlinFindUsagesWithStdlibFirTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFeaturesFirTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFirTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinScriptFindUsagesFirTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirFoldingTestGenerated] | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | [K2FilteringAutoImportTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [FirGotoTestGenerated] | 97 | 28 | 29 | 29 | 
 | :white_check_mark: | [K2ProjectViewTestGenerated] | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | [FirReferenceResolveInJavaTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirReferenceResolveTestGenerated] | 101 | 166 | 164 | 166 | 
 | :white_check_mark: | [FirReferenceToCompiledKotlinResolveInJavaTestGenerated] | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | [ReferenceResolveInLibrarySourcesFirTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [KotlinCompilerReferenceFirTestGenerated] | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | [KotlinFirBreadcrumbsTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [K2SharedQuickFixTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [SharedK2InspectionTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [SharedK2LocalInspectionTestGenerated] | 100 | 424 | 424 | 424 | 
 | :white_check_mark: | [SharedK2KDocHighlightingTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [SharedK2IntentionTestGenerated] | 100 | 455 | 455 | 455 | 
 | :white_check_mark: | [LineMarkersK2TestGenerated] | 100 | 46 | 46 | 46 | 
 | :x: | [K2PostfixTemplateTestGenerated] | 67 | 39 | 58 | 58 | 
 | :x: | [HighLevelQuickFixMultiFileTestGenerated] | 67 | 113 | 168 | 176 | 
 | :white_check_mark: | [FirUpdateKotlinCopyrightTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2BreakpointApplicabilityTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2ClassNameCalculatorTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2IdeK2CodeAsyncStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeContinuationStackTraceTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [K2IdeK2CodeCoroutineDumpTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeFileRankingTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [K2IdeK2CodeKotlinVariablePrintingTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [K2IdeK2CodeXCoroutinesStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IndyLambdaKotlinSteppingTestGenerated] | 100 | 325 | 325 | 325 | 
 | :white_check_mark: | [K2KotlinExceptionFilterTestGenerated] | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | [K2PositionManagerTestGenerated] | 100 | 20 | 20 | 20 | 
 | :white_check_mark: | [K2SmartStepIntoTestGenerated] | 100 | 46 | 46 | 46 | 
 | :white_check_mark: | [Fe10BindingIntentionTestGenerated] | 101 | 168 | 167 | 168 | 
 | :white_check_mark: | [Fe10BindingLocalInspectionTestGenerated] | 100 | 368 | 368 | 368 | 
 | :white_check_mark: | [Fe10BindingQuickFixTestGenerated] | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | [K2HighlightExitPointsTestGenerated] | 100 | 53 | 53 | 53 | 
 | :white_check_mark: | [K2HighlightUsagesTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KtCallChainHintsProviderTestGenerated] | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | [KtLambdasHintsProviderGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [KtParameterHintsProviderTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [KtRangesHintsProviderTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KtReferenceTypeHintsProviderTestGenerated] | 100 | 39 | 39 | 39 | 
 | :white_check_mark: | [K2InspectionTestGenerated] | 87 | 13 | 15 | 15 | 
 | :white_check_mark: | [K2MultiFileInspectionTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2GotoTestOrCodeActionTestGenerated] | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | [FirMoveLeftRightTestGenerated] | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | [KotlinFirMoveStatementTestGenerated] | 99 | 281 | 283 | 283 | 
 | :white_check_mark: | [K2MultiFileQuickFixTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [K2QuickFixTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [K2IntroduceFunctionTestGenerated] | 98 | 150 | 153 | 156 | 
 | :white_check_mark: | [K2IntroduceParameterTestGenerated] | 99 | 82 | 83 | 84 | 
 | :white_check_mark: | [K2IntroducePropertyTestGenerated] | 98 | 53 | 54 | 54 | 
 | :white_check_mark: | [K2IntroduceVariableTestGenerated] | 100 | 151 | 151 | 154 | 
 | :white_check_mark: | [K2MovePackageTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2MoveTopLevelToInnerTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2SafeDeleteTestGenerated] | 100 | 198 | 198 | 198 | 
 | :white_check_mark: | [FirAnnotatedMembersSearchTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinFirFileStructureTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [KotlinFirSurroundWithTestGenerated] | 100 | 77 | 77 | 77 | 
 | :white_check_mark: | [KotlinFirUnwrapRemoveTestGenerated] | 100 | 63 | 63 | 63 | 
 | :white_check_mark: | [ParcelizeK2QuickFixTestGenerated] | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | [K2UnusedSymbolHighlightingTestGenerated] | 108 | 150 | 139 | 151 | 
 | :white_check_mark: | K2UnusedSymbolHighlightingTestGenerated$Multifile | 105 | 23 | 22 | 23 | 
 | :white_check_mark: | K2UnusedSymbolHighlightingTestGenerated$Uncategorized | 109 | 127 | 117 | 128 | 
 | :white_check_mark: | FirGotoTypeDeclarationTestGenerated | 111 | 20 | 18 | 20 | 
 | :white_check_mark: | FirGotoDeclarationTestGenerated | 113 | 17 | 15 | 17 | 
 | :white_check_mark: | K2InplaceRenameTestGenerated | 121 | 122 | 101 | 127 | 
 | :white_check_mark: | [K2HighlightingMetaInfoTestGenerated] | 105 | 60 | 57 | 60 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Diagnostics | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Dsl | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$SmartCasts | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Unresolved | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$Uncategorized | 106 | 34 | 32 | 34 | 
 | :white_check_mark: | K2HighlightingMetaInfoTestGenerated$FocusMode | 150 | 3 | 2 | 3 | 

### Extensions

kt, test, before.Main.kt, kts, main.java, main.kt, option1.kt, kt.kt, java, 0.kt, 0.java, 0.properties, txt, 0.kts, gradle.kts

---
## Total 
 * K1: 14223 rate: 99 % of 14368 files
 * K2: 9948 rate: 69 % of 14368 files
---

## Build cases for K1

org.jetbrains.kotlin.idea.codeInsight.gradle.GradleBuildFileHighlightingTestGenerated$Gradle8 has directories
 * idea/tests/testData/gradle/highlighting/gradle8/gradleSampleMultiProject
 * idea/tests/testData/gradle/highlighting/gradle8/javaLibraryPlugin
 * idea/tests/testData/gradle/highlighting/gradle8/wizardMultiAllTargets
 * idea/tests/testData/gradle/highlighting/gradle8/wizardNativeUiMultiplatformApp
 * idea/tests/testData/gradle/highlighting/gradle8/wizardSharedUiMultiplatformApp
 * idea/tests/testData/gradle/highlighting/gradle8/wizardSimpleKotlinProject

org.jetbrains.kotlin.idea.codeInsight.gradle.GradleBuildFileHighlightingTestGenerated$Gradle7 has directories
 * idea/tests/testData/gradle/highlighting/gradle7/gradleSampleMultiProject
 * idea/tests/testData/gradle/highlighting/gradle7/javaLibraryPlugin
 * idea/tests/testData/gradle/highlighting/gradle7/wizardSimpleKotlinProject

org.jetbrains.kotlin.idea.debugger.test.PositionManagerTestGenerated$MultiFile has directories
 * jvm-debugger/test/testData/positionManager/multiFilePackage
 * jvm-debugger/test/testData/positionManager/multiFileSameName

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AccessibilityChecker has directories
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/annotationOnClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/errorType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunction
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/nestedClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunReturnType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunTypeParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParam2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParamBound
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/annotationOnClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/errorType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunction
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/nestedClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunReturnType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunTypeParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParam2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParamBound

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$ActualAnnotationsNotMatchExpect has directories
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenExpectWithUseSiteTarget
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualConstExpression
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualNoArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSingleArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeUsage
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualValueParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualWithImport
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasNoSource
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActual
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualDifferentArgsOrder
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideEmptyWithNonEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideWithEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenExpectWithUseSiteTarget
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualConstExpression
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualNoArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSingleArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeUsage
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualValueParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualWithImport
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasNoSource
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActual
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualDifferentArgsOrder
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideEmptyWithNonEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideWithEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnExpect

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AddDependency has directories
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
 * idea/tests/testData/multiModuleQuickFix/addDependency/import
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction2
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty2
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
 * idea/tests/testData/multiModuleQuickFix/addDependency/import
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction2
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty2

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AddMissingActualMembers has directories
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionSameSignature
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructorAndParameters
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithIncompatibleConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classOverloadedFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classPropertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classSomeProperties
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classWithIncompilableFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/companionAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/membersWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/primaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/propertyWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/secondaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionSameSignature
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructorAndParameters
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithIncompatibleConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classOverloadedFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classPropertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classSomeProperties
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classWithIncompilableFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/companionAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/membersWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/primaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/propertyWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/secondaryConstructorAbsence

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AddThrowAnnotation has directories
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/common
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/js
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvm
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvmWithoutStdlib
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/common
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/js
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvm
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvmWithoutStdlib

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$CanSealedSubClassBeObject has directories
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertActualSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertImplicitExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInCommon
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInJvmForExpect
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertActualSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertImplicitExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInCommon
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInJvmForExpect

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$ChangeModifier has directories
 * idea/tests/testData/multiModuleQuickFix/changeModifier/internal
 * idea/tests/testData/multiModuleQuickFix/changeModifier/public
 * idea/tests/testData/multiModuleQuickFix/changeModifier/internal
 * idea/tests/testData/multiModuleQuickFix/changeModifier/public

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$ChangeSignature has directories
 * idea/tests/testData/multiModuleQuickFix/changeSignature/actual
 * idea/tests/testData/multiModuleQuickFix/changeSignature/expect
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override2
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override3
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override4
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override5
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override6
 * idea/tests/testData/multiModuleQuickFix/changeSignature/actual
 * idea/tests/testData/multiModuleQuickFix/changeSignature/expect
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override2
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override3
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override4
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override5
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override6

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$CreateActual has directories
 * idea/tests/testData/multiModuleQuickFix/createActual/abstract
 * idea/tests/testData/multiModuleQuickFix/createActual/abstractClassWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/annotation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectationNoDir
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationWithComment
 * idea/tests/testData/multiModuleQuickFix/createActual/class
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithBase
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithDelegation
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpected
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedClass
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/enum
 * idea/tests/testData/multiModuleQuickFix/createActual/expectSealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/forbiddenForLeafSourceSets
 * idea/tests/testData/multiModuleQuickFix/createActual/function
 * idea/tests/testData/multiModuleQuickFix/createActual/functionSameFile
 * idea/tests/testData/multiModuleQuickFix/createActual/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createActual/interface
 * idea/tests/testData/multiModuleQuickFix/createActual/nested
 * idea/tests/testData/multiModuleQuickFix/createActual/object
 * idea/tests/testData/multiModuleQuickFix/createActual/package
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrect
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrectEmpty
 * idea/tests/testData/multiModuleQuickFix/createActual/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/property
 * idea/tests/testData/multiModuleQuickFix/createActual/sealed
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedSubclass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClassWithGenerics
 * idea/tests/testData/multiModuleQuickFix/createActual/withFakeJvm
 * idea/tests/testData/multiModuleQuickFix/createActual/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/withTest
 * idea/tests/testData/multiModuleQuickFix/createActual/withTestDummy
 * idea/tests/testData/multiModuleQuickFix/createActual/abstract
 * idea/tests/testData/multiModuleQuickFix/createActual/abstractClassWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/annotation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectationNoDir
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationWithComment
 * idea/tests/testData/multiModuleQuickFix/createActual/class
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithBase
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithDelegation
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpected
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedClass
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/enum
 * idea/tests/testData/multiModuleQuickFix/createActual/expectSealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/forbiddenForLeafSourceSets
 * idea/tests/testData/multiModuleQuickFix/createActual/function
 * idea/tests/testData/multiModuleQuickFix/createActual/functionSameFile
 * idea/tests/testData/multiModuleQuickFix/createActual/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createActual/interface
 * idea/tests/testData/multiModuleQuickFix/createActual/nested
 * idea/tests/testData/multiModuleQuickFix/createActual/object
 * idea/tests/testData/multiModuleQuickFix/createActual/package
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrect
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrectEmpty
 * idea/tests/testData/multiModuleQuickFix/createActual/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/property
 * idea/tests/testData/multiModuleQuickFix/createActual/sealed
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedSubclass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClassWithGenerics
 * idea/tests/testData/multiModuleQuickFix/createActual/withFakeJvm
 * idea/tests/testData/multiModuleQuickFix/createActual/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/withTest
 * idea/tests/testData/multiModuleQuickFix/createActual/withTestDummy

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$CreateActualExplicitApi has directories
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/class
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/function
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/class
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/function

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$CreateExpect has directories
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation2
 * idea/tests/testData/multiModuleQuickFix/createExpect/class
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithAnnotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperClassAndTypeParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/commented
 * idea/tests/testData/multiModuleQuickFix/createExpect/companion
 * idea/tests/testData/multiModuleQuickFix/createExpect/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataObject
 * idea/tests/testData/multiModuleQuickFix/createExpect/enum
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumComplex
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumEmpty
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleExpansion
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleTypeFromCommon
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithJdk
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/function
 * idea/tests/testData/multiModuleQuickFix/createExpect/function2
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface2
 * idea/tests/testData/multiModuleQuickFix/createExpect/hierarchy
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerEnum
 * idea/tests/testData/multiModuleQuickFix/createExpect/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/nestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/noAccessOnMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/onMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/property
 * idea/tests/testData/multiModuleQuickFix/createExpect/property2
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithConstModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithLateinitModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/sealedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/stdlibWithJavaAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/superTypeFromStdlib
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelFunctionWithAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelPropertyWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/typeAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAliases
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/withConstructorWithParametersWithoutValVar
 * idea/tests/testData/multiModuleQuickFix/createExpect/withInitializer
 * idea/tests/testData/multiModuleQuickFix/createExpect/withPlatformNested
 * idea/tests/testData/multiModuleQuickFix/createExpect/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor2
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSupertype
 * idea/tests/testData/multiModuleQuickFix/createExpect/withVararg
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation2
 * idea/tests/testData/multiModuleQuickFix/createExpect/class
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithAnnotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperClassAndTypeParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/commented
 * idea/tests/testData/multiModuleQuickFix/createExpect/companion
 * idea/tests/testData/multiModuleQuickFix/createExpect/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataObject
 * idea/tests/testData/multiModuleQuickFix/createExpect/enum
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumComplex
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumEmpty
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleExpansion
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleTypeFromCommon
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithJdk
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/function
 * idea/tests/testData/multiModuleQuickFix/createExpect/function2
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface2
 * idea/tests/testData/multiModuleQuickFix/createExpect/hierarchy
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerEnum
 * idea/tests/testData/multiModuleQuickFix/createExpect/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/nestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/noAccessOnMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/onMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/property
 * idea/tests/testData/multiModuleQuickFix/createExpect/property2
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithConstModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithLateinitModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/sealedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/stdlibWithJavaAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/superTypeFromStdlib
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelFunctionWithAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelPropertyWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/typeAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAliases
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/withConstructorWithParametersWithoutValVar
 * idea/tests/testData/multiModuleQuickFix/createExpect/withInitializer
 * idea/tests/testData/multiModuleQuickFix/createExpect/withPlatformNested
 * idea/tests/testData/multiModuleQuickFix/createExpect/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor2
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSupertype
 * idea/tests/testData/multiModuleQuickFix/createExpect/withVararg

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$FixNativeThrowsErrors has directories
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException1
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException2
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException3
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException4
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeEmptyThrows
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeThrowsOnIncompatibleOverride
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException1
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException2
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException3
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException4
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeEmptyThrows
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeThrowsOnIncompatibleOverride

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$FunctionTypeReceiverToParameter has directories
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionConstructor
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/property
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionConstructor
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/property

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$InitializeProperty has directories
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$InlineToValue has directories
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$MakeOverridenMemberOpen has directories
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/actual
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/expect
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasAbstract
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasOpen
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/actual
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/expect
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasAbstract
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasOpen

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$Other has directories
 * idea/tests/testData/multiModuleQuickFix/other/actualImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualNoImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualWithoutExpect
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClass
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClassMember
 * idea/tests/testData/multiModuleQuickFix/other/addActualToTopLevelMember
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToActual
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToExpect
 * idea/tests/testData/multiModuleQuickFix/other/addFunctionToCommonClassFromJavaUsage
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByActual
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByExpect
 * idea/tests/testData/multiModuleQuickFix/other/cancelMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/convertActualEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertActualSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyGetterToInitializer
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyToFunction
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageImport
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageRef
 * idea/tests/testData/multiModuleQuickFix/other/createFunInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createTestOnExpect
 * idea/tests/testData/multiModuleQuickFix/other/createValInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createVarInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeader
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeaderImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImplHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/generateEqualsInExpect
 * idea/tests/testData/multiModuleQuickFix/other/generateHashCodeInExpect
 * idea/tests/testData/multiModuleQuickFix/other/implementAbstractExpectMemberInheritedFromInterface
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInActualClassNoExpectMember
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInImplClassNonImplInheritor
 * idea/tests/testData/multiModuleQuickFix/other/importClassInCommon
 * idea/tests/testData/multiModuleQuickFix/other/importClassInFromProductionInCommonTests
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJs
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importCommonFunInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithoutActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importFunInCommon
 * idea/tests/testData/multiModuleQuickFix/other/makeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeInlineFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeInternalFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/mayBeConstantWithActual
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeaderWithInapplicableImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/moveActualPropertyToExistentConstructor
 * idea/tests/testData/multiModuleQuickFix/other/movePropertyToConstructor
 * idea/tests/testData/multiModuleQuickFix/other/notMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/orderHeader
 * idea/tests/testData/multiModuleQuickFix/other/orderImpl
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteForbiddenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteUsedInAnotherPlatform
 * idea/tests/testData/multiModuleQuickFix/other/actualImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualNoImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualWithoutExpect
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClass
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClassMember
 * idea/tests/testData/multiModuleQuickFix/other/addActualToTopLevelMember
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToActual
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToExpect
 * idea/tests/testData/multiModuleQuickFix/other/addFunctionToCommonClassFromJavaUsage
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByActual
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByExpect
 * idea/tests/testData/multiModuleQuickFix/other/cancelMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/convertActualEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertActualSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyGetterToInitializer
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyToFunction
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageImport
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageRef
 * idea/tests/testData/multiModuleQuickFix/other/createFunInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createTestOnExpect
 * idea/tests/testData/multiModuleQuickFix/other/createValInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createVarInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeader
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeaderImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImplHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/generateEqualsInExpect
 * idea/tests/testData/multiModuleQuickFix/other/generateHashCodeInExpect
 * idea/tests/testData/multiModuleQuickFix/other/implementAbstractExpectMemberInheritedFromInterface
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInActualClassNoExpectMember
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInImplClassNonImplInheritor
 * idea/tests/testData/multiModuleQuickFix/other/importClassInCommon
 * idea/tests/testData/multiModuleQuickFix/other/importClassInFromProductionInCommonTests
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJs
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importCommonFunInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithoutActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importFunInCommon
 * idea/tests/testData/multiModuleQuickFix/other/makeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeInlineFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeInternalFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/mayBeConstantWithActual
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeaderWithInapplicableImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/moveActualPropertyToExistentConstructor
 * idea/tests/testData/multiModuleQuickFix/other/movePropertyToConstructor
 * idea/tests/testData/multiModuleQuickFix/other/notMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/orderHeader
 * idea/tests/testData/multiModuleQuickFix/other/orderImpl
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteForbiddenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteUsedInAnotherPlatform

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$PackageDirectoryMismatch has directories
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToAnotherPackage
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToCommonSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToJvmSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToAnotherPackage
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToCommonSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToJvmSourceRoot

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$RedundantNullableReturnType has directories
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualMethod
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectMemberProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualMethod
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectMemberProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelProperty

org.jetbrains.kotlin.idea.navigation.KotlinGotoImplementationMultiModuleTestGenerated has directories
 * idea/tests/testData/navigation/implementations/multiModule/actualTypeAliasWithAnonymousSubclass
 * idea/tests/testData/navigation/implementations/multiModule/expectClass
 * idea/tests/testData/navigation/implementations/multiModule/expectClassFun
 * idea/tests/testData/navigation/implementations/multiModule/expectClassProperty
 * idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclass
 * idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclassFun
 * idea/tests/testData/navigation/implementations/multiModule/expectClassSuperclassProperty
 * idea/tests/testData/navigation/implementations/multiModule/expectCompanion
 * idea/tests/testData/navigation/implementations/multiModule/expectEnumEntry
 * idea/tests/testData/navigation/implementations/multiModule/expectObject
 * idea/tests/testData/navigation/implementations/multiModule/suspendFunImpl

org.jetbrains.kotlin.idea.navigation.KotlinGotoRelatedSymbolMultiModuleTestGenerated has directories
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromActualMemberFunToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromActualMemberValToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromExpectMemberFunToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromExpectMemberValToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromNestedActualClassToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromNestedExpectClassToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualClassToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualFunToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualValToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectClassToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectFunToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectValToActuals

org.jetbrains.kotlin.idea.navigation.KotlinGotoSuperMultiModuleTestGenerated has directories
 * idea/tests/testData/navigation/gotoSuper/multiModule/actualClass
 * idea/tests/testData/navigation/gotoSuper/multiModule/actualFunction
 * idea/tests/testData/navigation/gotoSuper/multiModule/actualProperty

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Type has directories
 * idea/tests/testData/hierarchy/class/type/CaretAtAnnotation
 * idea/tests/testData/hierarchy/class/type/CaretAtConstructor
 * idea/tests/testData/hierarchy/class/type/CaretAtFabricMethod
 * idea/tests/testData/hierarchy/class/type/CaretAtImport
 * idea/tests/testData/hierarchy/class/type/CaretAtJavaType
 * idea/tests/testData/hierarchy/class/type/CaretAtModifierList
 * idea/tests/testData/hierarchy/class/type/CaretAtReceiverExtFun
 * idea/tests/testData/hierarchy/class/type/CaretAtSuperCall
 * idea/tests/testData/hierarchy/class/type/CaretAtSuperTypeCallEntry
 * idea/tests/testData/hierarchy/class/type/CaretAtSupertypesList
 * idea/tests/testData/hierarchy/class/type/CaretAtTypeReference
 * idea/tests/testData/hierarchy/class/type/CaretInClassBody
 * idea/tests/testData/hierarchy/class/type/ClassFromClass
 * idea/tests/testData/hierarchy/class/type/ClassFromObject
 * idea/tests/testData/hierarchy/class/type/ClassInClassObject
 * idea/tests/testData/hierarchy/class/type/ClassInPackage
 * idea/tests/testData/hierarchy/class/type/InnerClass
 * idea/tests/testData/hierarchy/class/type/JKJHierarchy
 * idea/tests/testData/hierarchy/class/type/JavaFromKotlin
 * idea/tests/testData/hierarchy/class/type/JavaFromKotlinByRef
 * idea/tests/testData/hierarchy/class/type/JavaFromKotlinForKotlinClass
 * idea/tests/testData/hierarchy/class/type/JavaStyleClassLiteralInvalidCode
 * idea/tests/testData/hierarchy/class/type/JetInt
 * idea/tests/testData/hierarchy/class/type/KJKHierarchy
 * idea/tests/testData/hierarchy/class/type/KotlinFromJava
 * idea/tests/testData/hierarchy/class/type/KotlinFromJavaByRef
 * idea/tests/testData/hierarchy/class/type/KotlinFromKotlinByRef
 * idea/tests/testData/hierarchy/class/type/Object
 * idea/tests/testData/hierarchy/class/type/TwoChildren

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Super has directories
 * idea/tests/testData/hierarchy/class/super/JetList
 * idea/tests/testData/hierarchy/class/super/MultiTypeAlias
 * idea/tests/testData/hierarchy/class/super/TwoInterfaces
 * idea/tests/testData/hierarchy/class/super/TypeAlias

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Sub has directories
 * idea/tests/testData/hierarchy/class/sub/AllFromClass
 * idea/tests/testData/hierarchy/class/sub/AllFromInterface
 * idea/tests/testData/hierarchy/class/sub/ClassFromClass
 * idea/tests/testData/hierarchy/class/sub/ClassFromInterface
 * idea/tests/testData/hierarchy/class/sub/ConstructorCallCaretAfter
 * idea/tests/testData/hierarchy/class/sub/ConstructorCallCaretBefore
 * idea/tests/testData/hierarchy/class/sub/InterfaceFromClass
 * idea/tests/testData/hierarchy/class/sub/InterfaceFromInterface
 * idea/tests/testData/hierarchy/class/sub/MultiTypeAlias
 * idea/tests/testData/hierarchy/class/sub/ObjectFromClass
 * idea/tests/testData/hierarchy/class/sub/ObjectFromInterface
 * idea/tests/testData/hierarchy/class/sub/SecondaryConstructorCallCaretAfter
 * idea/tests/testData/hierarchy/class/sub/SecondaryConstructorCallCaretBefore
 * idea/tests/testData/hierarchy/class/sub/TypeAlias

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Callers has directories
 * idea/tests/testData/hierarchy/calls/callers/callInsideAnonymousFun
 * idea/tests/testData/hierarchy/calls/callers/callInsideLambda
 * idea/tests/testData/hierarchy/calls/callers/insideJavadoc
 * idea/tests/testData/hierarchy/calls/callers/insideKDoc
 * idea/tests/testData/hierarchy/calls/callers/kotlinClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinEnumClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinEnumEntry
 * idea/tests/testData/hierarchy/calls/callers/kotlinFunction
 * idea/tests/testData/hierarchy/calls/callers/kotlinFunctionNonCallUsages
 * idea/tests/testData/hierarchy/calls/callers/kotlinInterface
 * idea/tests/testData/hierarchy/calls/callers/kotlinLocalClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinLocalFunction
 * idea/tests/testData/hierarchy/calls/callers/kotlinLocalFunctionWithNonLocalCallers
 * idea/tests/testData/hierarchy/calls/callers/kotlinNestedClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinNestedInnerClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinObjectDeclaration
 * idea/tests/testData/hierarchy/calls/callers/kotlinPackageFunction
 * idea/tests/testData/hierarchy/calls/callers/kotlinPackageProperty
 * idea/tests/testData/hierarchy/calls/callers/kotlinPrimaryConstructorImplicitCalls
 * idea/tests/testData/hierarchy/calls/callers/kotlinPrivateClass
 * idea/tests/testData/hierarchy/calls/callers/kotlinPrivateFunction
 * idea/tests/testData/hierarchy/calls/callers/kotlinPrivateProperty
 * idea/tests/testData/hierarchy/calls/callers/kotlinProperty
 * idea/tests/testData/hierarchy/calls/callers/kotlinSecondaryConstructor
 * idea/tests/testData/hierarchy/calls/callers/kotlinSecondaryConstructorImplicitCalls
 * idea/tests/testData/hierarchy/calls/callers/kotlinUnresolvedFunction

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$CallersJava has directories
 * idea/tests/testData/hierarchy/calls/callersJava/javaMethod

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Callees has directories
 * idea/tests/testData/hierarchy/calls/callees/kotlinAnonymousObject
 * idea/tests/testData/hierarchy/calls/callees/kotlinClass
 * idea/tests/testData/hierarchy/calls/callees/kotlinClassObject
 * idea/tests/testData/hierarchy/calls/callees/kotlinEnumClass
 * idea/tests/testData/hierarchy/calls/callees/kotlinFunction
 * idea/tests/testData/hierarchy/calls/callees/kotlinInterface
 * idea/tests/testData/hierarchy/calls/callees/kotlinLocalClass
 * idea/tests/testData/hierarchy/calls/callees/kotlinLocalFunction
 * idea/tests/testData/hierarchy/calls/callees/kotlinNestedClass
 * idea/tests/testData/hierarchy/calls/callees/kotlinObject
 * idea/tests/testData/hierarchy/calls/callees/kotlinPackageFunction
 * idea/tests/testData/hierarchy/calls/callees/kotlinPackageProperty
 * idea/tests/testData/hierarchy/calls/callees/kotlinProperty

org.jetbrains.kotlin.idea.hierarchy.HierarchyTestGenerated$Overrides has directories
 * idea/tests/testData/hierarchy/overrides/kotlinBuiltInMemberFunction
 * idea/tests/testData/hierarchy/overrides/kotlinFunctionInClass
 * idea/tests/testData/hierarchy/overrides/kotlinFunctionInInterface
 * idea/tests/testData/hierarchy/overrides/kotlinPropertyInClass
 * idea/tests/testData/hierarchy/overrides/kotlinPropertyInInterface
 * idea/tests/testData/hierarchy/overrides/kotlinTopLevelFunction
 * idea/tests/testData/hierarchy/overrides/kotlinVarParameter

org.jetbrains.kotlin.idea.hierarchy.HierarchyWithLibTestGenerated has directories
 * idea/tests/testData/hierarchy/withLib/annotation
 * idea/tests/testData/hierarchy/withLib/enum

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithCompiledLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithCrossLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithCompilerPluginsWithLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithCompilerPluginsWithCompiledLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.resolve.ReferenceResolveWithCompilerPluginsWithCrossLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.caches.resolve.MultiModuleLineMarkerTestGenerated has directories
 * idea/tests/testData/multiModuleLineMarker/actualConstructorWithProperties
 * idea/tests/testData/multiModuleLineMarker/actualDerived
 * idea/tests/testData/multiModuleLineMarker/actualEnumEntries
 * idea/tests/testData/multiModuleLineMarker/actualEnumEntriesInOneLine
 * idea/tests/testData/multiModuleLineMarker/actualWithOverload
 * idea/tests/testData/multiModuleLineMarker/expectConstructorWithProperties
 * idea/tests/testData/multiModuleLineMarker/expectEnumEntries
 * idea/tests/testData/multiModuleLineMarker/expectEnumEntriesInOneLine
 * idea/tests/testData/multiModuleLineMarker/expectEnumWithEnumEntriesInOneLine
 * idea/tests/testData/multiModuleLineMarker/expectWithActualInSameModule
 * idea/tests/testData/multiModuleLineMarker/expectWithOverload
 * idea/tests/testData/multiModuleLineMarker/fromActualAnnotation
 * idea/tests/testData/multiModuleLineMarker/fromActualAnnotationWithParametersInOneLine
 * idea/tests/testData/multiModuleLineMarker/fromActualCompanion
 * idea/tests/testData/multiModuleLineMarker/fromActualPrimaryConstructor
 * idea/tests/testData/multiModuleLineMarker/fromActualSealedClass
 * idea/tests/testData/multiModuleLineMarker/fromActualSecondaryConstructor
 * idea/tests/testData/multiModuleLineMarker/fromActualTypeAlias
 * idea/tests/testData/multiModuleLineMarker/fromClassToAlias
 * idea/tests/testData/multiModuleLineMarker/fromClassToJavaAliasInTest
 * idea/tests/testData/multiModuleLineMarker/fromCommonToJvmHeader
 * idea/tests/testData/multiModuleLineMarker/fromCommonToJvmImpl
 * idea/tests/testData/multiModuleLineMarker/fromExpectCompanion
 * idea/tests/testData/multiModuleLineMarker/fromExpectedAnnotation
 * idea/tests/testData/multiModuleLineMarker/fromExpectedPrimaryConstructor
 * idea/tests/testData/multiModuleLineMarker/fromExpectedSealedClass
 * idea/tests/testData/multiModuleLineMarker/fromExpectedSecondaryConstructor
 * idea/tests/testData/multiModuleLineMarker/fromExpectedTypeAlias
 * idea/tests/testData/multiModuleLineMarker/hierarchyWithExpectClassCommonSide
 * idea/tests/testData/multiModuleLineMarker/hierarchyWithExpectClassCommonSideNonJavaIds
 * idea/tests/testData/multiModuleLineMarker/hierarchyWithExpectClassPlatformSide
 * idea/tests/testData/multiModuleLineMarker/kotlinTestAnnotations
 * idea/tests/testData/multiModuleLineMarker/suspendImplInPlatformModules
 * idea/tests/testData/multiModuleLineMarker/topLevelFunWithKotlinTest
 * idea/tests/testData/multiModuleLineMarker/transitive
 * idea/tests/testData/multiModuleLineMarker/transitiveCommon
 * idea/tests/testData/multiModuleLineMarker/withOverloads

org.jetbrains.kotlin.idea.caches.resolve.MultiPlatformHighlightingTestGenerated has directories
 * idea/tests/testData/multiModuleHighlighting/multiplatform/actualizedSupertype
 * idea/tests/testData/multiModuleHighlighting/multiplatform/additionalMembersInPlatformInterface
 * idea/tests/testData/multiModuleHighlighting/multiplatform/basic
 * idea/tests/testData/multiModuleHighlighting/multiplatform/catchHeaderExceptionInPlatformModule
 * idea/tests/testData/multiModuleHighlighting/multiplatform/completionHandlexCoroutines
 * idea/tests/testData/multiModuleHighlighting/multiplatform/contracts
 * idea/tests/testData/multiModuleHighlighting/multiplatform/depends
 * idea/tests/testData/multiModuleHighlighting/multiplatform/differentJvmImpls
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerClass
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerClassImplTypealias
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerFunUsesStdlibInSignature
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerFunctionProperty
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerPartiallyImplemented
 * idea/tests/testData/multiModuleHighlighting/multiplatform/headerWithoutImplForBoth
 * idea/tests/testData/multiModuleHighlighting/multiplatform/internal
 * idea/tests/testData/multiModuleHighlighting/multiplatform/internalDependencyFromTests
 * idea/tests/testData/multiModuleHighlighting/multiplatform/internalInheritanceToCommon
 * idea/tests/testData/multiModuleHighlighting/multiplatform/javaUsesPlatformFacade
 * idea/tests/testData/multiModuleHighlighting/multiplatform/jvmKotlinReferencesCommonKotlinThroughJava
 * idea/tests/testData/multiModuleHighlighting/multiplatform/jvmKotlinReferencesCommonKotlinThroughJavaDifferentJvmImpls
 * idea/tests/testData/multiModuleHighlighting/multiplatform/jvmNameInCommon
 * idea/tests/testData/multiModuleHighlighting/multiplatform/multifileFacade
 * idea/tests/testData/multiModuleHighlighting/multiplatform/nestedClassWithoutImpl
 * idea/tests/testData/multiModuleHighlighting/multiplatform/platformTypeAliasInterchangebleWithAliasedClass
 * idea/tests/testData/multiModuleHighlighting/multiplatform/sealedTypeAlias
 * idea/tests/testData/multiModuleHighlighting/multiplatform/suppressHeaderWithoutImpl
 * idea/tests/testData/multiModuleHighlighting/multiplatform/suspend
 * idea/tests/testData/multiModuleHighlighting/multiplatform/transitive
 * idea/tests/testData/multiModuleHighlighting/multiplatform/triangle
 * idea/tests/testData/multiModuleHighlighting/multiplatform/triangleWithDependency
 * idea/tests/testData/multiModuleHighlighting/multiplatform/typeAliasedParameter
 * idea/tests/testData/multiModuleHighlighting/multiplatform/typeAliasedSam
 * idea/tests/testData/multiModuleHighlighting/multiplatform/useAppendable
 * idea/tests/testData/multiModuleHighlighting/multiplatform/useCorrectBuiltInsForCommonModule
 * idea/tests/testData/multiModuleHighlighting/multiplatform/usePlatformSpecificMember
 * idea/tests/testData/multiModuleHighlighting/multiplatform/withOverrides

org.jetbrains.kotlin.idea.WorkSelectionTestGenerated has directories
 * idea/tests/testData/wordSelection/ArrayBrackets
 * idea/tests/testData/wordSelection/Class
 * idea/tests/testData/wordSelection/ClassMember1
 * idea/tests/testData/wordSelection/ClassMember2
 * idea/tests/testData/wordSelection/ClassMember3
 * idea/tests/testData/wordSelection/ClassMember4
 * idea/tests/testData/wordSelection/ClassMember5
 * idea/tests/testData/wordSelection/CommentForStatements
 * idea/tests/testData/wordSelection/CommentForStatementsInLambda
 * idea/tests/testData/wordSelection/DeclarationWithComment1
 * idea/tests/testData/wordSelection/DeclarationWithComment2
 * idea/tests/testData/wordSelection/DeclarationWithComment3
 * idea/tests/testData/wordSelection/DeclarationWithComment4
 * idea/tests/testData/wordSelection/DeclarationWithDocComment
 * idea/tests/testData/wordSelection/DefiningMultipleSuperClass
 * idea/tests/testData/wordSelection/DefiningSuperClass
 * idea/tests/testData/wordSelection/DefiningVariable
 * idea/tests/testData/wordSelection/DocComment
 * idea/tests/testData/wordSelection/DocCommentOneLine
 * idea/tests/testData/wordSelection/DocCommentTagLink
 * idea/tests/testData/wordSelection/DocCommentTagName
 * idea/tests/testData/wordSelection/DocCommentTagText
 * idea/tests/testData/wordSelection/EscapedIdentifier
 * idea/tests/testData/wordSelection/ForRange
 * idea/tests/testData/wordSelection/FunctionWithLineCommentAfter
 * idea/tests/testData/wordSelection/FunctionWithLineCommentBefore
 * idea/tests/testData/wordSelection/IfBody
 * idea/tests/testData/wordSelection/IfCondition
 * idea/tests/testData/wordSelection/InvokedExpression
 * idea/tests/testData/wordSelection/KT13675
 * idea/tests/testData/wordSelection/LabeledReturn
 * idea/tests/testData/wordSelection/LambdaArgument1
 * idea/tests/testData/wordSelection/LambdaArgument2
 * idea/tests/testData/wordSelection/LambdaArgument3
 * idea/tests/testData/wordSelection/LambdaArgument4
 * idea/tests/testData/wordSelection/LeftBrace
 * idea/tests/testData/wordSelection/LineComment
 * idea/tests/testData/wordSelection/MultiDeclaration
 * idea/tests/testData/wordSelection/ObjectExpression
 * idea/tests/testData/wordSelection/RightBrace
 * idea/tests/testData/wordSelection/SimpleComment
 * idea/tests/testData/wordSelection/SimpleStringLiteral
 * idea/tests/testData/wordSelection/SimpleStringLiteral2
 * idea/tests/testData/wordSelection/Statements
 * idea/tests/testData/wordSelection/TemplateStringLiteral1
 * idea/tests/testData/wordSelection/TemplateStringLiteral2
 * idea/tests/testData/wordSelection/TemplateStringLiteral3
 * idea/tests/testData/wordSelection/TypeArguments
 * idea/tests/testData/wordSelection/TypeParameters
 * idea/tests/testData/wordSelection/ValueArguments
 * idea/tests/testData/wordSelection/ValueParameters
 * idea/tests/testData/wordSelection/ValueParameters2
 * idea/tests/testData/wordSelection/ValueParametersInLambda
 * idea/tests/testData/wordSelection/ValueParametersInLambda2
 * idea/tests/testData/wordSelection/ValueParametersInLambda3
 * idea/tests/testData/wordSelection/ValueParametersInLambda4
 * idea/tests/testData/wordSelection/WhenEntries

org.jetbrains.kotlin.idea.caches.resolve.MultiplatformAnalysisTestGenerated has directories
 * idea/tests/testData/multiplatform/aliasesTypeMismatch
 * idea/tests/testData/multiplatform/builtinsAndStdlib
 * idea/tests/testData/multiplatform/callableReferences
 * idea/tests/testData/multiplatform/chainedTypeAliasRefinement
 * idea/tests/testData/multiplatform/commonSealedWithPlatformInheritor
 * idea/tests/testData/multiplatform/constructorsOfExpect
 * idea/tests/testData/multiplatform/correctOverloadResolutionAmbiguity
 * idea/tests/testData/multiplatform/defaultArguments
 * idea/tests/testData/multiplatform/diamondActualInBottom
 * idea/tests/testData/multiplatform/diamondActualOnOnePath
 * idea/tests/testData/multiplatform/diamondDuplicateActuals
 * idea/tests/testData/multiplatform/diamondModuleDependency1
 * idea/tests/testData/multiplatform/diamondModuleDependency2
 * idea/tests/testData/multiplatform/diamondSeesTwoActuals
 * idea/tests/testData/multiplatform/differentKindsOfDependencies
 * idea/tests/testData/multiplatform/duplicateActualsExplicit
 * idea/tests/testData/multiplatform/duplicateActualsImplicit
 * idea/tests/testData/multiplatform/duplicateActualsOneWeaklyIncompatible
 * idea/tests/testData/multiplatform/duplicateActualsOneWithStrongIncompatibility
 * idea/tests/testData/multiplatform/duplicateExpectsExplicit
 * idea/tests/testData/multiplatform/duplicateExpectsImplicit
 * idea/tests/testData/multiplatform/duplicateExpectsWithStrongIncompatibility
 * idea/tests/testData/multiplatform/enumFromCommonSerlializableSupertype
 * idea/tests/testData/multiplatform/expectActualLineMarkers
 * idea/tests/testData/multiplatform/extensionOnExpect
 * idea/tests/testData/multiplatform/hierarcicalActualization
 * idea/tests/testData/multiplatform/incompleteActualization
 * idea/tests/testData/multiplatform/internalFromDependsOn
 * idea/tests/testData/multiplatform/internalFromDependsOnOfProduction
 * idea/tests/testData/multiplatform/internalFromProduction
 * idea/tests/testData/multiplatform/jsNameClash
 * idea/tests/testData/multiplatform/jvmDefaultNonMpp
 * idea/tests/testData/multiplatform/jvmInlineValueClass
 * idea/tests/testData/multiplatform/kt41218
 * idea/tests/testData/multiplatform/kt44898
 * idea/tests/testData/multiplatform/kt48291
 * idea/tests/testData/multiplatform/ktij22295
 * idea/tests/testData/multiplatform/ktij27523
 * idea/tests/testData/multiplatform/lambdas
 * idea/tests/testData/multiplatform/languageConstructions
 * idea/tests/testData/multiplatform/multilevelParents
 * idea/tests/testData/multiplatform/multiplatformLibrary
 * idea/tests/testData/multiplatform/nativeStdlib
 * idea/tests/testData/multiplatform/overrideExpect
 * idea/tests/testData/multiplatform/overrideExpectWithCompositeType
 * idea/tests/testData/multiplatform/platformDependencyInCommon
 * idea/tests/testData/multiplatform/platformSpecificChecksInCommon
 * idea/tests/testData/multiplatform/qualifiedReceiver
 * idea/tests/testData/multiplatform/recursiveTypes
 * idea/tests/testData/multiplatform/sealedInheritorsInComplexModuleStructure1
 * idea/tests/testData/multiplatform/sealedInheritorsInComplexModuleStructure2
 * idea/tests/testData/multiplatform/simple
 * idea/tests/testData/multiplatform/smartCastOnPropertyFromDependentModule
 * idea/tests/testData/multiplatform/supertypes
 * idea/tests/testData/multiplatform/transitiveDependencyOnCommonSourceSets
 * idea/tests/testData/multiplatform/typeAliasToExpectClassExplicitReference
 * idea/tests/testData/multiplatform/typeAliases
 * idea/tests/testData/multiplatform/typeParameters
 * idea/tests/testData/multiplatform/unresolvedInMultiplatformLibrary
 * idea/tests/testData/multiplatform/useCorrectBuiltIns
 * idea/tests/testData/multiplatform/weaklyIncompatibleActualInIntermediateModule
 * idea/tests/testData/multiplatform/whenExhaustivenessForSealed

org.jetbrains.kotlin.idea.internal.BytecodeToolWindowMultiplatformTestGenerated$Common has directories
 * idea/tests/testData/internal/toolWindowMultiplatform/abstractClass
 * idea/tests/testData/internal/toolWindowMultiplatform/classWithConstructor
 * idea/tests/testData/internal/toolWindowMultiplatform/clazz
 * idea/tests/testData/internal/toolWindowMultiplatform/funWithParam
 * idea/tests/testData/internal/toolWindowMultiplatform/function
 * idea/tests/testData/internal/toolWindowMultiplatform/functionFromOtherFile
 * idea/tests/testData/internal/toolWindowMultiplatform/functionWithOrphanedExpect
 * idea/tests/testData/internal/toolWindowMultiplatform/generic
 * idea/tests/testData/internal/toolWindowMultiplatform/interfaceDeclaration
 * idea/tests/testData/internal/toolWindowMultiplatform/obj
 * idea/tests/testData/internal/toolWindowMultiplatform/propertyVal
 * idea/tests/testData/internal/toolWindowMultiplatform/propertyVar

org.jetbrains.kotlin.idea.internal.BytecodeToolWindowMultiplatformTestGenerated$Jvm has directories
 * idea/tests/testData/internal/toolWindowMultiplatform/abstractClass
 * idea/tests/testData/internal/toolWindowMultiplatform/classWithConstructor
 * idea/tests/testData/internal/toolWindowMultiplatform/clazz
 * idea/tests/testData/internal/toolWindowMultiplatform/funWithParam
 * idea/tests/testData/internal/toolWindowMultiplatform/function
 * idea/tests/testData/internal/toolWindowMultiplatform/functionFromOtherFile
 * idea/tests/testData/internal/toolWindowMultiplatform/functionWithOrphanedExpect
 * idea/tests/testData/internal/toolWindowMultiplatform/generic
 * idea/tests/testData/internal/toolWindowMultiplatform/interfaceDeclaration
 * idea/tests/testData/internal/toolWindowMultiplatform/obj
 * idea/tests/testData/internal/toolWindowMultiplatform/propertyVal
 * idea/tests/testData/internal/toolWindowMultiplatform/propertyVar

org.jetbrains.kotlin.idea.slicer.SlicerMultiplatformTestGenerated has directories
 * idea/tests/testData/slicer/mpp/actualClassFunctionParameter
 * idea/tests/testData/slicer/mpp/actualFunctionParameter
 * idea/tests/testData/slicer/mpp/expectClassFunctionParameter
 * idea/tests/testData/slicer/mpp/expectExtensionFunctionResultOut
 * idea/tests/testData/slicer/mpp/expectFunctionParameter
 * idea/tests/testData/slicer/mpp/expectFunctionResultIn
 * idea/tests/testData/slicer/mpp/expectFunctionResultOut
 * idea/tests/testData/slicer/mpp/expectPropertyResultIn
 * idea/tests/testData/slicer/mpp/expectPropertyResultOut

org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated$ScratchMultiFile has directories
 * scripting-support/testData/scratch/multiFile/inlineFun
 * scripting-support/testData/scratch/multiFile/javaDep

org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated$WorksheetMultiFile has directories
 * scripting-support/testData/worksheet/multiFile/inlineFunScriptRuntime
 * scripting-support/testData/worksheet/multiFile/javaDepScriptRuntime

org.jetbrains.kotlin.idea.script.ScriptTemplatesFromDependenciesTestGenerated has directories
 * scripting-support/testData/script/templatesFromDependencies/inJar
 * scripting-support/testData/script/templatesFromDependencies/inTests
 * scripting-support/testData/script/templatesFromDependencies/multipleRoots
 * scripting-support/testData/script/templatesFromDependencies/multipleTemplates
 * scripting-support/testData/script/templatesFromDependencies/outsideRoots
 * scripting-support/testData/script/templatesFromDependencies/singleTemplate

org.jetbrains.kotlin.idea.script.ScriptConfigurationHighlightingTestGenerated$Highlighting has directories
 * idea/tests/testData/script/definition/highlighting/acceptedAnnotations
 * idea/tests/testData/script/definition/highlighting/additionalImports
 * idea/tests/testData/script/definition/highlighting/asyncResolver
 * idea/tests/testData/script/definition/highlighting/conflictingModule
 * idea/tests/testData/script/definition/highlighting/customBaseClass
 * idea/tests/testData/script/definition/highlighting/customExtension
 * idea/tests/testData/script/definition/highlighting/customJavaHome
 * idea/tests/testData/script/definition/highlighting/customLibrary
 * idea/tests/testData/script/definition/highlighting/doNotSpeakAboutJava
 * idea/tests/testData/script/definition/highlighting/doNotSpeakAboutJavaLegacy
 * idea/tests/testData/script/definition/highlighting/emptyAsyncResolver
 * idea/tests/testData/script/definition/highlighting/implicitReceiver
 * idea/tests/testData/script/definition/highlighting/multiModule
 * idea/tests/testData/script/definition/highlighting/multipleScripts
 * idea/tests/testData/script/definition/highlighting/nestedClass
 * idea/tests/testData/script/definition/highlighting/noResolver
 * idea/tests/testData/script/definition/highlighting/propertyAccessor
 * idea/tests/testData/script/definition/highlighting/propertyAccessorFromModule
 * idea/tests/testData/script/definition/highlighting/simple

org.jetbrains.kotlin.idea.script.ScriptConfigurationHighlightingTestGenerated$Complex has directories
 * idea/tests/testData/script/definition/complex/errorResolver

org.jetbrains.kotlin.idea.script.ScriptConfigurationNavigationTestGenerated has directories
 * idea/tests/testData/script/definition/navigation/buildSrcProblem
 * idea/tests/testData/script/definition/navigation/conflictingModule
 * idea/tests/testData/script/definition/navigation/customBaseClass
 * idea/tests/testData/script/definition/navigation/includedPluginProblem
 * idea/tests/testData/script/definition/navigation/javaLib
 * idea/tests/testData/script/definition/navigation/javaLibWithSources
 * idea/tests/testData/script/definition/navigation/kotlinLib
 * idea/tests/testData/script/definition/navigation/kotlinLibWithSources
 * idea/tests/testData/script/definition/navigation/stdlib
 * idea/tests/testData/script/definition/navigation/stdlibWithSources

org.jetbrains.kotlin.idea.script.ScriptConfigurationCompletionTestGenerated has directories
 * idea/tests/testData/script/definition/completion/conflictingModule
 * idea/tests/testData/script/definition/completion/conflictingModuleCustomDef
 * idea/tests/testData/script/definition/completion/conflictingModuleJavaLib
 * idea/tests/testData/script/definition/completion/implicitReceiver

org.jetbrains.kotlin.idea.script.ScriptConfigurationInsertImportOnPasteTestGenerated$Copy has directories
 * idea/tests/testData/script/definition/imports/implicitImport

org.jetbrains.kotlin.idea.script.ScriptConfigurationInsertImportOnPasteTestGenerated$Cut has directories
 * idea/tests/testData/script/definition/imports/implicitImport

org.jetbrains.kotlin.idea.script.ScriptDefinitionsOrderTestGenerated has directories
 * idea/tests/testData/script/definition/order/reorder
 * idea/tests/testData/script/definition/order/switchOff

org.jetbrains.kotlin.idea.maven.configuration.MavenConfigureProjectByChangingFileTestGenerated has directories
 * maven/tests/testData/configurator/jvm/fixExisting
 * maven/tests/testData/configurator/jvm/jreLib
 * maven/tests/testData/configurator/jvm/jvmVersion11InheritanceFromJava
 * maven/tests/testData/configurator/jvm/jvmVersion1_8InheritanceFromJava
 * maven/tests/testData/configurator/jvm/jvmVersion8InheritanceFromJava
 * maven/tests/testData/configurator/jvm/libraryMissing
 * maven/tests/testData/configurator/jvm/pluginMissing
 * maven/tests/testData/configurator/jvm/simpleProject
 * maven/tests/testData/configurator/jvm/simpleProjectBeta
 * maven/tests/testData/configurator/jvm/simpleProjectDev
 * maven/tests/testData/configurator/jvm/simpleProjectMilestone
 * maven/tests/testData/configurator/jvm/simpleProjectReleaseCandidate
 * maven/tests/testData/configurator/jvm/simpleProjectSnapshot
 * maven/tests/testData/configurator/jvm/withExistingDefaultCompile
 * maven/tests/testData/configurator/jvm/withExistingTestCompile
 * maven/tests/testData/configurator/jvm/withJava9ModuleInfo

org.jetbrains.kotlin.idea.configuration.gradle.GradleConfigureProjectByChangingFileTestGenerated$Gradle has directories
 * idea/tests/testData/configuration/gradle/jreLib
 * idea/tests/testData/configuration/gradle/libraryMissing
 * idea/tests/testData/configuration/gradle/pluginPresent
 * idea/tests/testData/configuration/gradle/simpleProject
 * idea/tests/testData/configuration/gradle/simpleProjectBeta
 * idea/tests/testData/configuration/gradle/simpleProjectDev
 * idea/tests/testData/configuration/gradle/simpleProjectMilestone
 * idea/tests/testData/configuration/gradle/simpleProjectReleaseCandidate
 * idea/tests/testData/configuration/gradle/simpleProjectSnapshot
 * idea/tests/testData/configuration/gradle/withJava9ModuleInfo

org.jetbrains.kotlin.idea.configuration.gradle.GradleConfigureProjectByChangingFileTestGenerated$Gsk has directories
 * idea/tests/testData/configuration/gsk/libraryMissing
 * idea/tests/testData/configuration/gsk/pluginPresent
 * idea/tests/testData/configuration/gsk/simpleProject
 * idea/tests/testData/configuration/gsk/simpleProjectBeta
 * idea/tests/testData/configuration/gsk/simpleProjectDev
 * idea/tests/testData/configuration/gsk/simpleProjectMilestone
 * idea/tests/testData/configuration/gsk/simpleProjectReleaseCandidate
 * idea/tests/testData/configuration/gsk/simpleProjectSnapshot

org.jetbrains.kotlin.idea.completion.test.MultiPlatformCompletionTestGenerated has directories
 * completion/testData/multiPlatform/classInCommon
 * completion/testData/multiPlatform/classInCommonNonImported
 * completion/testData/multiPlatform/classInPlatform
 * completion/testData/multiPlatform/functionInCommon
 * completion/testData/multiPlatform/functionInPlatform

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/companion

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestInnerClassWithPackage has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClassWithPackage

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestJavaInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInnerClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestJavaNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestJavaNestedInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestNamedCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/namedCompanion

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestNestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedObject

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject$TestObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/companion

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestInnerClassWithPackage has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClassWithPackage

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInnerClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInvoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaNestedInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestNamedCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/namedCompanion

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestNestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedObject

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions$Constructors has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions$FromObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions$Hierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions$Members has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions$TopLevel has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestAllWithDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/allWithDefault

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestJavaConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/javaConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/jvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestNestedPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/nestedPrimaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/primaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Constructors$TestSecondaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestAllWithDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/allWithDefault

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/javaConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/jvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestNestedPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/nestedPrimaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/primaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestSecondaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$FromCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestFunctionWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/functionWithSeveralParameters

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$Named has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestExtensionForObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extensionForObject

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestJavaStaticMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestJavaStaticMethod2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod2

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/component

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/get

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/hasNext

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/invoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/iterator

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/minus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/next

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/plus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/component

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/get

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/hasNext

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/invoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/iterator

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/minus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/next

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/plus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtensionForObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extensionForObject

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaStaticMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaStaticMethod2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod2

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticExtension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticFunction

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFunctionWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/functionWithSeveralParameters

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Hierarchy$TestFromLibrary has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Java$TestJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/J

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Java$TestJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Java$TestJJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Java$TestJKJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JKJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/J

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJKJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JKJ

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Kotlin$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFromLibrary has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$ExtensionOperators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$InterfaceDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotation

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaAnnotationWithCustomName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithCustomName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaAnnotationWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithSeveralParameters

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethod

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaMethodSyntheticGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticGet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaMethodSyntheticIs has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIs

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaMethodSyntheticIsSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIsSet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaMethodSyntheticSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/component

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/get

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/hasNext

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/invoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/iterator

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/minus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/next

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/plus

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ExtensionOperators$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/get

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Operators$TestComponentFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/componentFromDataClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestComponentFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/componentFromDataClass

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotation

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaAnnotationWithCustomName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithCustomName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaAnnotationWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithSeveralParameters

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethod

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaMethodSyntheticGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticGet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaMethodSyntheticIs has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIs

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaMethodSyntheticIsSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIsSet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaMethodSyntheticSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extension

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestExtensionWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestExtensionWithJvmOverloadsAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithJvmOverloadsAndJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/function

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestFunctionWithJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestFunctionWithJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtensionWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtensionWithJvmOverloadsAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithJvmOverloadsAndJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFunctionWithJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFunctionWithJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties$ConstructorParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties$ConstructorParameterFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties$FromObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties$Members has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties$TopLevel has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotation

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestAnnotationWithCustomParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotationWithCustomParameter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestHierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/hierarchy

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/val

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/var

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithCustomNames

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameter$TestWithoutVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotation

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestAnnotationWithCustomParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotationWithCustomParameter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestHierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/hierarchy

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/val
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/val

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/var
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/var

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithCustomNames
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithCustomNames

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestWithoutVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestComponentB has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/componentB

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestEscapedName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/escapedName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/val

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/var

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithCustomNames

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ConstructorParameterFromDataClass$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestComponentB has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/componentB

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestEscapedName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/escapedName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticLateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromObject$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestJavaStaticField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestJavaStaticField2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField2

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithBackingField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticLateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$FromCompanion$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticLateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Named$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticLateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaStaticField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaStaticField2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField2

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithBackingField
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithBackingField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithBackingField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithCustomGetterAndSetter
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithCustomGetterAndSetter
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticLateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$NestedObject$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Get$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKJK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Set$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKKK

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestJavaFieldWithInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaFieldWithInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithBackingField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestPropertyWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithReceiver

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Members$TestVariableWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$InterfaceDefault$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestJavaFieldWithInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaFieldWithInvoke

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestPropertyWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithReceiver

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constant

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestConstantJava has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJava

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestConstantJavaWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJavaWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestConstantWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestExtensionVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestIsVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isVariableWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/lateinit

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/property

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithBackingField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variable

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariableWithCustomGetterAndSetterAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariableWithCustomGetterAndSetterAndMixedJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndMixedJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmField

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TopLevel$TestVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestConstantJava has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJava

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestConstantJavaWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJavaWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestConstantWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestExtensionVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestIsVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isVariableWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomFileName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithCustomGetterAndSetterAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithCustomGetterAndSetterAndMixedJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndMixedJvmName

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$TestVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library$Any has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library$Long has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library$Object has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library$String_ has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Any$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Long$Class has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Class$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Object$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$String_$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/class

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$String_$TestLength has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$TestLength has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.codeMetaInfo.MultiModuleLineMarkerCodeMetaInfoTestGenerated has directories
 * code-insight/testData/linemarkers/multiplatform
## Build cases for K2

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$AccessibilityChecker has directories
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/annotationOnClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/errorType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunction
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/nestedClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunReturnType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunTypeParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParam2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParamBound
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/annotationOnClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/classUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/errorType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunction
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/memberFunctionParentType2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/nestedClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunReturnType
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunTypeParameter
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelFunUpperBounds2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParam2
 * idea/tests/testData/multiModuleQuickFix/accessibilityChecker/topLevelPropertyTypeParamBound

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$ActualAnnotationsNotMatchExpect has directories
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenExpectWithUseSiteTarget
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualConstExpression
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualNoArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSingleArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeUsage
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualValueParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualWithImport
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasNoSource
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActual
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualDifferentArgsOrder
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideEmptyWithNonEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideWithEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualHasDefaultGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyNotSuggestedWhenExpectWithUseSiteTarget
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualConstExpression
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualGetter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualNoArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualPrimaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualSingleArg
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualTypeUsage
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualValueParameter
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/copyToActualWithImport
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpect
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualFakeOverride
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasDefaultEmptyConstructor
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualHasNoSource
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/removeFromExpectSuggestedWhenActualTypealias
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActual
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualDifferentArgsOrder
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideEmptyWithNonEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnActualOverrideWithEmpty
 * idea/tests/testData/multiModuleQuickFix/actualAnnotationsNotMatchExpect/replaceArgsOnExpect

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$AddDependency has directories
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
 * idea/tests/testData/multiModuleQuickFix/addDependency/import
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction2
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty2
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
 * idea/tests/testData/multiModuleQuickFix/addDependency/import
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction2
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty2

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$AddMissingActualMembers has directories
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionSameSignature
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructorAndParameters
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithIncompatibleConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classOverloadedFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classPropertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classSomeProperties
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classWithIncompilableFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/companionAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/membersWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/primaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/propertyWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/secondaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionSameSignature
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithConstructorAndParameters
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classFunctionWithIncompatibleConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classOverloadedFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classPropertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classSomeProperties
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/classWithIncompilableFunction
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/companionAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/membersWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/primaryConstructorAbsence
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/propertyWithIncorrectType
 * idea/tests/testData/multiModuleQuickFix/addMissingActualMembers/secondaryConstructorAbsence

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$AddThrowAnnotation has directories
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/common
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/js
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvm
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvmWithoutStdlib
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/common
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/js
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvm
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvmWithoutStdlib

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$CanSealedSubClassBeObject has directories
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertActualSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertImplicitExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInCommon
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInJvmForExpect
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertActualSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertImplicitExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInCommon
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInJvmForExpect

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$ChangeModifier has directories
 * idea/tests/testData/multiModuleQuickFix/changeModifier/internal
 * idea/tests/testData/multiModuleQuickFix/changeModifier/public
 * idea/tests/testData/multiModuleQuickFix/changeModifier/internal
 * idea/tests/testData/multiModuleQuickFix/changeModifier/public

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$ChangeSignature has directories
 * idea/tests/testData/multiModuleQuickFix/changeSignature/actual
 * idea/tests/testData/multiModuleQuickFix/changeSignature/expect
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override2
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override3
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override4
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override5
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override6
 * idea/tests/testData/multiModuleQuickFix/changeSignature/actual
 * idea/tests/testData/multiModuleQuickFix/changeSignature/expect
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override2
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override3
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override4
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override5
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override6

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$CreateActual has directories
 * idea/tests/testData/multiModuleQuickFix/createActual/abstract
 * idea/tests/testData/multiModuleQuickFix/createActual/abstractClassWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/annotation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectationNoDir
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationWithComment
 * idea/tests/testData/multiModuleQuickFix/createActual/class
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithBase
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithDelegation
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpected
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedClass
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/enum
 * idea/tests/testData/multiModuleQuickFix/createActual/expectSealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/forbiddenForLeafSourceSets
 * idea/tests/testData/multiModuleQuickFix/createActual/function
 * idea/tests/testData/multiModuleQuickFix/createActual/functionSameFile
 * idea/tests/testData/multiModuleQuickFix/createActual/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createActual/interface
 * idea/tests/testData/multiModuleQuickFix/createActual/nested
 * idea/tests/testData/multiModuleQuickFix/createActual/object
 * idea/tests/testData/multiModuleQuickFix/createActual/package
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrect
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrectEmpty
 * idea/tests/testData/multiModuleQuickFix/createActual/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/property
 * idea/tests/testData/multiModuleQuickFix/createActual/sealed
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedSubclass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClassWithGenerics
 * idea/tests/testData/multiModuleQuickFix/createActual/withFakeJvm
 * idea/tests/testData/multiModuleQuickFix/createActual/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/withTest
 * idea/tests/testData/multiModuleQuickFix/createActual/withTestDummy
 * idea/tests/testData/multiModuleQuickFix/createActual/abstract
 * idea/tests/testData/multiModuleQuickFix/createActual/abstractClassWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/annotation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectation
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationOptionalExpectationNoDir
 * idea/tests/testData/multiModuleQuickFix/createActual/annotationWithComment
 * idea/tests/testData/multiModuleQuickFix/createActual/class
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithBase
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithDelegation
 * idea/tests/testData/multiModuleQuickFix/createActual/constructorWithJdk
 * idea/tests/testData/multiModuleQuickFix/createActual/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpected
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedClass
 * idea/tests/testData/multiModuleQuickFix/createActual/defaultParameterInExpectedConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/enum
 * idea/tests/testData/multiModuleQuickFix/createActual/expectSealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/forbiddenForLeafSourceSets
 * idea/tests/testData/multiModuleQuickFix/createActual/function
 * idea/tests/testData/multiModuleQuickFix/createActual/functionSameFile
 * idea/tests/testData/multiModuleQuickFix/createActual/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createActual/interface
 * idea/tests/testData/multiModuleQuickFix/createActual/nested
 * idea/tests/testData/multiModuleQuickFix/createActual/object
 * idea/tests/testData/multiModuleQuickFix/createActual/package
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrect
 * idea/tests/testData/multiModuleQuickFix/createActual/packageIncorrectEmpty
 * idea/tests/testData/multiModuleQuickFix/createActual/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createActual/property
 * idea/tests/testData/multiModuleQuickFix/createActual/sealed
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedInCommonWhen
 * idea/tests/testData/multiModuleQuickFix/createActual/sealedSubclass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClass
 * idea/tests/testData/multiModuleQuickFix/createActual/valueClassWithGenerics
 * idea/tests/testData/multiModuleQuickFix/createActual/withFakeJvm
 * idea/tests/testData/multiModuleQuickFix/createActual/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createActual/withTest
 * idea/tests/testData/multiModuleQuickFix/createActual/withTestDummy

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$CreateActualExplicitApi has directories
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/class
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/function
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/class
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/function

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$CreateExpect has directories
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation2
 * idea/tests/testData/multiModuleQuickFix/createExpect/class
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithAnnotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperClassAndTypeParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/commented
 * idea/tests/testData/multiModuleQuickFix/createExpect/companion
 * idea/tests/testData/multiModuleQuickFix/createExpect/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataObject
 * idea/tests/testData/multiModuleQuickFix/createExpect/enum
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumComplex
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumEmpty
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleExpansion
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleTypeFromCommon
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithJdk
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/function
 * idea/tests/testData/multiModuleQuickFix/createExpect/function2
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface2
 * idea/tests/testData/multiModuleQuickFix/createExpect/hierarchy
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerEnum
 * idea/tests/testData/multiModuleQuickFix/createExpect/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/nestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/noAccessOnMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/onMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/property
 * idea/tests/testData/multiModuleQuickFix/createExpect/property2
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithConstModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithLateinitModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/sealedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/stdlibWithJavaAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/superTypeFromStdlib
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelFunctionWithAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelPropertyWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/typeAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAliases
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/withConstructorWithParametersWithoutValVar
 * idea/tests/testData/multiModuleQuickFix/createExpect/withInitializer
 * idea/tests/testData/multiModuleQuickFix/createExpect/withPlatformNested
 * idea/tests/testData/multiModuleQuickFix/createExpect/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor2
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSupertype
 * idea/tests/testData/multiModuleQuickFix/createExpect/withVararg
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/annotation2
 * idea/tests/testData/multiModuleQuickFix/createExpect/class
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithAnnotation
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperClassAndTypeParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/classWithSuperTypeFromOtherPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/commented
 * idea/tests/testData/multiModuleQuickFix/createExpect/companion
 * idea/tests/testData/multiModuleQuickFix/createExpect/createWithImport
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/dataObject
 * idea/tests/testData/multiModuleQuickFix/createExpect/enum
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumComplex
 * idea/tests/testData/multiModuleQuickFix/createExpect/enumEmpty
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleExpansion
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleParameter
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithAccessibleTypeFromCommon
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithJdk
 * idea/tests/testData/multiModuleQuickFix/createExpect/funWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/function
 * idea/tests/testData/multiModuleQuickFix/createExpect/function2
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface
 * idea/tests/testData/multiModuleQuickFix/createExpect/functionWithImplementationInInterface2
 * idea/tests/testData/multiModuleQuickFix/createExpect/hierarchy
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/inlineClass2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerClassWithTypeParam2
 * idea/tests/testData/multiModuleQuickFix/createExpect/innerEnum
 * idea/tests/testData/multiModuleQuickFix/createExpect/memberFunctionAndNestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/nestedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/noAccessOnMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/onMember
 * idea/tests/testData/multiModuleQuickFix/createExpect/primaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/property
 * idea/tests/testData/multiModuleQuickFix/createExpect/property2
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyInConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithConstModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithLateinitModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/propertyWithPrivateModifier
 * idea/tests/testData/multiModuleQuickFix/createExpect/sealedClass
 * idea/tests/testData/multiModuleQuickFix/createExpect/stdlibWithJavaAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/superTypeFromStdlib
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelFunctionWithAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/topLevelPropertyWithTypeParam
 * idea/tests/testData/multiModuleQuickFix/createExpect/typeAlias
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAliases
 * idea/tests/testData/multiModuleQuickFix/createExpect/withAnnotations
 * idea/tests/testData/multiModuleQuickFix/createExpect/withConstructorWithParametersWithoutValVar
 * idea/tests/testData/multiModuleQuickFix/createExpect/withInitializer
 * idea/tests/testData/multiModuleQuickFix/createExpect/withPlatformNested
 * idea/tests/testData/multiModuleQuickFix/createExpect/withRootPackage
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSecondaryConstructor2
 * idea/tests/testData/multiModuleQuickFix/createExpect/withSupertype
 * idea/tests/testData/multiModuleQuickFix/createExpect/withVararg

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$FixNativeThrowsErrors has directories
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException1
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException2
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException3
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException4
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeEmptyThrows
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeThrowsOnIncompatibleOverride
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException1
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException2
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException3
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException4
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeEmptyThrows
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeThrowsOnIncompatibleOverride

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$FunctionTypeReceiverToParameter has directories
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionConstructor
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/property
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionConstructor
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/classProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/functionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionParameter
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceFunctionReturn
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/interfaceProperty
 * idea/tests/testData/multiModuleQuickFix/functionTypeReceiverToParameter/property

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$InitializeProperty has directories
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$InlineToValue has directories
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$MakeOverridenMemberOpen has directories
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/actual
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/expect
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasAbstract
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasOpen
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/actual
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/expect
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasAbstract
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasOpen

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$Other has directories
 * idea/tests/testData/multiModuleQuickFix/other/actualImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualNoImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualWithoutExpect
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClass
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClassMember
 * idea/tests/testData/multiModuleQuickFix/other/addActualToTopLevelMember
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToActual
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToExpect
 * idea/tests/testData/multiModuleQuickFix/other/addFunctionToCommonClassFromJavaUsage
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByActual
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByExpect
 * idea/tests/testData/multiModuleQuickFix/other/cancelMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/convertActualEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertActualSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyGetterToInitializer
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyToFunction
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageImport
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageRef
 * idea/tests/testData/multiModuleQuickFix/other/createFunInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createTestOnExpect
 * idea/tests/testData/multiModuleQuickFix/other/createValInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createVarInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeader
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeaderImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImplHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/generateEqualsInExpect
 * idea/tests/testData/multiModuleQuickFix/other/generateHashCodeInExpect
 * idea/tests/testData/multiModuleQuickFix/other/implementAbstractExpectMemberInheritedFromInterface
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInActualClassNoExpectMember
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInImplClassNonImplInheritor
 * idea/tests/testData/multiModuleQuickFix/other/importClassInCommon
 * idea/tests/testData/multiModuleQuickFix/other/importClassInFromProductionInCommonTests
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJs
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importCommonFunInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithoutActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importFunInCommon
 * idea/tests/testData/multiModuleQuickFix/other/makeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeInlineFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeInternalFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/mayBeConstantWithActual
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeaderWithInapplicableImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/moveActualPropertyToExistentConstructor
 * idea/tests/testData/multiModuleQuickFix/other/movePropertyToConstructor
 * idea/tests/testData/multiModuleQuickFix/other/notMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/orderHeader
 * idea/tests/testData/multiModuleQuickFix/other/orderImpl
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteForbiddenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteUsedInAnotherPlatform
 * idea/tests/testData/multiModuleQuickFix/other/actualImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualNoImplementAsConstructorParam
 * idea/tests/testData/multiModuleQuickFix/other/actualWithoutExpect
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClass
 * idea/tests/testData/multiModuleQuickFix/other/addActualToClassMember
 * idea/tests/testData/multiModuleQuickFix/other/addActualToTopLevelMember
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToActual
 * idea/tests/testData/multiModuleQuickFix/other/addAnnotationTargetToExpect
 * idea/tests/testData/multiModuleQuickFix/other/addFunctionToCommonClassFromJavaUsage
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByActual
 * idea/tests/testData/multiModuleQuickFix/other/addOperatorByExpect
 * idea/tests/testData/multiModuleQuickFix/other/cancelMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/convertActualEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertActualSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectEnumToSealedClass
 * idea/tests/testData/multiModuleQuickFix/other/convertExpectSealedClassToEnum
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyGetterToInitializer
 * idea/tests/testData/multiModuleQuickFix/other/convertPropertyToFunction
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageImport
 * idea/tests/testData/multiModuleQuickFix/other/createClassFromUsageRef
 * idea/tests/testData/multiModuleQuickFix/other/createFunInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createTestOnExpect
 * idea/tests/testData/multiModuleQuickFix/other/createValInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/createVarInExpectClass
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeader
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedHeaderImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImpl
 * idea/tests/testData/multiModuleQuickFix/other/deprecatedImplHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/functionTypeReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/generateEqualsInExpect
 * idea/tests/testData/multiModuleQuickFix/other/generateHashCodeInExpect
 * idea/tests/testData/multiModuleQuickFix/other/implementAbstractExpectMemberInheritedFromInterface
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInActualClassNoExpectMember
 * idea/tests/testData/multiModuleQuickFix/other/implementMembersInImplClassNonImplInheritor
 * idea/tests/testData/multiModuleQuickFix/other/importClassInCommon
 * idea/tests/testData/multiModuleQuickFix/other/importClassInFromProductionInCommonTests
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJs
 * idea/tests/testData/multiModuleQuickFix/other/importCommonClassInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importCommonFunInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importExpectClassWithoutActualInJvm
 * idea/tests/testData/multiModuleQuickFix/other/importFunInCommon
 * idea/tests/testData/multiModuleQuickFix/other/makeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeInlineFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeInternalFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/makeOpenFromExpect
 * idea/tests/testData/multiModuleQuickFix/other/mayBeConstantWithActual
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunParameterToReceiverByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunReceiverToParameterByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberFunToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeader
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByHeaderWithInapplicableImpl
 * idea/tests/testData/multiModuleQuickFix/other/memberValToExtensionByImpl
 * idea/tests/testData/multiModuleQuickFix/other/moveActualPropertyToExistentConstructor
 * idea/tests/testData/multiModuleQuickFix/other/movePropertyToConstructor
 * idea/tests/testData/multiModuleQuickFix/other/notMakeAbstractFromActual
 * idea/tests/testData/multiModuleQuickFix/other/orderHeader
 * idea/tests/testData/multiModuleQuickFix/other/orderImpl
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteForbiddenFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteFromActual
 * idea/tests/testData/multiModuleQuickFix/other/safeDeleteUsedInAnotherPlatform

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$PackageDirectoryMismatch has directories
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToAnotherPackage
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToCommonSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToJvmSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToAnotherPackage
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToCommonSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToJvmSourceRoot

org.jetbrains.kotlin.idea.k2.codeinsight.fixes.HighLevelQuickFixMultiModuleTestGenerated$RedundantNullableReturnType has directories
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualMethod
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectMemberProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualMethod
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectMemberProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelProperty

org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2PositionManagerTestGenerated$MultiFile has directories
 * jvm-debugger/test/testData/positionManager/multiFilePackage
 * jvm-debugger/test/testData/positionManager/multiFileSameName

org.jetbrains.kotlin.idea.k2.highlighting.OutsiderHighlightingTestGenerated has directories
 * highlighting/highlighting-k2/testData/outsider/badDiff
 * highlighting/highlighting-k2/testData/outsider/badSource
 * highlighting/highlighting-k2/testData/outsider/badSourceDiff
 * highlighting/highlighting-k2/testData/outsider/circularDependency

org.jetbrains.kotlin.idea.k2.highlighting.K2GradleBuildFileHighlightingTestGenerated$Gradle8 has directories
 * idea/tests/testData/gradle/highlighting/gradle8/gradleSampleMultiProject
 * idea/tests/testData/gradle/highlighting/gradle8/javaLibraryPlugin
 * idea/tests/testData/gradle/highlighting/gradle8/wizardMultiAllTargets
 * idea/tests/testData/gradle/highlighting/gradle8/wizardNativeUiMultiplatformApp
 * idea/tests/testData/gradle/highlighting/gradle8/wizardSharedUiMultiplatformApp
 * idea/tests/testData/gradle/highlighting/gradle8/wizardSimpleKotlinProject

org.jetbrains.kotlin.idea.k2.highlighting.K2GradleBuildFileHighlightingTestGenerated$Gradle7 has directories
 * idea/tests/testData/gradle/highlighting/gradle7/gradleSampleMultiProject
 * idea/tests/testData/gradle/highlighting/gradle7/javaLibraryPlugin
 * idea/tests/testData/gradle/highlighting/gradle7/wizardSimpleKotlinProject

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/companion

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestInnerClassWithPackage has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClassWithPackage

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestJavaInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInnerClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestJavaNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestJavaNestedInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestNamedCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/namedCompanion

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestNestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedObject

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject$TestObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/companion

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestInnerClassWithPackage has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/innerClassWithPackage

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaInnerClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInnerClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaInvoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaNestedInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/javaNestedInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestNamedCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/namedCompanion

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestNestedClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestNestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/nestedObject

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions$Constructors has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions$FromObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions$Hierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions$Members has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions$TopLevel has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestAllWithDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/allWithDefault

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestJavaConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/javaConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/jvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestNestedPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/nestedPrimaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/primaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Constructors$TestSecondaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestAllWithDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/allWithDefault

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/javaConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/jvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestNestedPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/nestedPrimaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPrimaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/primaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestSecondaryConstructor has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/constructors/secondaryConstructor

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$FromCompanion has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestFunctionWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/functionWithSeveralParameters

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$Named has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestExtensionForObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extensionForObject

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestJavaStaticMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestJavaStaticMethod2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod2

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$NestedObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extension
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/function
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtension
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/named/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/staticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/staticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/component

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/get

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/hasNext

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/invoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/iterator

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/minus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/next

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/plus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/component
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/component

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/get
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/get

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/hasNext
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/hasNext

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/invoke
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/invoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/iterator
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/iterator

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/minus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/minus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/next
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/next

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/plus
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/plus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtensionForObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/extensionForObject

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaStaticMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaStaticMethod2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/javaStaticMethod2

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticExtension
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/fromCompanion/overloadsStaticFunction
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestOverloadsExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestOverloadsFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestOverloadsStaticExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticExtension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestOverloadsStaticFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/nestedObject/overloadsStaticFunction

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFunctionWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/fromObject/functionWithSeveralParameters

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Hierarchy$TestFromLibrary has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Java$TestJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/J

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Java$TestJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Java$TestJJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Java$TestJKJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JKJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/J

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJJJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JJJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJKJ has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/java/JKJ

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Kotlin$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JJKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKJK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/kotlin/JKKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKKK
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFromLibrary has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/hierarchy/fromLibrary

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$ExtensionOperators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$InterfaceDefault has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotation

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaAnnotationWithCustomName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithCustomName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaAnnotationWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithSeveralParameters

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethod

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaMethodSyntheticGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticGet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaMethodSyntheticIs has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIs

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaMethodSyntheticIsSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIsSet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaMethodSyntheticSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestComponent has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/component

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/get

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestHasNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/hasNext

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/invoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestIterator has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/iterator

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestMinus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/minus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestNext has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/next

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestPlus has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/plus

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ExtensionOperators$TestSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/extensionOperators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/get

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/interfaceDefault/invoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Operators$TestComponentFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/componentFromDataClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestComponentFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/operators/componentFromDataClass

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotation

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaAnnotationWithCustomName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithCustomName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaAnnotationWithSeveralParameters has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaAnnotationWithSeveralParameters

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaMethod has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethod

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaMethodSyntheticGet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticGet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaMethodSyntheticIs has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIs

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaMethodSyntheticIsSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticIsSet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaMethodSyntheticSet has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/members/javaMethodSyntheticSet

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$Operators has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/operators/set

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestExtension has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extension
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extension

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestExtensionWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestExtensionWithJvmOverloadsAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithJvmOverloadsAndJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestFunction has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/function

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestFunctionWithJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestFunctionWithJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtensionWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtensionWithJvmOverloadsAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/extensionWithJvmOverloadsAndJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFunctionWithJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFunctionWithJvmOverloads has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties$ConstructorParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties$ConstructorParameterFromDataClass has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties$FromObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties$Members has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties$TopLevel has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotation

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestAnnotationWithCustomParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotationWithCustomParameter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestHierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/hierarchy

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/val

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/var

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithCustomNames

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameter$TestWithoutVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestAnnotation has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotation

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestAnnotationWithCustomParameter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/annotationWithCustomParameter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestHierarchy has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/hierarchy

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/val
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/val

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/var
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/var

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithCustomNames
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithCustomNames

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/varWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestWithoutVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameter/withoutVal

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestComponentB has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/componentB

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestEscapedName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/escapedName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestVal has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/val

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestVar has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/var

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestVarWithCustomNames has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithCustomNames

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ConstructorParameterFromDataClass$TestVarWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/varWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestComponentB has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/componentB

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestEscapedName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/constructorParameterFromDataClass/escapedName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticLateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromObject$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestJavaStaticField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestJavaStaticField2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField2

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithBackingField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticLateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$FromCompanion$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticLateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Named$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/constant
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/extensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fieldVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/lateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/property
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtensionVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticExtensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticLateinit
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticLateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticProperty
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticVariable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/staticVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/named/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variable
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaStaticField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaStaticField2 has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/javaStaticField2

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithBackingField
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithBackingField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithBackingField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/fromCompanion/propertyWithCustomGetterAndSetter
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithCustomGetterAndSetter
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestFieldProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestFieldVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/fieldVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticExtensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticLateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestStaticVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/staticVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$NestedObject$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/fromObject/nestedObject/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JJKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Get$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/get/JKKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JJKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJKJK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKJK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Set$TestJKKK has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/hierarchy/kotlin/set/JKKK

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestJavaFieldWithInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaFieldWithInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithBackingField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestPropertyWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithReceiver

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Members$TestVariableWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestIsVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/isVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$InterfaceDefault$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/interfaceDefault/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestJavaFieldWithInvoke has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/javaFieldWithInvoke

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestPropertyWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/propertyWithReceiver

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithJvmField
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithReceiver has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/members/variableWithReceiver

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestConstant has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constant

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestConstantJava has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJava

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestConstantJavaWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJavaWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestConstantWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestExtensionVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestExtensionVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestIsProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestIsVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isVariableWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestLateinit has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/lateinit

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/property

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestPropertyWithBackingField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithBackingField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestPropertyWithCustomGetterAndSetter has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithCustomGetterAndSetter

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestPropertyWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/propertyWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariable has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variable

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariableWithCustomGetterAndSetterAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariableWithCustomGetterAndSetterAndMixedJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndMixedJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariableWithJvmField has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmField

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TopLevel$TestVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestConstantJava has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJava

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestConstantJavaWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantJavaWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestConstantWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/constantWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestExtensionVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/extensionVariableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestIsVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/isVariableWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithCustomFileName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomFileName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithCustomGetterAndSetterAndJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithCustomGetterAndSetterAndMixedJvmName has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithCustomGetterAndSetterAndMixedJvmName

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$TestVariableWithJvmNameOnProperty has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library$Any has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library$Long has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library$Object has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library$String_ has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Any$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/any/hashCode
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Long$Class has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Class$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/long/class/class
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Object$TestHashCode has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$String_$TestClass has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/class

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$String_$TestLength has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$TestLength has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/string_/length

org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.LocalSessionInvalidationTestGenerated has directories
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclical
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linearWithCyclicalDependency

org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.GlobalSessionInvalidationTestGenerated has directories
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclical
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linearWithCyclicalDependency

org.jetbrains.kotlin.idea.fir.analysis.providers.dependents.ModuleDependentsTestGenerated has directories
 * base/fir/analysis-api-providers/testData/moduleDependents/binaryTree
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclical
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclicalSelf
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/moduleDependents/deduplicatedLibraries
 * base/fir/analysis-api-providers/testData/moduleDependents/linear
 * base/fir/analysis-api-providers/testData/moduleDependents/rhombus
 * base/fir/analysis-api-providers/testData/moduleDependents/singleRoot
 * base/fir/analysis-api-providers/testData/moduleDependents/specialDependents

org.jetbrains.kotlin.idea.fir.analysis.providers.sealedInheritors.SealedInheritorsProviderTestGenerated has directories
 * base/fir/analysis-api-providers/testData/sealedInheritors/ambiguousLibrarySealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/ambiguousLibrarySealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/ambiguousSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/ambiguousSealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/ambiguousSimpleNamesInHierarchy
 * base/fir/analysis-api-providers/testData/sealedInheritors/illegalDistributedSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/illegalExtendedLibrarySealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/indirectInheritorsLibrarySealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/indirectInheritorsLibrarySealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/indirectInheritorsSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/indirectInheritorsSealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/kmpExpectActual
 * base/fir/analysis-api-providers/testData/sealedInheritors/librarySealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/librarySealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/nestedLibrarySealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/nestedLibrarySealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/nestedSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/nestedSealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/otherModuleSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/otherModuleSealedInterface
 * base/fir/analysis-api-providers/testData/sealedInheritors/sameModuleSealedClass
 * base/fir/analysis-api-providers/testData/sealedInheritors/sameModuleSealedClassWithIrregularPackage
 * base/fir/analysis-api-providers/testData/sealedInheritors/sameModuleSealedInterface

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithCompiledLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithCrossLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/enumEntryMethods
 * idea/tests/testData/resolve/referenceWithLib/enumSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride
 * idea/tests/testData/resolve/referenceWithLib/fakeOverride2
 * idea/tests/testData/resolve/referenceWithLib/infinityAndNanInJavaAnnotation
 * idea/tests/testData/resolve/referenceWithLib/innerClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/iteratorWithTypeParameter
 * idea/tests/testData/resolve/referenceWithLib/multiDeclarationWithTypeParameters
 * idea/tests/testData/resolve/referenceWithLib/namedArguments
 * idea/tests/testData/resolve/referenceWithLib/nestedClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/overloadFun
 * idea/tests/testData/resolve/referenceWithLib/overridingFunctionWithSamAdapter
 * idea/tests/testData/resolve/referenceWithLib/packageOfLibDeclaration
 * idea/tests/testData/resolve/referenceWithLib/referenceToRootJavaClassFromLib
 * idea/tests/testData/resolve/referenceWithLib/sameNameInLib
 * idea/tests/testData/resolve/referenceWithLib/setWithTypeParameters

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithCompilerPluginsWithLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithCompilerPluginsWithCompiledLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithCompilerPluginsWithCrossLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithCompilerPluginsWithLib/serialization

org.jetbrains.kotlin.idea.fir.navigation.FirGotoRelatedSymbolMultiModuleTestGenerated has directories
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromActualMemberFunToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromActualMemberValToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromExpectMemberFunToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromExpectMemberValToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromNestedActualClassToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromNestedExpectClassToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualClassToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualFunToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelActualValToExpect
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectClassToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectFunToActuals
 * idea/tests/testData/navigation/relatedSymbols/multiModule/fromTopLevelExpectValToActuals

org.jetbrains.kotlin.idea.fir.completion.K2MultiPlatformCompletionTestGenerated has directories
 * completion/testData/multiPlatform/classInCommon
 * completion/testData/multiPlatform/classInCommonNonImported
 * completion/testData/multiPlatform/classInPlatform
 * completion/testData/multiPlatform/functionInCommon
 * completion/testData/multiPlatform/functionInPlatform

org.jetbrains.kotlin.idea.fir.codeInsight.K2MultiModuleLineMarkerTestGenerated has directories
 * code-insight/testData/linemarkers/multiplatform

org.jetbrains.kotlin.idea.fir.resolve.K2MultiModuleHighlightingTestGenerated has directories
 * fir/tests/testData/resolve/anchors/anchorInDependentModule
 * fir/tests/testData/resolve/anchors/anchorInSameModule
 * fir/tests/testData/resolve/anchors/anchorInSameModuleJavaDependency
## K1 only cases

648 K1 only cases (8099 files):

 * org.jetbrains.kotlin.DataFlowValueRenderingTestGenerated
 * org.jetbrains.kotlin.addImport.AddImportTestGenerated
 * org.jetbrains.kotlin.addImportAlias.AddImportAliasTest53Generated
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Delegation
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Facades
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$NullabilityAnnotations
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Uncategorized
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByFqNameTestGenerated$CompilationErrors
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByFqNameTestGenerated$IdeRegression
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByFqNameTestGenerated$Script
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByPsiTestGenerated$Facades
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByPsiTestGenerated$Scripts
 * org.jetbrains.kotlin.asJava.classes.IdeLightClassesByPsiTestGenerated$Uncategorized
 * org.jetbrains.kotlin.checkers.JavaAgainstKotlinBinariesCheckerTestGenerated
 * org.jetbrains.kotlin.checkers.JavaAgainstKotlinSourceCheckerTestGenerated$JavaWithKotlin
 * org.jetbrains.kotlin.checkers.JsCheckerTestGenerated
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$AfterUnmatchedBrace
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$ArrayAccess
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$BeforeDot
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$Commenter
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$ContextReceivers
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$ControlFlowConstructions
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$Elvis
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$EmptyBraces
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$EmptyParameters
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$EmptyParenthesisInBinaryExpression
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$ExpressionBody
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$MultilineString
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$Script
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$Templates
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$DirectSettings$Uncategorized
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$BeforeDot
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$Elvis
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$EmptyParameters
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$EmptyParenthesisInBinaryExpression
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$ExpressionBody
 * org.jetbrains.kotlin.formatter.EnterHandlerTestGenerated$InvertedSettings$Uncategorized
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$CallChain
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$FileAnnotations
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$ModifierList
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$ParameterList
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$TrailingComma
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$Formatter$Uncategorized
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$CollectionLiteralExpression
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$Indices
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$LambdaParameters
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$TypeArguments
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$TypeParameters
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$ValueArguments
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterCallSite$ValueParameters
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInverted$CallChain
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInverted$ParameterList
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInverted$TrailingComma
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInverted$Uncategorized
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$CollectionLiteralExpression
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$Indices
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$LambdaParameters
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$TypeArguments
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$TypeParameters
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$ValueArguments
 * org.jetbrains.kotlin.formatter.FormatterTestGenerated$FormatterInvertedCallSite$ValueParameters
 * org.jetbrains.kotlin.idea.ExpressionSelectionTestGenerated
 * org.jetbrains.kotlin.idea.SmartSelectionTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.ChangeLocalityDetectorTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.ExpressionTypeTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.InsertImportOnPasteTestGenerated$Cut
 * org.jetbrains.kotlin.idea.codeInsight.LineMarkersTestGenerated$RecursiveCall
 * org.jetbrains.kotlin.idea.codeInsight.LineMarkersTestGenerated$SuspendCall
 * org.jetbrains.kotlin.idea.codeInsight.LineMarkersTestInLibrarySourcesGenerated
 * org.jetbrains.kotlin.idea.codeInsight.MoveOnCutPasteTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.MultiFileInspectionTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.OutOfBlockModificationTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.PairMatcherTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.RenderingKDocTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionProviderTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.generate.CodeInsightActionTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateHashCodeAndEqualsActionTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateTestSupportMethodActionTestGenerated$JUnit4
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateTestSupportMethodActionTestGenerated$JUnit5
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateTestSupportMethodActionTestGenerated$Junit3
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateTestSupportMethodActionTestGenerated$TestNG
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateToStringActionTestGenerated$Common
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateToStringActionTestGenerated$MultipleTemplates
 * org.jetbrains.kotlin.idea.codeInsight.generate.GenerateToStringActionTestGenerated$SingleTemplate
 * org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.SerializationPluginIdeDiagnosticTestGenerated
 * org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.SerializationQuickFixTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.Java8BasicCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$AfterAs
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$AnonymousObject
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$CallableReference
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$Constructor
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$ForLoopRange
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$FunctionLiterals
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$Generics
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$HeuristicSignatures
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$IfValue
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$InElvisOperator
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$InOperator
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$Inheritors
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$LambdaSignature
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$MultipleArgsItem
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$PropertyDelegate
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$SmartCasts
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$This
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$Vararg
 * org.jetbrains.kotlin.idea.completion.test.JvmSmartCompletionTestGenerated$WhenEntry
 * org.jetbrains.kotlin.idea.completion.test.K1CompletionIncrementalResolveTestGenerated$Smart
 * org.jetbrains.kotlin.idea.completion.test.K1JSBasicCompletionTestGenerated$Js
 * org.jetbrains.kotlin.idea.completion.test.KotlinSourceInJavaCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.KotlinStdLibInJavaCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.MultiFileSmartCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$Lambda
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$LambdaSignature
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$SuspendLambdaSignature
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.completion.test.weighers.SmartCompletionWeigherTestGenerated
 * org.jetbrains.kotlin.idea.coverage.KotlinCoverageOutputFilesTestGenerated
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentAutoImportTestGenerated
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentCompletionHandlerTestGenerated
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentCompletionTestGenerated$RuntimeType
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentCompletionTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentHighlightingTestGenerated$CodeFragments
 * org.jetbrains.kotlin.idea.debugger.evaluate.CodeFragmentHighlightingTestGenerated$Imports
 * org.jetbrains.kotlin.idea.debugger.test.BreakpointApplicabilityTestGenerated
 * org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeBreakpointHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionInMppTestGenerated$Multiplatform
 * org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated$JvmMultiModule$Delegates
 * org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated$JvmMultiModule$Uncategorized
 * org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeScriptEvaluateExpressionTestGenerated
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Append
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Distinct
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Filter
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$FlatMap
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Map
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Misc
 * org.jetbrains.kotlin.idea.debugger.test.sequence.exec.IrSequenceTraceWithIREvaluatorTestCaseGenerated$Sort
 * org.jetbrains.kotlin.idea.decompiler.navigation.NavigateJavaSourceToLibrarySourceTestGenerated
 * org.jetbrains.kotlin.idea.decompiler.navigation.NavigateJavaSourceToLibraryTestGenerated
 * org.jetbrains.kotlin.idea.decompiler.navigation.NavigateToDecompiledLibraryTestGenerated
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Class$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ClassFun
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ClassMembers
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ClassObject
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Classes
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Constructor$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ContextReceivers
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Coroutines
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$DataClass
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$DefaultAccessors
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Error
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$FromLoadJava$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Fun$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Fun$Vararg
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$GenericWithTypeVariables
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$GenericWithoutTypeVariables
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Inline
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$JavaBean
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$KotlinSignature$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Library
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$MemberOrder
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Modality
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$NestedClasses
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$NonGeneric
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$NotNull
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$PackageMembers
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Parameter
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Parameters
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$PlatformTypes
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Prop$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Propagation$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$PropertiesWithoutBackingFields
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Return
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Type
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$TypeParameter
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Typealias
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Types
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Vararg
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Visibility
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$WithUseSiteTarget
 * org.jetbrains.kotlin.idea.decompiler.textBuilder.CommonDecompiledTextTestGenerated
 * org.jetbrains.kotlin.idea.decompiler.textBuilder.JvmDecompiledTextTestGenerated
 * org.jetbrains.kotlin.idea.editor.backspaceHandler.BackspaceHandlerTestGenerated$StringTemplate
 * org.jetbrains.kotlin.idea.editor.backspaceHandler.BackspaceHandlerTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.editor.commenter.KotlinCommenterTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.DiagnosticMessageJsTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.DiagnosticMessageTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.DslHighlighterTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.KotlinReceiverUsageHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.UsageHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.imports.JsOptimizeImportsTestGenerated$Js
 * org.jetbrains.kotlin.idea.imports.JvmOptimizeImportsTestGenerated$Jvm$Uncategorized
 * org.jetbrains.kotlin.idea.index.KotlinTypeAliasByExpansionShortNameIndexTestGenerated
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$Inspections
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$InspectionsLocal
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$Intentions
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$AddArrow
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$AlsoToApply
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$AnonymousFunction
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ApplyToAlso
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ArrayInDataClass
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ArrayList
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$BlockElse
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$BlockElseUsedAsExpression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CallableReferenceExpressions
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CanBeParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CanBeVal
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CatchIgnoresException
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Check
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CheckNotNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Comment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Commons
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Compare
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Complex
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConstantConditionIf
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConvertObjectToDataObject
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConvertToExpressionBody$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConvertTwoComparisonsToRangeCheck
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Default
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$DeprecatedCallableAddReplaceWith
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$DotQualifiedExpressions
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$DoubleNegation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EmptyRange
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EnumValuesSoftDeprecate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EqualsBetweenInconvertibleTypes
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EqualsOrHashCode
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ExplicitThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$False
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$FloatingPoint
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$FloatingPointLiteralPrecision
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Function
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$FunctionWithLambdaExpressionBody$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$HashSet
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IfThenToElvis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IfThenToSafeAccess
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IfToAssignment$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IfToReturn$OnlySingleStatement
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IfToReturn$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IgnoreChainedIf$False
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IgnoreChainedIf$True
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ImplicitNullableNothingType
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ImplicitThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IncompleteDestructuringInspection
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$InconsistentCommentForJavaParameter$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$InvertEmptinessCheck
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Io
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JavaCollectionsStaticMethod
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JavaMapForEach
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JoinDeclarationAndAssignment$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$KeepComments
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LambdaExpression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LeakingThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LetToRun
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LocalVariableName
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Log4j
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Log4j2
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MainFunctionReturnUnit
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MapSumWithConstant
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Math
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MayBeConstant
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MigrateDiagnosticSuppression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MoveLambdaOutsideParentheses
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MoveToPrevLine
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MoveVariableDeclarationIntoWhen
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NestedLambdaShadowedImplicitParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonExhaustiveWhenStatementMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonExternalClassifierExtendingStateOrProps
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonNullableBooleanPropertyInExternalInterface
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonVarPropertyInExternalInterface
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NullChecksToSafeCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NullableBooleanElvis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ObjectLiteralToLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$OnlySingleStatement$False
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$OnlySingleStatement$True
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$OverrideDeprecatedMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$PrimitiveArray
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitJvmOverloadsOnConstructorsOfAnnotationClassesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitJvmOverloadsOnConstructorsOfAnnotationClassesMigration1_3
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitRepeatedUseSiteTargetAnnotationsMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitTypeParametersForLocalVariablesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitUseSiteTargetAnnotationsOnSuperTypesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitUseSiteTargetAnnotationsOnSuperTypesMigration1_3
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Property
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Readln
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReadlnOrNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Receivers
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RecursiveEqualsCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RecursivePropertyAccessor
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantAsSequence
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantCompanionReference
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantDiagnosticSuppress
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantElseInIf
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantElvisReturnNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantEnumConstructorInvocation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantInnerClassModifier
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLabelMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLambdaArrow
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLambdaOrAnonymousFunction$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantOverride
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantRequireNotNullCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantRunCatching
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantSamConstructor
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantUnitExpression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantUnitReturnType
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantVisibilityModifier
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantWith
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReferenceExpression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveBraces
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveEmptyParenthesesFromAnnotationEntry
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveExplicitTypeArguments
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveRedundantQualifierName
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveRedundantSpreadOperator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveToStringInStringTemplate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceArrayOfWithLiteral
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceAssertBooleanWithAssertEquality
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceCallWithBinaryOperator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceCollectionCountWithSize
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceGetOrSet
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceGuardClauseWithFunctionCall$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceJavaStaticMethodWithKotlinAnalog$Collections
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceManualRangeWithIndicesCalls
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceMapIndexedWithListGenerator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceNegatedIsEmptyWithIsNotEmpty
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceNotNullAssertionWithElvisReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplacePutWithAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeStartEndInclusiveWithFirstLast
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeToWithRangeUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeToWithUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceReadLineWithReadln$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceStringFormatWithLiteral
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceToStringWithStringTemplate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceToWithInfixForm
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceUntilWithRangeUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithEnumMap
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithIgnoreCaseEquals
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithImportAlias
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithOperatorAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithStringBuilderAppendRange
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReportImmediatelyReturnedVariables
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReportNonTrivialAccessors$Default
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReportNonTrivialAccessors$False
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReportNonTrivialAccessors$True
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Require
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RequireNotNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RestrictReturnStatementTargetMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RunToLet
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SafeCastWithReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SelfAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SelfReferenceConstructorParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SetterBackingFieldAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifiableCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifiableCallChain$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyAssertNotNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyNestedEachInScope
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyWhenWithBooleanConstantCondition
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SingleElse
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Slf4j
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SpecifyType
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SubjectVariable$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspendFunctionOnCoroutineScope
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousAsDynamic
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousCallableReferenceInLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousCollectionReassignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousVarProperty
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$System
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ThrowableNotThrown
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ToString
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$True
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$TryToAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$TryToReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnlabeledReturnInsideLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnnecessaryOptInAnnotation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnnecessaryVariable$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnsafeCastFromDynamic
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedDataClassCopyResult
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedEquals
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedLambdaExpressionBody
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedMainParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedReceiverParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedUnaryOperator$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UseExpressionBody$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UselessCallOnCollection
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UselessCallOnNotNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Util
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$Array
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$Collection
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$IntArray
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$List
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$Map
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$Set
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness$Str
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WarningOnMainUnusedParameterMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WhenToAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WhenToReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WhenWithOnlyElse$Uncategorized
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Whitespace
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WithDropLast
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WithIndexingOperation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WithSubstringAfter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WithSubstringBefore
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WithTake
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WrapRun
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Wrapped
 * org.jetbrains.kotlin.idea.inspections.MultiFileLocalInspectionTestGenerated
 * org.jetbrains.kotlin.idea.inspections.ViewOfflineInspectionTestGenerated$LocalVariableName
 * org.jetbrains.kotlin.idea.inspections.ViewOfflineInspectionTestGenerated$SuspiciousCollectionReassignment
 * org.jetbrains.kotlin.idea.intentions.ConcatenatedStringGeneratorTestGenerated
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Any
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Contains
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Count
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Filter
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$FirstOrNull
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$FlatMap
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$ForEach
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$IndexOf
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$IntroduceIndex
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Map
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$MaxMin
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$SmartCasts
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Sum
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$TakeWhile
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$ToCollection
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTest2Generated$Uncategorized
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$AllToNone$Uncategorized
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$AnyToNone$ToAny
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$AssignmentToIf
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$AssignmentToWhen
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Delegate
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$EliminateSubject
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$FilterNotToFilter
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$FilterNotToToFilterTo
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$FilterToFilterNot
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$FilterToToFilterNotTo
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Function
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$NoneToAll
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Predicate
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Property$Uncategorized
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Property$Val
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Property$Var
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$PropertyToIf
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$PropertyToWhen
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$RemoveExplicitTypeWithApiMode
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$ReturnToIf
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$SpecifyTypeExplicitlyInDestructuringAssignment
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$TakeIfToTakeUnless
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$TakeUnlessToTakeIf
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$ToAll
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$ToAny
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$ToNone
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$Val
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$ValNoBacking
 * org.jetbrains.kotlin.idea.intentions.MultiFileIntentionTestGenerated
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$AddSemicolon
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$DeclarationAndAssignment
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$InitializerAndIfToElvis
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$NestedIfs
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$RemoveBraces
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$RemoveTrailingComma
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$StringTemplate
 * org.jetbrains.kotlin.idea.intentions.declarations.JoinLinesTestGenerated$WhenEntry
 * org.jetbrains.kotlin.idea.kdoc.KDocHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.kdoc.KDocTypingTestGenerated
 * org.jetbrains.kotlin.idea.kdoc.KdocResolveTestGenerated
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_01
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_02
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_03
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_04
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_05
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_06
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_07
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_08
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_09
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_10
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_11
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_12
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_13
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_14
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_15
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_16
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_17
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_18
 * org.jetbrains.kotlin.idea.maven.KotlinMavenInspectionTestGenerated$_19
 * org.jetbrains.kotlin.idea.navigation.GotoSuperTestGenerated
 * org.jetbrains.kotlin.idea.navigation.KotlinGotoImplementationTestGenerated
 * org.jetbrains.kotlin.idea.navigationToolbar.KotlinNavBarTestGenerated
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$AddNameToArgument
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$AnnotationEntry
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$BinaryOperations
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Call$TypeArguments
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$CallExpression$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$CallableReferences
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$CanBeVal
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ClassUsages$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$CodeStructure
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Component
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ConvertCollection
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$DataClassConstructorVsCopyVisibility
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$DefinitelyNonNullableTypes
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$DelegateAccessors
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$DelegationSpecifier
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Extension
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ExtensionByExtensionReceiver
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$FixOverloadedOperator
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$FromChar
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$FunctionLiteralArguments$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$General
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Get
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$HasNext
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ImportDirective$Kt21515
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ImportDirective$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$InContainingDeclaration
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$IncompatibleTypes
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Invoke
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Iterator
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$LocalVariable
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Member
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Next
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$NothingToOverride
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$NumberConversion$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$OverrideDeprecation
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Parameter
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$PrimaryParameter
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Property$Abstract
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ReferenceExpression
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ReplaceWithSafeCallForScopeFunction
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$RoundNumber
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Set
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Simple
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ToByte
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ToChar
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ToShort
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$TypeArguments
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$TypeArguments$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$Unavailable
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$WithLocalElements
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$WrapWithCollectionLiteral
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddAnnotationTarget
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddExclExclCall
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddGenericUpperBound
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddOpenToClassDeclaration
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddSpreadOperatorForArrayAsVarargAfterSam
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AnnotationEntry
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Call$TypeArguments
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$CallExpression$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ChangeSignature$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ClassUsages$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ConflictingExtension
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ConvertJavaInterfaceToClass
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$CreateSecondaryConstructor
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$DefinitelyNonNullableTypes
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$DelegationSpecifier
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$DeprecatedSymbolUsage$TypeArguments
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$DeprecatedSymbolUsage$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Extension
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$FieldFromJava
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$FromJava
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$FromKotlinToJava
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$GenericVarianceViolation
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ImportDirective
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Imports
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$IncreaseVisibility
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$JavaAnnotationPositionedArguments
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Jk
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Kj
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MakePrivateAndOverrideMember
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MakeUpperBoundNonNullable
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Member
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MemberVisibilityCanBePrivate
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Modifiers$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MoveToSealedParent
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$NothingToOverride
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Parameter
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$PrimaryParameter
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Property$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ReferenceExpression
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$RemoveUnused
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ReplaceWithSafeCall
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ReplaceWithSafeCallForScopeFunction
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Simple
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$SpecifySuperExplicitly
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$SurroundWithNullCheck
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeAliases$WholeProject
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeArguments
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeImports
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeMismatch$Uncategorized
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeReference
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$When
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$WholeProject
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$WrapWithSafeLetCall
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$WrongNullabilityForJavaOverride
 * org.jetbrains.kotlin.idea.refactoring.NameSuggestionProviderTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.copy.CopyTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.copy.MultiModuleCopyTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineMultiFileTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$FromFinalJavaToKotlin
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$FromJavaToKotlin
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$FromKotlinToJava
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$InlineTypeAlias
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$JavaUsages
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$NamedFunction$FromFinalJavaToKotlin
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$NamedFunction$FromJavaToKotlin
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestWithSomeDescriptorsGenerated
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractInterface
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractSuperclass
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceConstant$BinaryExpression
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceConstant$DotQualifiedExpression
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceConstant$StringTemplates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceConstant$Uncategorized
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceJavaParameter
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceLambdaParameter$Multiline
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceLambdaParameter$StringTemplates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceLambdaParameter$Uncategorized
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceTypeAlias
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceTypeParameter
 * org.jetbrains.kotlin.idea.refactoring.move.MoveTestGenerated$MoveDirectory
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$J2K
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$K2J
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$K2K
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$J2K
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$K2J
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$K2K
 * org.jetbrains.kotlin.idea.refactoring.safeDelete.SafeDeleteTestGenerated$KotlinClassWithJava
 * org.jetbrains.kotlin.idea.refactoring.safeDelete.SafeDeleteTestGenerated$KotlinFunctionWithJava
 * org.jetbrains.kotlin.idea.refactoring.safeDelete.SafeDeleteTestGenerated$KotlinPropertyWithJava
 * org.jetbrains.kotlin.idea.repl.IdeReplCompletionTestGenerated
 * org.jetbrains.kotlin.idea.resolve.AdditionalResolveDescriptorRendererTestGenerated
 * org.jetbrains.kotlin.idea.resolve.PartialBodyResolveTestGenerated
 * org.jetbrains.kotlin.idea.resolve.ReferenceToJavaWithWrongFileStructureTestGenerated
 * org.jetbrains.kotlin.idea.resolve.ResolveModeComparisonTestGenerated
 * org.jetbrains.kotlin.idea.scratch.ScratchLineMarkersTestGenerated
 * org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated$ScratchRepl
 * org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated$ScratchRightPanelOutput
 * org.jetbrains.kotlin.idea.scratch.ScratchRunActionTestGenerated$WorksheetRepl
 * org.jetbrains.kotlin.idea.slicer.SlicerNullnessGroupingTestGenerated
 * org.jetbrains.kotlin.idea.slicer.SlicerTreeTestGenerated$Outflow
 * org.jetbrains.kotlin.idea.stubs.MultiFileHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.stubs.StubBuilderTestGenerated
 * org.jetbrains.kotlin.parcelize.ide.test.ParcelizeK1CheckerTestGenerated
 * org.jetbrains.kotlin.search.InheritorsSearchTestGenerated
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Constructor
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Imports
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Java
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Kt21515
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Type
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Typealias
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Uncategorized
---
