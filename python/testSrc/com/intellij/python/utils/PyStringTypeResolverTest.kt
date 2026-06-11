package com.intellij.python.utils

import com.intellij.openapi.application.PathManager
import com.intellij.psi.PsiElement
import com.intellij.python.lsp.core.type.PyStringTypeResolver
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.impl.PyFileImpl
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyCollectionType
import com.jetbrains.python.psi.types.PyCollectionTypeImpl
import com.jetbrains.python.psi.types.PyFunctionType
import com.jetbrains.python.psi.types.PyLiteralType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyNumericTowerUtil
import com.jetbrains.python.psi.types.PyOverloadType
import com.jetbrains.python.psi.types.PySelfType
import com.jetbrains.python.psi.types.PyTupleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeVarType
import com.jetbrains.python.psi.types.PyTypedDictType
import com.jetbrains.python.psi.types.PyTypingNewType
import com.jetbrains.python.psi.types.PyUnionType
import com.jetbrains.python.psi.types.PyUnpackedTupleTypeImpl
import com.jetbrains.python.psi.types.TypeEvalContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class PyStringTypeResolverTest : PyTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.configureByText("test.py", """
      from typing import TypedDict, Protocol, Generic, Literal

      def f() -> None: ...
      
      class A:
          class B:
            def f(self) -> None: ...
          def f(self) -> None: ...
          @staticmethod
          def s() -> None: ...
          def g[T: int](t: T) -> T: ...
          
          class B:
            def f(self) -> None: ...
            
      class B(TypedDict):
          a: int
    """.trimIndent())
  }

  val builtins get() = PyBuiltinCache.getInstance(myFixture.file)

  private val typeEvalContext: TypeEvalContext
    get() = TypeEvalContext.externalContext(myFixture.project)

  @OptIn(ExperimentalContracts::class)
  inline fun <reified T> Any?.assertIs(): T & Any {
    contract { returns() implies (this@assertIs is T) }

    return assertInstanceOf(this, T::class.java)
  }

  private inline fun <reified T : PyType> parse(s: String, anchor: PsiElement = myFixture.file): T {
    val result = PyStringTypeResolver.resolvePyType(anchor as PyTypedElement, s.trimIndent())
    assertNotNull("the type completely failed to parse", result)
    return result!!.get().assertIs<T>()
  }

  fun `test parse unresolved`() {
    val ty = PyStringTypeResolver.resolvePyType(myFixture.file as PyFileImpl, "unresolved")
    assertNull("The type was resolved, which is unexpected", ty)
  }

  fun `test parse Any old`() {
    val result = PyStringTypeResolver.resolvePyType(myFixture.file as PyTypedElement, PyTypingTypeProvider.ANY)
    assertNotNull("the type completely failed to parse", result)
    assertNull("The type was not Any", result!!.get())
  }

  fun `test parse Any`() {
    withNewAnyTypeEnabled {
      parse<PyAnyType.Any>(PyTypingTypeProvider.ANY)
    }
  }

  fun `test parse Unknown`() {
    withNewAnyTypeEnabled {
      parse<PyAnyType.Unknown>(PyNames.UNKNOWN_TYPE)
    }
  }

  fun `test parse union with Any`() {
    parse<PyUnionType>("typing.Any | None")
  }

  fun `test parse simple`() {
    val mapping =
      parse<PyCollectionType>("collections.abc.Mapping[collections.abc.Sequence[builtins.int], collections.abc.Iterable[builtins.str]]")

    val params = mapping.elementTypes
    assertSize(2, params)

    val (keyParam, valParam) = params

    // Key is Sequence[int]
    keyParam.assertIs<PyCollectionType>()
    val keyIter = keyParam.iteratedItemType
    assertEquals(builtins.intType, keyIter)

    // Value is Iterable[str]
    valParam.assertIs<PyCollectionType>()
    val valIter = valParam.iteratedItemType!!
    assertEquals(builtins.strType, valIter)
  }

  fun `test parse tuple fixed`() {
    val tupleType = parse<PyTupleType>("builtins.tuple[builtins.int, builtins.str]")
    assertFalse(tupleType.isHomogeneous)

    val elems = tupleType.elementTypes

    assertSize(2, elems)
    val (first, second) = elems
    assertEquals(builtins.intType, first)
    assertEquals(builtins.strType, second)
  }

  fun `test parse int`() {
    val type = parse<PyClassType>("builtins.int")
    assertFalse(type.isDefinition)
    assertEquals(builtins.intType, type)
  }

  fun `test parse type int`() {
    val type = parse<PyClassType>("builtins.type[builtins.int]")
    assertTrue(type.isDefinition)
    assertEquals("int", type.classQName)
  }

  fun `test parse tuple variadic`() {
    val tupleType = parse<PyTupleType>("builtins.tuple[builtins.int, ...]")

    assertTrue(tupleType.isHomogeneous)
    assertEquals(builtins.intType, tupleType.elementTypes.single())
  }

  fun `test parse literal`() {
    val union = parse<PyUnionType>("typing.Literal[1, 2]")
    assertSize(2, union.members)

    val (left, right) = union.members.toList()
    left.assertIs<PyLiteralType>()
    assertEquals("1", left.expressionText)
    right.assertIs<PyLiteralType>()
    assertEquals("2", right.expressionText)
  }

  fun `test parse function type`() {
    val fnType = parse<PyFunctionType>("def test.f(x: builtins.int, y: builtins.str = 'abb') -> builtins.str")

    assertEquals("f", fnType.callable.name)

    val parameters = fnType.getParameters(typeEvalContext)!!
    assertSize(2, parameters)

    val parameter0 = parameters[0]
    assertEquals("x", parameter0.name)
    val parameter0Type = parameter0.getType(typeEvalContext).assertIs<PyClassType>()
    assertEquals("int", parameter0Type.name)
    assertFalse(parameter0.hasDefaultValue())

    val parameter1 = parameters[1]
    assertEquals("y", parameter1.name)
    val parameter1Type = parameter1.getType(typeEvalContext).assertIs<PyClassType>()
    assertEquals("str", parameter1Type.name)
    assertTrue(parameter1.hasDefaultValue())

    val returnType = fnType.getReturnType(typeEvalContext).assertIs<PyClassType>()
    assertEquals("str", returnType.name)
  }

  fun `test parse member callable`() {
    val fnType = parse<PyFunctionType>("def test.A.f() -> None")

    assertEquals("f", fnType.callable.name)
  }

  fun `test parse member callable staticmethod`() {
    val fnType = parse<PyFunctionType>("def test.A.s() -> None")

    assertEquals("s", fnType.callable.name)
  }

  fun `test parse function from nested class`() {
    val fnType = parse<PyFunctionType>("def test.A.B.f() -> None")

    assertEquals("f", fnType.callable.name)
  }

  fun `test parse function unresolved`() {
    parse<PyCallableType>("def unresolved() -> Unknown")
  }

  fun `test parse function with type parameter`() {
    val fnType = parse<PyFunctionType>("def test.A.g[T: builtins.int](x: T) -> T")

    assertEquals("g", fnType.callable.name)

    fnType.getReturnType(typeEvalContext).assertIs<PyTypeVarType>()
  }

  fun `test parse dict literal expression type`() {
    //{'new_col': ['sum', 'mean']}
    parse<PyCollectionTypeImpl>("builtins.dict[builtins.str, builtins.list[builtins.str]]")
  }

  fun `test parse module expression type`() {
    myFixture.configureByFile("pandas/__init__.py")
    val moduleType = parse<PyModuleType>("Module[pandas]")
    assertEquals("pandas", moduleType.name)
  }

  fun `test parse callable type`() {
    val ct = parse<PyCallableType>("(builtins.int) -> builtins.str")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val p0Ty = params.single().getType(typeEvalContext)!!
    assertEquals("int", p0Ty.name)
    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals("str", ret.name)
  }

  fun `test callable with parameter with default value`() {
    parse<PyCallableType>("""
      (
          *,
          key: None = None,
      ) -> builtins.bool
    """)
  }

  fun `test parse callable type complex`() {
    val ct = parse<PyCallableType>("(builtins.int | builtins.list[builtins.int]) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val p0Ty = params.single().getType(typeEvalContext)!!
    assertEquals("int | list", p0Ty.name)
    assertEquals("int", ((p0Ty as PyUnionType).members.last()!! as PyCollectionType).iteratedItemType!!.name)
  }

  fun `test parse callable type with type parameter`() {
    val ct = parse<PyCallableType>("[T: builtins.int](x: T) -> T")

    val params = ct.getParameters(typeEvalContext)!!
    assertSize(1, params)
    params.single().getType(typeEvalContext).assertIs<PyTypeVarType>()
    ct.getReturnType(typeEvalContext).assertIs<PyTypeVarType>()
  }

  fun `test parse callable named parameter`() {
    val ct = parse<PyCallableType>("(a: builtins.int) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertSize(1, params)
    val param = params.single()
    assertEquals("a", param.name)
    assertEquals(builtins.intType, param.getType(typeEvalContext))

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  fun `test parse callable default parameter`() {
    val ct = parse<PyCallableType>("(a: builtins.int = ...) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertSize(1, params)
    val param = params.single()
    assertEquals("a", param.name)
    assertEquals(builtins.intType, param.getType(typeEvalContext))
    assertTrue(param.hasDefaultValue())

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  fun `test parse nested callable`() {
    // Callable[[Callable[[], None], Callable[[], None]]
    val outer = parse<PyCallableType>("(() -> None, /) -> () -> None")

    // Outer should have exactly one positional-only parameter which is a callable
    val params = outer.getParameters(typeEvalContext)!!
    assertSize(2, params)
    val (first, slash) = params
    val innerParamCallable = first.getType(typeEvalContext).assertIs<PyCallableType>()

    assertTrue(slash.isPositionOnlySeparator)

    // Validate inner callable signature: no params, returns None
    val innerParamCallableParams = innerParamCallable.getParameters(typeEvalContext)!!
    assertEmpty(innerParamCallableParams)
    val innerParamCallableRet = innerParamCallable.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, innerParamCallableRet)

    // Return type should also be a callable
    val innerRetCallable = outer.getReturnType(typeEvalContext).assertIs<PyCallableType>()

    val innerRetCallableParams = innerRetCallable.getParameters(typeEvalContext)!!
    assertEmpty(innerRetCallableParams)
    val innerRetCallableRet = innerRetCallable.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, innerRetCallableRet)
  }

  fun `test callable variadic`() {
    val ct = parse<PyCallableType>("(*builtins.str, **test.B) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertSize(2, params)
    val (args, kwargs) = params

    // Check *args parameter
    assertTrue(args.isPositionalContainer)
    assertFalse(args.isKeywordContainer)
    assertNull(args.name)
    // The type should be str (a star expression unwraps it)
    val argsType = args.getType(typeEvalContext).assertIs<PyClassType>()
    assertEquals(builtins.strType, argsType)

    // Check **kwargs parameter
    assertFalse(kwargs.isPositionalContainer)
    assertTrue(kwargs.isKeywordContainer)
    assertNull(kwargs.name)
    val kwargsType = kwargs.getType(typeEvalContext).assertIs<PyClassType>()
    // The type should be B (as double star expression unwraps it)
    assertEquals("B", kwargsType.name)

    val ret = ct.getReturnType(typeEvalContext).assertIs<PyClassType>()
    assertEquals(builtins.noneType, ret)
  }

  fun `test type callable`() {
    parse<PyCustomType>("builtins.type[Callable]")
  }

  fun `test type typed dict`() {
    parse<PyCustomType>("builtins.type[TypedDict]")
  }

  fun `test Protocol`() {
    parse<PyCustomType>("builtins.type[Protocol]")
  }

  fun `test Generic`() {
    parse<PyCustomType>("builtins.type[Generic]")
  }

  fun `test special form`() {
    val type = parse<PyClassType>("builtins.type[Literal]")
    assertFalse(type.isDefinition)
    assertEquals("typing._SpecialForm", type.classQName)
    val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(myFixture.project))
    val getItemMember = type.resolveMember("__getitem__", null, AccessDirection.READ, resolveContext)!!.single().element
    getItemMember.assertIs<PyCallable>()
  }

  fun `test callable complex variadic`() {
    val ct = parse<PyCallableType>("(*a: *builtins.tuple[builtins.int], **b: **test.B) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertSize(2, params)
    val (args, kwargs) = params

    // Check *args parameter
    assertTrue(args.isPositionalContainer)
    assertEquals("a", args.name)
    // The type should be *tuple[int] (a star expression wrapping tuple)
    args.getType(typeEvalContext).assertIs<PyUnpackedTupleTypeImpl>()

    // Check **kwargs parameter
    assertTrue(kwargs.isKeywordContainer)
    assertEquals("b", kwargs.name)
    // The type should be **B (a double star expression wrapping B)
    kwargs.getType(typeEvalContext).assertIs<PyTypedDictType>()

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  fun `test complex callable`() {
    val fnType =
      parse<PyCallableType>("(builtins.str, /, x: builtins.int = builtins.int, *args: builtins.float, z: builtins.bool, **kwargs: builtins.complex) -> builtins.int")

    val params = fnType.getParameters(typeEvalContext)!!
    assertSize(6, params)
    val (first, slash, x, args, z) = params
    val kwargs = params.last()

    assertNull(first.name)
    assertEquals(builtins.strType, first.getType(typeEvalContext))

    assertTrue(slash.isPositionOnlySeparator)

    assertEquals("x", x.name)
    assertEquals(builtins.intType, x.getType(typeEvalContext))
    assertTrue(x.hasDefaultValue())

    assertTrue(args.isPositionalContainer)
    assertFalse(args.isKeywordContainer)
    assertEquals("args", args.name)
    assertEquals(PyNumericTowerUtil.enrich(builtins.floatType), args.getType(typeEvalContext))

    assertEquals("z", z.name)
    assertEquals(builtins.boolType, z.getType(typeEvalContext))

    assertFalse(kwargs.isPositionalContainer)
    assertTrue(kwargs.isKeywordContainer)
    assertEquals("kwargs", kwargs.name)
    val kwargsType = kwargs.getType(typeEvalContext).assertIs<PyCollectionType>()
    assertEquals(builtins.dictType!!.pyClass, kwargsType.pyClass)
    assertEquals(PyNumericTowerUtil.enrich(builtins.complexType), kwargsType.elementTypes[1])
  }

  fun `test parse generic scope function`() {
    val ty = parse<PyTypeVarType>("T@test.f")
    assertEquals("T", ty.name)
    assertEquals("test.f", ty.scopeOwner!!.qualifiedName)
  }

  fun `test parse generic scope class`() {
    val ty = parse<PyTypeVarType>("T@test.A")
    assertEquals("T", ty.name)
    assertEquals("test.A", ty.scopeOwner!!.qualifiedName)
  }

  fun `test parse generic scope nested class with function`() {
    val ty = parse<PyTypeVarType>("T@test.A.B.f")
    assertEquals("T", ty.name)
    assertEquals("test.A.B.f", ty.scopeOwner!!.qualifiedName)
  }

  fun `test parse generic scope method`() {
    val ty = parse<PyTypeVarType>("T@test.A.f")
    assertEquals("T", ty.name)
    assertEquals("test.A.f", ty.scopeOwner!!.qualifiedName)
  }

  fun `test self type`() {
    val ty = parse<PySelfType>("typing.Self@test.A")
    assertEquals("test.A", ty.pyClass.qualifiedName)
  }

  fun `test new type`() {
    val file = myFixture.configureByText("user_id.py", """
      from typing import NewType
      
      UserId = NewType("UserId", int)
    """.trimIndent())
    val type = parse<PyTypingNewType>("user_id.UserId", file)
    assertEquals("UserId", type.name)
    assertEquals(builtins.intType, type.classType)
  }

  fun `test enum literal`() {
    val file = myFixture.configureByText("color.py", """
      from enum import Enum

      class Color(Enum):
        RED = 1
        GREEN = 2
        BLUE = 3
    """.trimIndent()) as PyFile
    val enumClass = file.findTopLevelClass("Color")
    val type = parse<PyUnionType>("typing.Literal[color.Color.BLUE, color.Color.RED]", file)
    val expectedNames = listOf("BLUE", "RED")
    assertSize(expectedNames.size, type.members)
    for ((index, subType) in type.members.withIndex()) {
      subType.assertIs<PyLiteralType>()
      assertEquals(enumClass, subType.pyClass)
      assertEquals(expectedNames[index], subType.enumMemberName)
    }
  }

  fun `test overload`() {
    val type = parse<PyOverloadType>("Overload[(int) -> int, (str) -> str]")
    assertSize(2, type.items)
    for (item in type.items) {
      item.assertIs<PyCallableType>()
    }
  }

  fun `test isSelf`() {
    myFixture.configureByText("test.py", """
      class A:
          def f(self, a: A): ...
          @classmethod
          def m(cls, a: type[A]): ...
    """.trimIndent())
    val method = parse<PyFunctionType>("def test.A.f(self: test.A, a: test.A) -> None")
    val methodParameters = method.getParameters(typeEvalContext)!!
    assertTrue(methodParameters[0].isSelf)
    assertFalse(methodParameters[1].isSelf)

    val classmethod = parse<PyFunctionType>("def test.A.m(cls: builtins.type[test.A], a: builtins.type[test.A]) -> None")
    val classParameters = classmethod.getParameters(typeEvalContext)!!
    assertTrue(classParameters[0].isSelf)
    assertFalse(classParameters[1].isSelf)
  }

  override fun getTestDataPath(): String {
    return PathManager.getHomePath() + "/python/testData/lsp/typeInference"
  }
}