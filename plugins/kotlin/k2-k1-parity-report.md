# K2/K1 feature parity report


Generated on Fri Dec 08 13:03:59 CET 2023

## Shared cases
shared 9875 files out of 700 cases

| Status | Case name | Success rate, % | K2 files | K1 files | Total files |
| -- | -- | --  | -- | -- | -- |
 | :white_check_mark: | [FirParameterInfoTestGenerated] | 93 | 117 | 126 | 126 | 
 | :x: | FirParameterInfoTestGenerated$WithLib3 | 0 | 0 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$ArrayAccess | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$FunctionCall | 92 | 82 | 89 | 89 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$Annotations | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$TypeArguments | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib1 | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | FirParameterInfoTestGenerated$WithLib2 | 100 | 1 | 1 | 1 | 
 | :x: | [HighLevelQuickFixTestGenerated] | 27 | 420 | 1566 | 1571 | 
 | :x: | HighLevelQuickFixTestGenerated$AddAnnotationTarget | 0 | 0 | 30 | 30 | 
 | :x: | HighLevelQuickFixTestGenerated$AddAnnotationUseSiteTarget | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConstructorParameter | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConstructorParameterFromSuperTypeCall | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$AddConversionCall | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AddCrossinline | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddDataModifier | 0 | 0 | 16 | 16 | 
 | :x: | HighLevelQuickFixTestGenerated$AddDefaultConstructor | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddElseBranchToIf | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$AddEmptyArgumentList | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddEqEqTrue | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$AddFunModifier | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$AddGenericUpperBound | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$AddInline | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddInlineToReifiedFunctionFix | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddIsToWhenCondition | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddJvmInline | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddJvmStaticAnnotation | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddNewLineAfterAnnotations | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$AddNoinline | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReifiedToTypeParameterOfFunctionFix | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnExpression | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnToLastExpressionInFunction | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddReturnToUnusedLastExpressionInFunction | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddRunBeforeLambda | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$AddSemicolonBeforeLambdaExpression | 0 | 0 | 13 | 13 | 
 | :x: | HighLevelQuickFixTestGenerated$AddStarProjections | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddSuspend | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$AddTypeAnnotationToValueParameter | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$AddUnsafeVarianceAnnotation | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$AddValVar | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AddVarianceModifier | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$AssignToProperty | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$CallFromPublicInline | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$CanBeParameter | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$CanBePrimaryConstructorProperty | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$CastDueToProgressionResolveChange | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeObjectToClass | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeSignature | 0 | 0 | 63 | 63 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeSuperTypeListEntryTypeArgument | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToLabeledReturn | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToMutableCollection | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$ChangeToUseSpreadOperator | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$CompilerError | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertCollectionLiteralToIntArrayOf | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertIllegalEscapeToUnicodeEscape | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertLateinitPropertyToNotNullDelegate | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertPropertyInitializerToGetter | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertToAnonymousObject | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$ConvertToIsArrayOfCall | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$CreateFromUsage | 0 | 0 | 46 | 46 | 
 | :x: | HighLevelQuickFixTestGenerated$CreateLabel | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$DeclarationCantBeInlined | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$DeclaringJavaClass | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$DecreaseVisibility | 0 | 0 | 14 | 14 | 
 | :x: | HighLevelQuickFixTestGenerated$DeprecatedJavaAnnotation | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$DeprecatedSymbolUsage | 0 | 0 | 23 | 23 | 
 | :x: | HighLevelQuickFixTestGenerated$EqualityNotApplicable | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$Final | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$FoldTryCatch | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$FunctionWithLambdaExpressionBody | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$Implement | 0 | 0 | 39 | 39 | 
 | :x: | HighLevelQuickFixTestGenerated$ImportAlias | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$IncreaseVisibility | 0 | 0 | 22 | 22 | 
 | :x: | HighLevelQuickFixTestGenerated$InitializeWithConstructorParameter | 0 | 0 | 17 | 17 | 
 | :x: | HighLevelQuickFixTestGenerated$InlineClass | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$InlineTypeParameterFix | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$InsertDelegationCall | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$IsEnumEntry | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$JavaClassOnCompanion | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$KdocMissingDocumentation | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$LeakingThis | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$Libraries | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$MakeConstructorParameterProperty | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$MakeTypeParameterReified | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$MakeUpperBoundNonNullable | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$MemberVisibilityCanBePrivate | 0 | 0 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$Migration | 0 | 0 | 2 | 2 | 
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
 | :x: | HighLevelQuickFixTestGenerated$RemoveUnused | 0 | 0 | 28 | 28 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUnusedParameter | 0 | 0 | 24 | 24 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUnusedReceiver | 0 | 0 | 2 | 2 | 
 | :x: | HighLevelQuickFixTestGenerated$RemoveUseSiteTarget | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameToRem | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameToUnderscore | 0 | 0 | 8 | 8 | 
 | :x: | HighLevelQuickFixTestGenerated$RenameUnresolvedReference | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$ReorderParameters | 0 | 0 | 11 | 11 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceJvmFieldWithConst | 0 | 0 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$RestrictedRetentionForExpressionAnnotation | 0 | 0 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$SimplifyComparison | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$SmartCastImpossibleInIfThen | 0 | 0 | 12 | 12 | 
 | :x: | HighLevelQuickFixTestGenerated$SpecifyOverrideExplicitly | 0 | 0 | 7 | 7 | 
 | :x: | HighLevelQuickFixTestGenerated$SpecifySuperExplicitly | 0 | 0 | 10 | 10 | 
 | :x: | HighLevelQuickFixTestGenerated$SpecifyTypeExplicitly | 0 | 0 | 1 | 1 | 
 | :x: | HighLevelQuickFixTestGenerated$SuperTypeIsExtensionType | 0 | 0 | 3 | 3 | 
 | :x: | HighLevelQuickFixTestGenerated$Suppress | 0 | 0 | 4 | 4 | 
 | :x: | HighLevelQuickFixTestGenerated$SurroundWithNullCheck | 0 | 0 | 24 | 24 | 
 | :x: | HighLevelQuickFixTestGenerated$SuspiciousCollectionReassignment | 0 | 0 | 1 | 1 | 
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
 | :x: | HighLevelQuickFixTestGenerated$Lateinit | 33 | 2 | 6 | 6 | 
 | :x: | HighLevelQuickFixTestGenerated$SurroundWithArrayOfForNamedArgumentsToVarargs | 44 | 4 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$SupertypeInitialization | 50 | 16 | 32 | 32 | 
 | :x: | HighLevelQuickFixTestGenerated$Override | 58 | 14 | 24 | 24 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceWithDotCall | 67 | 6 | 9 | 9 | 
 | :x: | HighLevelQuickFixTestGenerated$Modifiers | 70 | 48 | 69 | 69 | 
 | :x: | HighLevelQuickFixTestGenerated$TypeAddition | 70 | 14 | 20 | 20 | 
 | :x: | HighLevelQuickFixTestGenerated$When | 70 | 26 | 37 | 37 | 
 | :x: | HighLevelQuickFixTestGenerated$AutoImports | 71 | 35 | 49 | 49 | 
 | :x: | HighLevelQuickFixTestGenerated$Abstract | 74 | 26 | 35 | 35 | 
 | :x: | HighLevelQuickFixTestGenerated$ReplaceWithArrayCallInAnnotation | 80 | 4 | 5 | 5 | 
 | :x: | HighLevelQuickFixTestGenerated$AddExclExclCall | 84 | 31 | 37 | 37 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ReplaceInfixOrOperatorCall | 86 | 19 | 22 | 22 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ReplaceWithSafeCall | 86 | 25 | 29 | 29 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Expressions | 90 | 36 | 40 | 40 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Nullables | 92 | 11 | 12 | 12 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$WrapWithSafeLetCall | 94 | 32 | 34 | 34 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddInitializer | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$AddPropertyAccessors | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$CheckArguments | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ConflictingImports | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ConvertToBlockBody | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$LocalVariableWithTypeParameters | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$RemoveRedundantSpreadOperator | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SpecifySuperType | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$SpecifyVisibilityInExplicitApiMode | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$Supercalls | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$ToString | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | HighLevelQuickFixTestGenerated$WrongPrimitive | 100 | 14 | 14 | 14 | 
 | :x: | [K2IndyLambdaKotlinSteppingTestGenerated] | 74 | 233 | 314 | 314 | 
 | :x: | K2IndyLambdaKotlinSteppingTestGenerated$SmartStepInto | 0 | 0 | 18 | 18 | 
 | :x: | K2IndyLambdaKotlinSteppingTestGenerated$Custom | 52 | 68 | 131 | 131 | 
 | :white_check_mark: | K2IndyLambdaKotlinSteppingTestGenerated$Filters | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | K2IndyLambdaKotlinSteppingTestGenerated$StepIntoOnly | 100 | 25 | 25 | 25 | 
 | :white_check_mark: | K2IndyLambdaKotlinSteppingTestGenerated$StepOut | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2IndyLambdaKotlinSteppingTestGenerated$StepOver | 100 | 118 | 118 | 118 | 
 | :x: | [K2IntentionTestGenerated] | 32 | 657 | 2071 | 2084 | 
 | :x: | K2IntentionTestGenerated$AddAnnotationUseSiteTarget | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$AddForLoopIndices | 0 | 0 | 14 | 14 | 
 | :x: | K2IntentionTestGenerated$AddJvmOverloads | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$AddJvmStatic | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$AddLabeledReturnInLambda | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$AddMissingDestructuring | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$AddThrowsAnnotation | 0 | 0 | 29 | 29 | 
 | :x: | K2IntentionTestGenerated$AddValOrVar | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$AnonymousFunctionToLambda | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$Branched | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ChangeVisibility | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ConvertArgumentToSet | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$ConvertArrayParameterToVararg | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$ConvertBlockCommentToLineComment | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertCamelCaseTestFunctionToSpaced | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertCollectionConstructorToFunction | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertEnumToSealedClass | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$ConvertFilteringFunctionWithDemorgansLaw | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionToProperty | 0 | 0 | 32 | 32 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionTypeParameterToReceiver | 0 | 0 | 19 | 19 | 
 | :x: | K2IntentionTestGenerated$ConvertFunctionTypeReceiverToParameter | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$ConvertLambdaToMultiLine | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertLambdaToReference | 0 | 0 | 10 | 10 | 
 | :x: | K2IntentionTestGenerated$ConvertLambdaToSingleLine | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$ConvertLateinitPropertyToNullable | 0 | 0 | 4 | 4 | 
 | :x: | K2IntentionTestGenerated$ConvertLazyPropertyToOrdinary | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$ConvertLineCommentToBlockComment | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ConvertNullablePropertyToLateinit | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ConvertObjectLiteralToClass | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertOrdinaryPropertyToLazy | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ConvertParameterToReceiver | 0 | 0 | 22 | 22 | 
 | :x: | K2IntentionTestGenerated$ConvertPrimaryConstructorToSecondary | 0 | 0 | 40 | 40 | 
 | :x: | K2IntentionTestGenerated$ConvertPropertyInitializerToGetter | 0 | 0 | 16 | 16 | 
 | :x: | K2IntentionTestGenerated$ConvertPropertyToFunction | 0 | 0 | 23 | 23 | 
 | :x: | K2IntentionTestGenerated$ConvertRangeCheckToTwoComparisons | 0 | 0 | 12 | 12 | 
 | :x: | K2IntentionTestGenerated$ConvertReceiverToParameter | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ConvertReferenceToLambda | 0 | 0 | 47 | 47 | 
 | :x: | K2IntentionTestGenerated$ConvertSealedClassToEnum | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$ConvertSnakeCaseTestFunctionToSpaced | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertToIndexedFunctionCall | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ConvertToScope | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$ConvertTrimIndentToTrimMargin | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$ConvertTrimMarginToTrimIndent | 0 | 0 | 10 | 10 | 
 | :x: | K2IntentionTestGenerated$ConvertUnsafeCastCallToUnsafeCast | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertUnsafeCastToUnsafeCastCall | 0 | 0 | 2 | 2 | 
 | :x: | K2IntentionTestGenerated$ConvertVarargParameterToArray | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ConvertVariableAssignmentToExpression | 0 | 0 | 4 | 4 | 
 | :x: | K2IntentionTestGenerated$CopyConcatenatedStringToClipboard | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$Declarations | 0 | 0 | 15 | 15 | 
 | :x: | K2IntentionTestGenerated$DestructuringInLambda | 0 | 0 | 26 | 26 | 
 | :x: | K2IntentionTestGenerated$DestructuringVariables | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$EvaluateCompileTimeExpression | 0 | 0 | 16 | 16 | 
 | :x: | K2IntentionTestGenerated$ExpandBooleanExpression | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$ImplementAbstractMember | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$ImplementAsConstructorParameter | 0 | 0 | 11 | 11 | 
 | :x: | K2IntentionTestGenerated$IndentRawString | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$InfixCallToOrdinary | 0 | 0 | 6 | 6 | 
 | :x: | K2IntentionTestGenerated$InlayHints | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$InsertCurlyBracesToTemplate | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$IntroduceBackingProperty | 0 | 0 | 15 | 15 | 
 | :x: | K2IntentionTestGenerated$IntroduceImportAlias | 0 | 0 | 30 | 30 | 
 | :x: | K2IntentionTestGenerated$IntroduceVariable | 0 | 0 | 14 | 14 | 
 | :x: | K2IntentionTestGenerated$IterateExpression | 0 | 0 | 13 | 13 | 
 | :x: | K2IntentionTestGenerated$IterationOverMap | 0 | 0 | 40 | 40 | 
 | :x: | K2IntentionTestGenerated$JoinDeclarationAndAssignment | 0 | 0 | 48 | 48 | 
 | :x: | K2IntentionTestGenerated$LambdaToAnonymousFunction | 0 | 0 | 30 | 30 | 
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
 | :x: | K2IntentionTestGenerated$RemoveLabeledReturnInLambda | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$RemoveSingleExpressionStringTemplate | 0 | 0 | 7 | 7 | 
 | :x: | K2IntentionTestGenerated$ReplaceAddWithPlusAssign | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ReplaceExplicitFunctionLiteralParamWithIt | 0 | 0 | 24 | 24 | 
 | :x: | K2IntentionTestGenerated$ReplaceItWithExplicitFunctionLiteralParam | 0 | 0 | 5 | 5 | 
 | :x: | K2IntentionTestGenerated$ReplaceMapGetOrDefault | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ReplaceSizeCheckWithIsNotEmpty | 0 | 0 | 21 | 21 | 
 | :x: | K2IntentionTestGenerated$ReplaceSizeZeroCheckWithIsEmpty | 0 | 0 | 17 | 17 | 
 | :x: | K2IntentionTestGenerated$ReplaceTypeArgumentWithUnderscore | 0 | 0 | 31 | 31 | 
 | :x: | K2IntentionTestGenerated$ReplaceUnderscoreWithParameterName | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$ReplaceUntilWithRangeTo | 0 | 0 | 1 | 1 | 
 | :x: | K2IntentionTestGenerated$ReplaceWithOrdinaryAssignment | 0 | 0 | 9 | 9 | 
 | :x: | K2IntentionTestGenerated$SamConversionToAnonymousObject | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$SimplifyBooleanWithConstants | 0 | 0 | 39 | 39 | 
 | :x: | K2IntentionTestGenerated$SpecifyExplicitLambdaSignature | 0 | 0 | 19 | 19 | 
 | :x: | K2IntentionTestGenerated$SwapStringEqualsIgnoreCase | 0 | 0 | 3 | 3 | 
 | :x: | K2IntentionTestGenerated$ToInfixCall | 0 | 0 | 20 | 20 | 
 | :x: | K2IntentionTestGenerated$ToOrdinaryStringLiteral | 0 | 0 | 28 | 28 | 
 | :x: | K2IntentionTestGenerated$UsePropertyAccessSyntax | 0 | 0 | 57 | 57 | 
 | :x: | K2IntentionTestGenerated$UseWithIndex | 0 | 0 | 8 | 8 | 
 | :x: | K2IntentionTestGenerated$ValToObject | 0 | 0 | 8 | 8 | 
 | :white_check_mark: | K2IntentionTestGenerated$ImportMember | 95 | 21 | 22 | 22 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToBlockBody | 98 | 40 | 41 | 41 | 
 | :white_check_mark: | K2IntentionTestGenerated$SpecifyTypeExplicitly | 98 | 44 | 45 | 45 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddFullQualifier | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddMissingClassKeyword | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNameToArgument | 100 | 30 | 30 | 30 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesInCommentToJavaCallArguments | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesToCallArguments | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddNamesToFollowingArguments | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddOpenModifier | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddPropertyAccessors | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | K2IntentionTestGenerated$AddWhenRemainingBranches | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2IntentionTestGenerated$Chop | 100 | 10 | 10 | 10 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertBinaryExpressionWithDemorgansLaw | 100 | 26 | 26 | 26 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertConcatenationToBuildString | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertForEachToForLoop | 100 | 22 | 22 | 22 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertPropertyGetterToInitializer | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertStringTemplateToBuildString | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToConcatenatedString | 100 | 37 | 37 | 37 | 
 | :white_check_mark: | K2IntentionTestGenerated$ConvertToRawStringTemplate | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | K2IntentionTestGenerated$ImportAllMembers | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | K2IntentionTestGenerated$InvertIfCondition | 100 | 58 | 58 | 58 | 
 | :white_check_mark: | K2IntentionTestGenerated$JoinArgumentList | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | K2IntentionTestGenerated$JoinParameterList | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | K2IntentionTestGenerated$MergeIfs | 100 | 11 | 11 | 11 | 
 | :white_check_mark: | K2IntentionTestGenerated$MovePropertyToConstructor | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveAllArgumentNames | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveSingleArgumentName | 100 | 17 | 17 | 17 | 
 | :white_check_mark: | K2IntentionTestGenerated$ReplaceUnderscoreWithTypeArgument | 100 | 27 | 27 | 27 | 
 | :white_check_mark: | K2IntentionTestGenerated$ToRawStringLiteral | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | K2IntentionTestGenerated$TrailingComma | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | K2IntentionTestGenerated$InsertExplicitTypeArguments | 103 | 31 | 30 | 31 | 
 | :white_check_mark: | K2IntentionTestGenerated$RemoveExplicitType | 115 | 62 | 54 | 66 | 
 | :x: | FindUsagesWithDisableComponentSearchFirTestGenerated | 5 | 1 | 20 | 20 | 
 | :x: | K2SmartStepIntoTestGenerated | 5 | 2 | 42 | 42 | 
 | :x: | [HighLevelWeigherTestGenerated] | 69 | 72 | 105 | 106 | 
 | :x: | HighLevelWeigherTestGenerated$ExpectedInfo | 13 | 2 | 15 | 15 | 
 | :x: | HighLevelWeigherTestGenerated$ContextualReturn | 65 | 11 | 17 | 17 | 
 | :x: | HighLevelWeigherTestGenerated$Uncategorized | 78 | 45 | 58 | 59 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ExpectedType | 86 | 6 | 7 | 7 | 
 | :white_check_mark: | HighLevelWeigherTestGenerated$ParameterNameAndType | 100 | 8 | 8 | 8 | 
 | :x: | [K2IntroduceVariableTestGenerated] | 63 | 84 | 134 | 135 | 
 | :x: | K2IntroduceVariableTestGenerated$StringTemplates | 18 | 3 | 17 | 17 | 
 | :x: | K2IntroduceVariableTestGenerated$Script | 33 | 1 | 3 | 3 | 
 | :x: | K2IntroduceVariableTestGenerated$ExtractToScope | 64 | 7 | 11 | 11 | 
 | :x: | K2IntroduceVariableTestGenerated$Uncategorized | 68 | 60 | 88 | 89 | 
 | :x: | K2IntroduceVariableTestGenerated$MultiDeclarations | 78 | 7 | 9 | 9 | 
 | :white_check_mark: | K2IntroduceVariableTestGenerated$ExplicateTypeArguments | 100 | 6 | 6 | 6 | 
 | :x: | K2MoveTestGenerated | 34 | 56 | 163 | 163 | 
 | :x: | [FirJvmOptimizeImportsTestGenerated] | 71 | 84 | 119 | 119 | 
 | :x: | FirJvmOptimizeImportsTestGenerated$Jvm | 43 | 20 | 47 | 47 | 
 | :white_check_mark: | FirJvmOptimizeImportsTestGenerated$Common | 89 | 64 | 72 | 72 | 
 | :x: | K2AddImportActionTestGenerated | 57 | 20 | 35 | 35 | 
 | :x: | K2AutoImportTestGenerated | 64 | 18 | 28 | 28 | 
 | :x: | HighLevelQuickFixMultiFileTestGenerated$Uncategorized | 65 | 87 | 134 | 135 | 
 | :x: | SharedK2MultiFileQuickFixTestGenerated | 67 | 2 | 3 | 3 | 
 | :x: | HighLevelMultiFileJvmBasicCompletionTestGenerated | 70 | 64 | 91 | 95 | 
 | :white_check_mark: | [K2JvmBasicCompletionTestGenerated] | 88 | 600 | 684 | 718 | 
 | :x: | K2JvmBasicCompletionTestGenerated$Java | 74 | 37 | 50 | 54 | 
 | :white_check_mark: | K2JvmBasicCompletionTestGenerated$Common | 89 | 563 | 634 | 664 | 
 | :x: | HighLevelBasicCompletionHandlerTestGenerated$Basic | 76 | 214 | 283 | 289 | 
 | :x: | K2CompletionCharFilterTestGenerated | 80 | 28 | 35 | 35 | 
 | :white_check_mark: | [K2MultiFileLocalInspectionTestGenerated] | 92 | 11 | 12 | 12 | 
 | :x: | K2MultiFileLocalInspectionTestGenerated$RedundantQualifierName | 80 | 4 | 5 | 5 | 
 | :white_check_mark: | K2MultiFileLocalInspectionTestGenerated$UnusedSymbol | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | FirWithLibBasicCompletionTestGenerated | 88 | 15 | 17 | 17 | 
 | :white_check_mark: | FirShortenRefsTestGenerated$This | 88 | 7 | 8 | 8 | 
 | :white_check_mark: | FirKeywordCompletionTestGenerated$Keywords | 91 | 127 | 139 | 139 | 
 | :white_check_mark: | [K2IdeK2CodeKotlinEvaluateExpressionTestGenerated] | 92 | 312 | 340 | 340 | 
 | :white_check_mark: | K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$MultipleBreakpoints | 92 | 33 | 36 | 36 | 
 | :white_check_mark: | K2IdeK2CodeKotlinEvaluateExpressionTestGenerated$SingleBreakpoint | 92 | 279 | 304 | 304 | 
 | :white_check_mark: | K2KDocCompletionTestGenerated | 93 | 28 | 30 | 30 | 
 | :white_check_mark: | [K2SelectExpressionForDebuggerTestGenerated] | 99 | 68 | 69 | 69 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$DisallowMethodCalls | 95 | 20 | 21 | 21 | 
 | :white_check_mark: | K2SelectExpressionForDebuggerTestGenerated$Uncategorized | 100 | 48 | 48 | 48 | 
 | :white_check_mark: | FirUastDeclarationTestGenerated | 97 | 28 | 29 | 29 | 
 | :white_check_mark: | KotlinFirInlineTestGenerated$InlineVariableOrProperty | 97 | 32 | 33 | 33 | 
 | :white_check_mark: | FirRenameTestGenerated | 98 | 273 | 278 | 278 | 
 | :white_check_mark: | [FirLegacyUastValuesTestGenerated] | 100 | 79 | 79 | 79 | 
 | :white_check_mark: | [FirUastTypesTestGenerated] | 100 | 14 | 14 | 14 | 
 | :white_check_mark: | [FirUastValuesTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [K2BytecodeToolWindowTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [FirKeywordCompletionHandlerTestGenerated] | 100 | 49 | 49 | 49 | 
 | :white_check_mark: | [HighLevelJavaCompletionHandlerTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2ExternalAnnotationTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [FindUsagesFirTestGenerated] | 93 | 264 | 284 | 284 | 
 | :white_check_mark: | [KotlinFindUsagesWithLibraryFirTestGenerated] | 100 | 22 | 22 | 22 | 
 | :white_check_mark: | [KotlinFindUsagesWithStdlibFirTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFeaturesFirTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [KotlinGroupUsagesBySimilarityFirTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinScriptFindUsagesFirTestGenerated] | 100 | 5 | 5 | 5 | 
 | :white_check_mark: | [FirGotoDeclarationTestGenerated] | 100 | 13 | 13 | 13 | 
 | :white_check_mark: | [FirGotoTypeDeclarationTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [FirReferenceResolveInJavaTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [FirReferenceResolveTestGenerated] | 101 | 154 | 152 | 154 | 
 | :white_check_mark: | [FirReferenceToCompiledKotlinResolveInJavaTestGenerated] | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | [ReferenceResolveInLibrarySourcesFirTestGenerated] | 100 | 4 | 4 | 4 | 
 | :white_check_mark: | [K2FilteringAutoImportTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [KotlinFirBreadcrumbsTestGenerated] | 100 | 15 | 15 | 15 | 
 | :white_check_mark: | [K2SharedQuickFixTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [SharedK2InspectionTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [SharedK2LocalInspectionTestGenerated] | 100 | 255 | 255 | 255 | 
 | :white_check_mark: | [SharedK2KDocHighlightingTestGenerated] | 100 | 2 | 2 | 2 | 
 | :white_check_mark: | [SharedK2IntentionTestGenerated] | 100 | 331 | 331 | 331 | 
 | :white_check_mark: | [LineMarkersK2TestGenerated] | 100 | 46 | 46 | 46 | 
 | :white_check_mark: | [FirUpdateKotlinCopyrightTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2BreakpointApplicabilityTestGenerated] | 100 | 7 | 7 | 7 | 
 | :white_check_mark: | [K2ClassNameCalculatorTestGenerated] | 100 | 8 | 8 | 8 | 
 | :white_check_mark: | [K2IdeK2CodeAsyncStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeContinuationStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeCoroutineDumpTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2IdeK2CodeFileRankingTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [K2IdeK2CodeKotlinVariablePrintingTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [K2IdeK2CodeXCoroutinesStackTraceTestGenerated] | 100 | 3 | 3 | 3 | 
 | :white_check_mark: | [K2KotlinExceptionFilterTestGenerated] | 100 | 23 | 23 | 23 | 
 | :white_check_mark: | [K2PositionManagerTestGenerated] | 100 | 20 | 20 | 20 | 
 | :white_check_mark: | [Fe10BindingIntentionTestGenerated] | 101 | 132 | 131 | 132 | 
 | :white_check_mark: | [Fe10BindingLocalInspectionTestGenerated] | 100 | 214 | 214 | 214 | 
 | :white_check_mark: | [K2HighlightExitPointsTestGenerated] | 100 | 51 | 51 | 51 | 
 | :white_check_mark: | [K2HighlightUsagesTestGenerated] | 100 | 1 | 1 | 1 | 
 | :white_check_mark: | [K2HighlightingMetaInfoTestGenerated] | 104 | 51 | 49 | 51 | 
 | :white_check_mark: | [K2InspectionTestGenerated] | 88 | 14 | 16 | 16 | 
 | :white_check_mark: | [K2GotoTestOrCodeActionTestGenerated] | 100 | 9 | 9 | 9 | 
 | :white_check_mark: | [FirMoveLeftRightTestGenerated] | 100 | 16 | 16 | 16 | 
 | :white_check_mark: | [KotlinFirMoveStatementTestGenerated] | 99 | 277 | 279 | 279 | 
 | :white_check_mark: | [K2MultiFileQuickFixTestGenerated] | 100 | 6 | 6 | 6 | 
 | :white_check_mark: | [K2SafeDeleteTestGenerated] | 100 | 198 | 198 | 198 | 
 | :white_check_mark: | [FirAnnotatedMembersSearchTestGenerated] | 100 | 12 | 12 | 12 | 
 | :white_check_mark: | [KotlinFirFileStructureTestGenerated] | 100 | 19 | 19 | 19 | 
 | :white_check_mark: | [KotlinFirSurroundWithTestGenerated] | 100 | 74 | 74 | 74 | 
 | :white_check_mark: | [KotlinFirUnwrapRemoveTestGenerated] | 100 | 63 | 63 | 63 | 
 | :white_check_mark: | [K2ProjectViewTestGenerated] | 100 | 31 | 31 | 31 | 
 | :white_check_mark: | [ParcelizeK2QuickFixTestGenerated] | 100 | 18 | 18 | 18 | 
 | :white_check_mark: | K2UnusedSymbolHighlightingTestGenerated$Uncategorized | 105 | 122 | 116 | 125 | 
 | :white_check_mark: | K2InplaceRenameTestGenerated | 121 | 121 | 100 | 126 | 

### Extensions

kt, test, before.Main.kt, kts, main.java, main.kt, option1.kt, kt.kt, java, 0.kt, 0.java, 0.properties, 0.kts, gradle.kts

---
## Total 
 * K1: 9770 rate: 99 % of 9875 files
 * K2: 6530 rate: 66 % of 9875 files
---

## Build cases for K1

org.jetbrains.kotlin.idea.debugger.test.PositionManagerTestGenerated$MultiFile has directories
 * jvm-debugger/test/testData/positionManager/multiFilePackage
 * jvm-debugger/test/testData/positionManager/multiFileSameName

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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AddDependency has directories
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$AddThrowAnnotation has directories
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$ChangeModifier has directories
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$CreateActualExplicitApi has directories
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$FixNativeThrowsErrors has directories
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$InitializeProperty has directories
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$InlineToValue has directories
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$MakeOverridenMemberOpen has directories
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

org.jetbrains.kotlin.idea.quickfix.QuickFixMultiModuleTestGenerated$PackageDirectoryMismatch has directories
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

org.jetbrains.kotlin.idea.script.ScriptConfigurationHighlightingTestGenerated$Highlighting has directories
 * idea/tests/testData/script/definition/highlighting/acceptedAnnotations
 * idea/tests/testData/script/definition/highlighting/additionalImports
 * idea/tests/testData/script/definition/highlighting/asyncResolver
 * idea/tests/testData/script/definition/highlighting/conflictingModule
 * idea/tests/testData/script/definition/highlighting/customBaseClass
 * idea/tests/testData/script/definition/highlighting/customExtension
 * idea/tests/testData/script/definition/highlighting/customJavaHome
 * idea/tests/testData/script/definition/highlighting/customLibrary
 * idea/tests/testData/script/definition/highlighting/customLibraryInModuleDeps
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

org.jetbrains.kotlin.tools.projectWizard.cli.YamlBuildFileGenerationTestGenerated has directories
 * project-wizard/tests/testData/buildFileGeneration/android
 * project-wizard/tests/testData/buildFileGeneration/jsNodeAndBrowserTargets
 * project-wizard/tests/testData/buildFileGeneration/jvmTarget
 * project-wizard/tests/testData/buildFileGeneration/jvmTargetWithJava
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependency
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependencyWithSingleRoot
 * project-wizard/tests/testData/buildFileGeneration/kotlinJvm
 * project-wizard/tests/testData/buildFileGeneration/nativeForCurrentSystem
 * project-wizard/tests/testData/buildFileGeneration/simpleMultiplatform
 * project-wizard/tests/testData/buildFileGeneration/simpleNativeTarget
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsBrowser
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsNode
 * project-wizard/tests/testData/buildFileGeneration/wasmTarget

org.jetbrains.kotlin.tools.projectWizard.cli.ProjectTemplateBuildFileGenerationTestGenerated has directories
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/consoleApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/frontendApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/fullStackWebApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/multiplatformLibrary
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/nativeApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/simpleWasmApplication

org.jetbrains.kotlin.tools.projectWizard.wizard.YamlNewWizardProjectImportTestGenerated$GradleKts has directories
 * project-wizard/tests/testData/buildFileGeneration/android
 * project-wizard/tests/testData/buildFileGeneration/jsNodeAndBrowserTargets
 * project-wizard/tests/testData/buildFileGeneration/jvmTarget
 * project-wizard/tests/testData/buildFileGeneration/jvmTargetWithJava
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependency
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependencyWithSingleRoot
 * project-wizard/tests/testData/buildFileGeneration/kotlinJvm
 * project-wizard/tests/testData/buildFileGeneration/nativeForCurrentSystem
 * project-wizard/tests/testData/buildFileGeneration/simpleMultiplatform
 * project-wizard/tests/testData/buildFileGeneration/simpleNativeTarget
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsBrowser
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsNode
 * project-wizard/tests/testData/buildFileGeneration/wasmTarget

org.jetbrains.kotlin.tools.projectWizard.wizard.YamlNewWizardProjectImportTestGenerated$GradleGroovy has directories
 * project-wizard/tests/testData/buildFileGeneration/android
 * project-wizard/tests/testData/buildFileGeneration/jsNodeAndBrowserTargets
 * project-wizard/tests/testData/buildFileGeneration/jvmTarget
 * project-wizard/tests/testData/buildFileGeneration/jvmTargetWithJava
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependency
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependencyWithSingleRoot
 * project-wizard/tests/testData/buildFileGeneration/kotlinJvm
 * project-wizard/tests/testData/buildFileGeneration/nativeForCurrentSystem
 * project-wizard/tests/testData/buildFileGeneration/simpleMultiplatform
 * project-wizard/tests/testData/buildFileGeneration/simpleNativeTarget
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsBrowser
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsNode
 * project-wizard/tests/testData/buildFileGeneration/wasmTarget

org.jetbrains.kotlin.tools.projectWizard.wizard.YamlNewWizardProjectImportTestGenerated$Maven has directories
 * project-wizard/tests/testData/buildFileGeneration/android
 * project-wizard/tests/testData/buildFileGeneration/jsNodeAndBrowserTargets
 * project-wizard/tests/testData/buildFileGeneration/jvmTarget
 * project-wizard/tests/testData/buildFileGeneration/jvmTargetWithJava
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependency
 * project-wizard/tests/testData/buildFileGeneration/jvmToJvmDependencyWithSingleRoot
 * project-wizard/tests/testData/buildFileGeneration/kotlinJvm
 * project-wizard/tests/testData/buildFileGeneration/nativeForCurrentSystem
 * project-wizard/tests/testData/buildFileGeneration/simpleMultiplatform
 * project-wizard/tests/testData/buildFileGeneration/simpleNativeTarget
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsBrowser
 * project-wizard/tests/testData/buildFileGeneration/singlePlatformJsNode
 * project-wizard/tests/testData/buildFileGeneration/wasmTarget

org.jetbrains.kotlin.tools.projectWizard.wizard.ProjectTemplateNewWizardProjectImportTestGenerated$GradleKts has directories
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/consoleApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/frontendApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/fullStackWebApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/multiplatformLibrary
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/nativeApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/simpleWasmApplication

org.jetbrains.kotlin.tools.projectWizard.wizard.ProjectTemplateNewWizardProjectImportTestGenerated$GradleGroovy has directories
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/consoleApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/frontendApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/fullStackWebApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/multiplatformLibrary
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/nativeApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/simpleWasmApplication

org.jetbrains.kotlin.tools.projectWizard.wizard.ProjectTemplateNewWizardProjectImportTestGenerated$Maven has directories
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/consoleApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/frontendApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/fullStackWebApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/multiplatformLibrary
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/nativeApplication
 * project-wizard/tests/testData/projectTemplatesBuildFileGeneration/simpleWasmApplication

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$ClassOrObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Functions has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceTestGenerated$Properties has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceTestGenerated$Library has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode
## Build cases for K2

org.jetbrains.kotlin.idea.k2.debugger.test.cases.K2PositionManagerTestGenerated$MultiFile has directories
 * jvm-debugger/test/testData/positionManager/multiFilePackage
 * jvm-debugger/test/testData/positionManager/multiFileSameName

org.jetbrains.kotlin.idea.k2.highlighting.OutsiderHighlightingTestGenerated has directories
 * highlighting/highlighting-k2/testData/outsider/badDiff
 * highlighting/highlighting-k2/testData/outsider/badSource
 * highlighting/highlighting-k2/testData/outsider/badSourceDiff
 * highlighting/highlighting-k2/testData/outsider/circularDependency

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$ClassOrObject has directories
 * compiler-reference-index/tests/testData/compilerIndex/classOrObject/object

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Functions has directories
 * compiler-reference-index/tests/testData/compilerIndex/functions/topLevel/functionWithJvmOverloads

org.jetbrains.kotlin.idea.fir.search.refIndex.KotlinCompilerReferenceFirTestGenerated$Properties has directories
 * compiler-reference-index/tests/testData/compilerIndex/properties/topLevel/variableWithJvmNameOnProperty

org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceByReferenceFirTestGenerated$Library has directories
 * compiler-reference-index/tests/testData/compilerIndexByReference/library/object/hashCode

org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.LocalSessionInvalidationTestGenerated has directories
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTree
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeNoInvalidated
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeWithAdditionalEdge
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeWithInvalidInRoot
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclical
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linear
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linearWithCyclicalDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/rhombus
 * base/fir/analysis-api-providers/testData/sessionInvalidation/rhombusWithTwoInvalid

org.jetbrains.kotlin.idea.fir.analysis.providers.sessions.GlobalSessionInvalidationTestGenerated has directories
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTree
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeNoInvalidated
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeWithAdditionalEdge
 * base/fir/analysis-api-providers/testData/sessionInvalidation/binaryTreeWithInvalidInRoot
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclical
 * base/fir/analysis-api-providers/testData/sessionInvalidation/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linear
 * base/fir/analysis-api-providers/testData/sessionInvalidation/linearWithCyclicalDependency
 * base/fir/analysis-api-providers/testData/sessionInvalidation/rhombus
 * base/fir/analysis-api-providers/testData/sessionInvalidation/rhombusWithTwoInvalid

org.jetbrains.kotlin.idea.fir.analysis.providers.dependents.ModuleDependentsTestGenerated has directories
 * base/fir/analysis-api-providers/testData/moduleDependents/binaryTree
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclical
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclicalSelf
 * base/fir/analysis-api-providers/testData/moduleDependents/cyclicalWithOutsideDependency
 * base/fir/analysis-api-providers/testData/moduleDependents/deduplicatedLibraries
 * base/fir/analysis-api-providers/testData/moduleDependents/linear
 * base/fir/analysis-api-providers/testData/moduleDependents/rhombus
 * base/fir/analysis-api-providers/testData/moduleDependents/singleRoot

org.jetbrains.kotlin.idea.fir.resolve.FirReferenceResolveWithLibTestGenerated has directories
 * idea/tests/testData/resolve/referenceWithLib/dataClassSyntheticMethods
 * idea/tests/testData/resolve/referenceWithLib/delegatedPropertyWithTypeParameters
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$AccessibilityChecker has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$ActualAnnotationsNotMatchExpect has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$AddDependency has directories
 * idea/tests/testData/multiModuleQuickFix/addDependency/class
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelFunction2
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty
 * idea/tests/testData/multiModuleQuickFix/addDependency/topLevelProperty2

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$AddMissingActualMembers has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$AddThrowAnnotation has directories
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/common
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/js
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvm
 * idea/tests/testData/multiModuleQuickFix/addThrowAnnotation/jvmWithoutStdlib

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$CanSealedSubClassBeObject has directories
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertActualSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notConvertImplicitExpectSubClass
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInCommon
 * idea/tests/testData/multiModuleQuickFix/canSealedSubClassBeObject/notGenerateEqualsAndHashCodeForSealedInJvmForExpect

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$ChangeModifier has directories
 * idea/tests/testData/multiModuleQuickFix/changeModifier/internal
 * idea/tests/testData/multiModuleQuickFix/changeModifier/public

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$ChangeSignature has directories
 * idea/tests/testData/multiModuleQuickFix/changeSignature/actual
 * idea/tests/testData/multiModuleQuickFix/changeSignature/expect
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override2
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override3
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override4
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override5
 * idea/tests/testData/multiModuleQuickFix/changeSignature/override6

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$CreateActual has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$CreateActualExplicitApi has directories
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/class
 * idea/tests/testData/multiModuleQuickFix/createActualExplicitApi/function

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$CreateExpect has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$FixNativeThrowsErrors has directories
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException1
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException2
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException3
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/addCancellationException4
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeEmptyThrows
 * idea/tests/testData/multiModuleQuickFix/fixNativeThrowsErrors/removeThrowsOnIncompatibleOverride

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$FunctionTypeReceiverToParameter has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$InitializeProperty has directories
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeNonActualParameterWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notInitializeWithConstructorParameter
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveNonActualParamterToActualConstructor
 * idea/tests/testData/multiModuleQuickFix/initializeProperty/notMoveToActualConstructor

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$InlineToValue has directories
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/commonWithJvm
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/genericParameter
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JS
 * idea/tests/testData/multiModuleQuickFix/inlineToValue/JVM

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$MakeOverridenMemberOpen has directories
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/actual
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/expect
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasAbstract
 * idea/tests/testData/multiModuleQuickFix/makeOverridenMemberOpen/hasOpen

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$Other has directories
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

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$PackageDirectoryMismatch has directories
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToAnotherPackage
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToCommonSourceRoot
 * idea/tests/testData/multiModuleQuickFix/packageDirectoryMismatch/moveFileToJvmSourceRoot

org.jetbrains.kotlin.idea.fir.quickfix.HighLevelQuickFixMultiModuleTestGenerated$RedundantNullableReturnType has directories
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualMethod
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/actualTopLevelProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectMemberProperty
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelFunction
 * idea/tests/testData/multiModuleQuickFix/redundantNullableReturnType/expectTopLevelProperty

org.jetbrains.kotlin.idea.fir.completion.K2MultiPlatformCompletionTestGenerated has directories
 * completion/testData/multiPlatform/classInCommon
 * completion/testData/multiPlatform/classInCommonNonImported
 * completion/testData/multiPlatform/classInPlatform
 * completion/testData/multiPlatform/functionInCommon
 * completion/testData/multiPlatform/functionInPlatform

org.jetbrains.kotlin.idea.fir.codeInsight.K2MultiModuleLineMarkerTestGenerated has directories
 * code-insight/testData/linemarkers/multiplatform

org.jetbrains.kotlin.idea.fir.resolve.K2MultiModuleHighlightingTestGenerated has directories
 * fir/testData/resolve/anchors/anchorInDependentModule
 * fir/testData/resolve/anchors/anchorInSameModule
 * fir/testData/resolve/anchors/anchorInSameModuleJavaDependency
## K1 only cases

565 K1 only cases (7711 files):

 * KotlinReceiverUsageHighlightingTestGenerated
 * org.jetbrains.kotlin.DataFlowValueRenderingTestGenerated
 * org.jetbrains.kotlin.addImport.AddImportTestGenerated
 * org.jetbrains.kotlin.addImportAlias.AddImportAliasTest53Generated
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Delegation
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Facades
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$NullabilityAnnotations
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$Uncategorized
 * org.jetbrains.kotlin.asJava.classes.IdeCompiledLightClassesByFqNameTestGenerated$WithTestCompilerPluginEnabled
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
 * org.jetbrains.kotlin.idea.codeInsight.hints.KotlinArgumentsHintsProviderTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.hints.KotlinLambdasHintsProviderGenerated
 * org.jetbrains.kotlin.idea.codeInsight.hints.KotlinRangesHintsProviderTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.hints.KotlinReferenceTypeHintsProviderTestGenerated
 * org.jetbrains.kotlin.idea.codeInsight.postfix.PostfixTemplateProviderTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.codeInsight.postfix.PostfixTemplateProviderTestGenerated$WrapWithCall
 * org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.SerializationPluginIdeDiagnosticTestGenerated
 * org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.SerializationQuickFixTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.CompletionIncrementalResolveTest31Generated
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
 * org.jetbrains.kotlin.idea.completion.test.K1JSBasicCompletionTestGenerated$Js
 * org.jetbrains.kotlin.idea.completion.test.KotlinSourceInJavaCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.KotlinStdLibInJavaCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.MultiFileSmartCompletionTestGenerated
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$Lambda
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$LambdaSignature
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$SuspendLambdaSignature
 * org.jetbrains.kotlin.idea.completion.test.handlers.SmartCompletionHandlerTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.completion.test.weighers.SmartCompletionWeigherTestGenerated
 * org.jetbrains.kotlin.idea.conversion.copy.LiteralKotlinToKotlinCopyPasteTestGenerated
 * org.jetbrains.kotlin.idea.conversion.copy.LiteralTextToKotlinCopyPasteTestGenerated
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
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Annotations
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Class
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ClassFun
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ClassObject
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Constructor
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$ContextReceivers
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Coroutines
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$DataClass
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$FromLoadJava
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Fun
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Inline
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$MemberOrder
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$NestedClasses
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$PlatformTypes
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Prop
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Type
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Typealias
 * org.jetbrains.kotlin.idea.decompiler.stubBuilder.LoadJavaClsStubTestGenerated$Visibility
 * org.jetbrains.kotlin.idea.decompiler.textBuilder.CommonDecompiledTextTestGenerated
 * org.jetbrains.kotlin.idea.decompiler.textBuilder.JvmDecompiledTextTestGenerated
 * org.jetbrains.kotlin.idea.editor.backspaceHandler.BackspaceHandlerTestGenerated$StringTemplate
 * org.jetbrains.kotlin.idea.editor.backspaceHandler.BackspaceHandlerTestGenerated$Uncategorized
 * org.jetbrains.kotlin.idea.editor.commenter.KotlinCommenterTestGenerated
 * org.jetbrains.kotlin.idea.editor.quickDoc.QuickDocProviderTestGenerated
 * org.jetbrains.kotlin.idea.folding.KotlinFoldingTestGenerated$CheckCollapse
 * org.jetbrains.kotlin.idea.folding.KotlinFoldingTestGenerated$NoCollapse
 * org.jetbrains.kotlin.idea.highlighter.DiagnosticMessageJsTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.DiagnosticMessageTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.DslHighlighterTestGenerated
 * org.jetbrains.kotlin.idea.highlighter.UsageHighlightingTestGenerated
 * org.jetbrains.kotlin.idea.imports.JsOptimizeImportsTestGenerated$Js
 * org.jetbrains.kotlin.idea.imports.JvmOptimizeImportsTestGenerated$Jvm$Uncategorized
 * org.jetbrains.kotlin.idea.index.KotlinTypeAliasByExpansionShortNameIndexTestGenerated
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$Inspections
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$InspectionsLocal
 * org.jetbrains.kotlin.idea.inspections.InspectionTestGenerated$Intentions
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ArrayInDataClass
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CanBeVal
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$CatchIgnoresException
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Collections
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ComplexRedundantLet
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConstantConditionIf
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ControlFlowWithEmptyBody
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConventionNameCalls
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConvertObjectToDataObject
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ConvertTwoComparisonsToRangeCheck
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Coroutines
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$DeprecatedCallableAddReplaceWith
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$DoubleNegation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EmptyRange
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EnumValuesSoftDeprecate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EqualsBetweenInconvertibleTypes
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$EqualsOrHashCode
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ExplicitThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$FloatingPointLiteralPrecision
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$FunctionWithLambdaExpressionBody
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ImplicitNullableNothingType
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ImplicitThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$IncompleteDestructuringInspection
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$InconsistentCommentForJavaParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JavaCollectionsStaticMethod
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JavaMapForEach
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$JoinDeclarationAndAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LeakingThis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LiftOut
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$LocalVariableName
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$Logging
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MainFunctionReturnUnit
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MayBeConstant
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MigrateDiagnosticSuppression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MoveLambdaOutsideParentheses
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$MoveVariableDeclarationIntoWhen
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NestedLambdaShadowedImplicitParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonExhaustiveWhenStatementMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonExternalClassifierExtendingStateOrProps
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonNullableBooleanPropertyInExternalInterface
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NonVarPropertyInExternalInterface
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NullChecksToSafeCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$NullableBooleanElvis
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ObjectLiteralToLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$OverrideDeprecatedMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitJvmOverloadsOnConstructorsOfAnnotationClassesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitJvmOverloadsOnConstructorsOfAnnotationClassesMigration1_3
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitRepeatedUseSiteTargetAnnotationsMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitTypeParametersForLocalVariablesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitUseSiteTargetAnnotationsOnSuperTypesMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ProhibitUseSiteTargetAnnotationsOnSuperTypesMigration1_3
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RecursiveEqualsCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RecursivePropertyAccessor
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantCompanionReference
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantDiagnosticSuppress
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantElseInIf
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantElvisReturnNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantEnumConstructorInvocation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantIf
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantInnerClassModifier
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLabelMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLambdaArrow
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantLambdaOrAnonymousFunction
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantNullableReturnType
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantOverride
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantRequireNotNullCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantSamConstructor
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantUnitExpression
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantVisibilityModifier
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RedundantWith
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveEmptyParenthesesFromAnnotationEntry
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveExplicitTypeArguments
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveRedundantQualifierName
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveRedundantSpreadOperator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RemoveToStringInStringTemplate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceArrayOfWithLiteral
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceAssertBooleanWithAssertEquality
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceCollectionCountWithSize
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceGuardClauseWithFunctionCall
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceJavaStaticMethodWithKotlinAnalog
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceManualRangeWithIndicesCalls
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceMapIndexedWithListGenerator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceNegatedIsEmptyWithIsNotEmpty
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceNotNullAssertionWithElvisReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplacePutWithAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeStartEndInclusiveWithFirstLast
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeToWithRangeUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceRangeToWithUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceReadLineWithReadln
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceStringFormatWithLiteral
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceSubstring
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceToStringWithStringTemplate
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceToWithInfixForm
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceUntilWithRangeUntil
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithEnumMap
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithIgnoreCaseEquals
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithImportAlias
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithOperatorAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ReplaceWithStringBuilderAppendRange
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$RestrictReturnStatementTargetMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SafeCastWithReturn
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ScopeFunctions
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SelfAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SelfReferenceConstructorParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SetterBackingFieldAssignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimpleRedundantLet
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyAssertNotNull
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyNestedEachInScope
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SimplifyWhenWithBooleanConstantCondition
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousAsDynamic
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousCallableReferenceInLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousCollectionReassignment
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$SuspiciousVarProperty
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$ThrowableNotThrown
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnlabeledReturnInsideLambda
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnnecessaryOptInAnnotation
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnnecessaryVariable
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnsafeCastFromDynamic
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedDataClassCopyResult
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedEquals
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedLambdaExpressionBody
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedMainParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedReceiverParameter
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UnusedUnaryOperator
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UseExpressionBody
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$UsePropertyAccessSyntax
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$VerboseNullabilityAndEmptiness
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WarningOnMainUnusedParameterMigration
 * org.jetbrains.kotlin.idea.inspections.LocalInspectionTestGenerated$WhenWithOnlyElse
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
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$RemoveExplicitTypeArguments
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$RemoveExplicitTypeWithApiMode
 * org.jetbrains.kotlin.idea.intentions.K1IntentionTestGenerated$SpecifyTypeExplicitlyInDestructuringAssignment
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
 * org.jetbrains.kotlin.idea.navigation.KotlinGotoTestGenerated$GotoClass
 * org.jetbrains.kotlin.idea.navigation.KotlinGotoTestGenerated$GotoSymbol
 * org.jetbrains.kotlin.idea.navigationToolbar.KotlinNavBarTestGenerated
 * org.jetbrains.kotlin.idea.quickfix.K1QuickFixTestGenerated$ReplaceWithSafeCallForScopeFunction
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddAnnotationTarget
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddExclExclCall
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddGenericUpperBound
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$AddSpreadOperatorForArrayAsVarargAfterSam
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ChangeSignature
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ConvertJavaInterfaceToClass
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$CreateFromUsage
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$DeprecatedSymbolUsage
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$IncreaseVisibility
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MakePrivateAndOverrideMember
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MakeUpperBoundNonNullable
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MemberVisibilityCanBePrivate
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Migration
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Modifiers
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$MoveToSealedParent
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$Override
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$RemoveUnused
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ReplaceWithSafeCall
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$ReplaceWithSafeCallForScopeFunction
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$SpecifySuperExplicitly
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$SurroundWithNullCheck
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeImports
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$TypeMismatch
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$When
 * org.jetbrains.kotlin.idea.quickfix.QuickFixMultiFileTestGenerated$WrapWithSafeLetCall
 * org.jetbrains.kotlin.idea.refactoring.NameSuggestionProviderTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.copy.CopyTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.copy.MultiModuleCopyTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineMultiFileTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$AnonymousFunction
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$InlineTypeAlias
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$LambdaExpression
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestGenerated$NamedFunction
 * org.jetbrains.kotlin.idea.refactoring.inline.InlineTestWithSomeDescriptorsGenerated
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Basic
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$ControlFlow
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$DefaultContainer
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Delegation
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Duplicates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Initializers
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Multiline
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$OptIn
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Parameters
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$Script
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$StringTemplates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$ExtractFunction$TypeParameters
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
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceParameter$Multiline
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceParameter$Script
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceParameter$StringTemplates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceParameter$Uncategorized
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceParameter$VariableConversion
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceProperty$Script
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceProperty$StringTemplates
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceProperty$Uncategorized
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceTypeAlias
 * org.jetbrains.kotlin.idea.refactoring.introduce.ExtractionTestGenerated$IntroduceTypeParameter
 * org.jetbrains.kotlin.idea.refactoring.move.MultiModuleMoveTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$J2K
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$K2J
 * org.jetbrains.kotlin.idea.refactoring.pullUp.PullUpTestGenerated$K2K
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$J2K
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$K2J
 * org.jetbrains.kotlin.idea.refactoring.pushDown.PushDownTestGenerated$K2K
 * org.jetbrains.kotlin.idea.refactoring.rename.MultiModuleRenameTestGenerated
 * org.jetbrains.kotlin.idea.refactoring.safeDelete.MultiModuleSafeDeleteTestGenerated
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
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Annotations
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$AnonymousBlock
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$AnonymousClass
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ArrayAccessExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ArrayInitializerExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ArrayType
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$AssertStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$AssignmentExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$BinaryExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Blocks
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$BoxedType
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$BreakStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$CallChainExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$CaseConversion
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Class
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ClassExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Collections
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Comments
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ConditionalExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Constructors
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ContinueStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$DeclarationStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$DetectProperties
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$DoWhileStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$DocComments
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Enum
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Equals
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ExplicitApiMode
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Field
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$For
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ForeachStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Formatting
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Function
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$FunctionalInterfaces
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Identifier
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$IfStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ImplicitCasts
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ImportStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Inheritance
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Interface
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$InvalidCode
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$IsOperator
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Issues
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$JavaStandardMethods
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$JavaStreamsApi
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$KotlinApiAccess
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$LabelStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Lambda
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$LibraryUsage
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$List
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$LiteralExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$LocalVariable
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$MethodCallExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Misc
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$MutableCollections
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$NewClassExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$NewJavaFeatures
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Nullability
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ObjectLiteral
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Overloads
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$PackageStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ParenthesizedExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$PolyadicExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$PostProcessing
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$PostfixOperator
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$PrefixOperator
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Projections
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Protected
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$RawGenerics
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ReturnStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Settings
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$StaticMembers
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Strings
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$SuperExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Switch
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$SynchronizedStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ThisExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ThrowStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ToArray
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$ToKotlinClasses
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$TryStatement
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$TryWithResource
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$TypeCastExpression
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$TypeParameters
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Types
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$VarArg
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$Visibility
 * org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverterSingleFileTestGenerated$WhileStatement
 * org.jetbrains.kotlin.nj2k.PartialConverterTestGenerated$Field
 * org.jetbrains.kotlin.nj2k.PartialConverterTestGenerated$Function
 * org.jetbrains.kotlin.parcelize.ide.test.ParcelizeK1CheckerTestGenerated
 * org.jetbrains.kotlin.psi.patternMatching.PsiUnifierTestGenerated$Equivalence
 * org.jetbrains.kotlin.search.InheritorsSearchTestGenerated
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Constructor
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Imports
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Java
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Kt21515
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Type
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Typealias
 * org.jetbrains.kotlin.shortenRefs.ShortenRefsTestGenerated$Uncategorized
---
