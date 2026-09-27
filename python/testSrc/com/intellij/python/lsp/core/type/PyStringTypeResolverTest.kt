// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.type

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyCustomType
import com.jetbrains.python.PyNames
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyCallableType
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyClassTypeImpl
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(classes = [PyStringTypeResolver::class])
class PyStringTypeResolverTest : PyCodeInsightTestCase() {
  @BeforeEach
  fun configureTestFile() {
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

  private val builtins get() = PyBuiltinCache.getInstance(myFixture.file)

  private val typeEvalContext: TypeEvalContext
    get() = TypeEvalContext.externalContext(myFixture.project)

  private fun test(block: () -> Unit) = runReadActionBlocking { block() }

  private inline fun <reified T : PyType> test(typeString: String) = test {
    parse<T>(typeString)
  }

  private inline fun <reified T : PyType> parse(s: String, anchor: PsiElement = myFixture.file): T {
    val result = PyStringTypeResolver.resolvePyType(anchor as PyTypedElement, s.trimIndent())
    assertNotNull(result, "the type completely failed to parse")
    return assertInstanceOf<T>(result!!.get())
  }

  @Test
  fun `parse unresolved`() = test {
    val ty = PyStringTypeResolver.resolvePyType(myFixture.file as PyTypedElement, "unresolved")
    assertNull(ty, "The type was resolved, which is unexpected")
  }

  @Test
  @TestCaseOptions(enablePyAnyType = false)
  fun `parse Any old`() = test {
    assertFalse(PyAnyType.isEnabled)
    val result = PyStringTypeResolver.resolvePyType(myFixture.file as PyTypedElement, PyTypingTypeProvider.ANY)
    assertNotNull(result, "the type completely failed to parse")
    assertNull(result!!.get(), "The type was not Any")
  }

  @Test
  @TestCaseOptions(enablePyAnyType = true)
  fun `parse Any`() = test<PyAnyType.Any>(PyTypingTypeProvider.ANY)

  @Test
  @TestCaseOptions(enablePyAnyType = true)
  fun `parse Unknown`() = test<PyAnyType.Unknown>(PyNames.UNKNOWN_TYPE)

  @Test
  fun `parse union with Any`() = test<PyUnionType>("typing.Any | None")

  @Test
  fun `parse simple`() = test {
    val mapping =
      parse<PyClassType>("collections.abc.Mapping[collections.abc.Sequence[builtins.int], collections.abc.Iterable[builtins.str]]")

    val params = mapping.typeArguments
    assertEquals(2, params.size)

    val (keyParam, valParam) = params

    // Key is Sequence[int]
    assertInstanceOf<PyClassType>(keyParam)
    assertEquals(listOf(builtins.intType), keyParam.typeArguments)

    // Value is Iterable[str]
    assertInstanceOf<PyClassType>(valParam)
    assertEquals(listOf(builtins.strType), valParam.typeArguments)
  }

  @Test
  fun `parse tuple fixed`() = test {
    val tupleType = parse<PyTupleType>("builtins.tuple[builtins.int, builtins.str]")
    assertFalse(tupleType.isHomogeneous)

    val elems = tupleType.elementTypes

    assertEquals(2, elems.size)
    val (first, second) = elems
    assertEquals(builtins.intType, first)
    assertEquals(builtins.strType, second)
  }

  @Test
  fun `parse int`() = test {
    val type = parse<PyClassType>("builtins.int")
    assertFalse(type.isDefinition)
    assertEquals(builtins.intType, type)
  }

  @Test
  fun `parse type int`() = test {
    val type = parse<PyClassType>("builtins.type[builtins.int]")
    assertTrue(type.isDefinition)
    assertEquals(builtins.intType!!.pyClass, type.pyClass)
  }

  @Test
  fun `parse tuple variadic`() = test {
    val tupleType = parse<PyTupleType>("builtins.tuple[builtins.int, ...]")

    assertTrue(tupleType.isHomogeneous)
    assertEquals(builtins.intType, tupleType.elementTypes.single())
  }

  @Test
  fun `parse literal`() = test {
    val union = parse<PyUnionType>("typing.Literal[1, 2]")
    assertEquals(2, union.members.size)

    val (left, right) = union.members.toList()
    assertInstanceOf<PyLiteralType>(left)
    assertEquals("1", left.expressionText)
    assertInstanceOf<PyLiteralType>(right)
    assertEquals("2", right.expressionText)
  }

  @Test
  fun `parse function type`() = test {
    val fnType = parse<PyFunctionType>("def test.f(x: builtins.int, y: builtins.str = 'abb') -> builtins.str")

    assertEquals("f", fnType.callable.name)

    val parameters = fnType.getParameters(typeEvalContext)!!
    assertEquals(2, parameters.size)

    val parameter0 = parameters[0]
    assertEquals("x", parameter0.name)
    val parameter0Type = assertInstanceOf<PyClassType>(parameter0.getType(typeEvalContext))
    assertEquals("int", parameter0Type.name)
    assertFalse(parameter0.hasDefaultValue())

    val parameter1 = parameters[1]
    assertEquals("y", parameter1.name)
    val parameter1Type = assertInstanceOf<PyClassType>(parameter1.getType(typeEvalContext))
    assertEquals("str", parameter1Type.name)
    assertTrue(parameter1.hasDefaultValue())

    val returnType = assertInstanceOf<PyClassType>(fnType.getReturnType(typeEvalContext))
    assertEquals("str", returnType.name)
  }

  @Test
  fun `parse member callable`() = test {
    val fnType = parse<PyFunctionType>("def test.A.f() -> None")

    assertEquals("f", fnType.callable.name)
  }

  @Test
  fun `parse member callable staticmethod`() = test {
    val fnType = parse<PyFunctionType>("def test.A.s() -> None")

    assertEquals("s", fnType.callable.name)
  }

  @Test
  fun `parse function from nested class`() = test {
    val fnType = parse<PyFunctionType>("def test.A.B.f() -> None")

    assertEquals("f", fnType.callable.name)
  }

  @Test
  fun `parse function unresolved`() = test<PyCallableType>("def unresolved() -> Unknown")

  @Test
  fun `parse function with type parameter`() = test {
    val fnType = parse<PyFunctionType>("def test.A.g[T: builtins.int](x: T) -> T")

    assertInstanceOf<PyTypeVarType>(fnType.getReturnType(typeEvalContext))
    assertEquals("g", fnType.callable.name)
  }

  @Test
  //{'new_col': ['sum', 'mean']}
  fun `parse dict literal expression type`() = test<PyClassTypeImpl>("builtins.dict[builtins.str, builtins.list[builtins.str]]")

  @Test
  fun `parse module expression type`() {
    val pandas = myFixture.addFileToProject("pandas/__init__.py", "")
    test {
      val moduleType = parse<PyModuleType>("Module[pandas]", pandas)
      assertEquals("pandas", moduleType.name)
    }
  }

  @Test
  fun `parse callable type`() = test {
    val ct = parse<PyCallableType>("(builtins.int) -> builtins.str")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val p0Ty = params.single().getType(typeEvalContext)!!
    assertEquals("int", p0Ty.name)
    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals("str", ret.name)
  }

  @Test
  fun `callable with parameter with default value`() = test<PyCallableType>("""
      (
          *,
          key: None = None,
      ) -> builtins.bool
    """)

  @Test
  fun `parse callable type complex`() = test {
    val ct = parse<PyCallableType>("(builtins.int | builtins.list[builtins.int]) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val p0Ty = params.single().getType(typeEvalContext)!!
    assertEquals("int | list", p0Ty.name)
    val listType = (p0Ty as PyUnionType).members.last() as PyClassType
    assertEquals(listOf(builtins.intType), listType.typeArguments)
  }

  @Test
  fun `parse callable type with type parameter`() = test {
    val ct = parse<PyCallableType>("[T: builtins.int](x: T) -> T")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    assertInstanceOf<PyTypeVarType>(params.single().getType(typeEvalContext))
    assertInstanceOf<PyTypeVarType>(ct.getReturnType(typeEvalContext))
  }

  @Test
  fun `parse callable named parameter`() = test {
    val ct = parse<PyCallableType>("(a: builtins.int) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val param = params.single()
    assertEquals("a", param.name)
    assertEquals(builtins.intType, param.getType(typeEvalContext))

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  @Test
  fun `parse callable default parameter`() = test {
    val ct = parse<PyCallableType>("(a: builtins.int = ...) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(1, params.size)
    val param = params.single()
    assertEquals("a", param.name)
    assertEquals(builtins.intType, param.getType(typeEvalContext))
    assertTrue(param.hasDefaultValue())

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  @Test
  fun `parse nested callable`() = test {
    // Callable[[Callable[[], None], Callable[[], None]]
    val outer = parse<PyCallableType>("(() -> None, /) -> () -> None")

    // Outer should have exactly one positional-only parameter which is a callable
    val params = outer.getParameters(typeEvalContext)!!
    assertEquals(2, params.size)
    val (first, slash) = params
    val innerParamCallable = assertInstanceOf<PyCallableType>(first.getType(typeEvalContext))

    assertTrue(slash.isPositionOnlySeparator)

    // Validate inner callable signature: no params, returns None
    val innerParamCallableParams = innerParamCallable.getParameters(typeEvalContext)!!
    assertTrue(innerParamCallableParams.isEmpty())
    val innerParamCallableRet = innerParamCallable.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, innerParamCallableRet)

    // Return type should also be a callable
    val innerRetCallable = assertInstanceOf<PyCallableType>(outer.getReturnType(typeEvalContext))

    val innerRetCallableParams = innerRetCallable.getParameters(typeEvalContext)!!
    assertTrue(innerRetCallableParams.isEmpty())
    val innerRetCallableRet = innerRetCallable.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, innerRetCallableRet)
  }

  @Test
  fun `callable variadic`() = test {
    val ct = parse<PyCallableType>("(*builtins.str, **test.B) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(2, params.size)
    val (args, kwargs) = params

    // Check *args parameter
    assertTrue(args.isPositionalContainer)
    assertFalse(args.isKeywordContainer)
    assertNull(args.name)
    // The type should be str (a star expression unwraps it)
    val argsType = assertInstanceOf<PyClassType>(args.getType(typeEvalContext))
    assertEquals(builtins.strType, argsType)

    // Check **kwargs parameter
    assertFalse(kwargs.isPositionalContainer)
    assertTrue(kwargs.isKeywordContainer)
    assertNull(kwargs.name)
    val kwargsType = assertInstanceOf<PyClassType>(kwargs.getType(typeEvalContext))
    // The type should be B (as double star expression unwraps it)
    assertEquals("B", kwargsType.name)

    val ret = assertInstanceOf<PyClassType>(ct.getReturnType(typeEvalContext))
    assertEquals(builtins.noneType, ret)
  }

  @Test
  fun `type callable`() = test<PyCustomType>("builtins.type[Callable]")

  @Test
  fun `type typed dict`() = test<PyCustomType>("builtins.type[TypedDict]")

  @Test
  fun `type Protocol`() = test<PyCustomType>("builtins.type[Protocol]")

  @Test
  fun `type Generic`() = test<PyCustomType>("builtins.type[Generic]")

  @Test
  fun `special form`() = test {
    val type = parse<PyClassType>("builtins.type[Literal]")
    assertFalse(type.isDefinition)
    assertEquals("typing._SpecialForm", type.classQName)
    val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(myFixture.project))
    val getItemMember = type.resolveMember("__getitem__", null, AccessDirection.READ, resolveContext)!!.single().element
    assertInstanceOf<PyCallable>(getItemMember)
  }

  @Test
  fun `callable complex variadic`() = test {
    val ct = parse<PyCallableType>("(*a: *builtins.tuple[builtins.int], **b: **test.B) -> None")

    val params = ct.getParameters(typeEvalContext)!!
    assertEquals(2, params.size)
    val (args, kwargs) = params

    // Check *args parameter
    assertTrue(args.isPositionalContainer)
    assertEquals("a", args.name)
    // The type should be *tuple[int] (a star expression wrapping tuple)
    assertInstanceOf<PyUnpackedTupleTypeImpl>(args.getType(typeEvalContext))

    // Check **kwargs parameter
    assertTrue(kwargs.isKeywordContainer)
    assertEquals("b", kwargs.name)
    // The type should be **B (a double star expression wrapping B)
    assertInstanceOf<PyTypedDictType>(kwargs.getType(typeEvalContext))

    val ret = ct.getReturnType(typeEvalContext)!!
    assertEquals(builtins.noneType, ret)
  }

  @Test
  fun `complex callable`() = test {
    val fnType =
      parse<PyCallableType>("(builtins.str, /, x: builtins.int = builtins.int, *args: builtins.float, z: builtins.bool, **kwargs: builtins.complex) -> builtins.int")

    val params = fnType.getParameters(typeEvalContext)!!
    assertEquals(6, params.size)
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
    val kwargsType = assertInstanceOf<PyClassType>(kwargs.getType(typeEvalContext))
    assertEquals(builtins.dictType!!.pyClass, kwargsType.pyClass)
    assertEquals(PyNumericTowerUtil.enrich(builtins.complexType), kwargsType.typeArguments[1])
  }

  @Test
  fun `parse generic scope function`() = test {
    val ty = parse<PyTypeVarType>("T@test.f")
    assertEquals("T", ty.name)
    assertEquals("test.f", ty.scopeOwner!!.qualifiedName)
  }

  @Test
  fun `parse generic scope class`() = test {
    val ty = parse<PyTypeVarType>("T@test.A")
    assertEquals("T", ty.name)
    assertEquals("test.A", ty.scopeOwner!!.qualifiedName)
  }

  @Test
  fun `parse generic scope nested class with function`() = test {
    val ty = parse<PyTypeVarType>("T@test.A.B.f")
    assertEquals("T", ty.name)
    assertEquals("test.A.B.f", ty.scopeOwner!!.qualifiedName)
  }

  @Test
  fun `parse generic scope method`() = test {
    val ty = parse<PyTypeVarType>("T@test.A.f")
    assertEquals("T", ty.name)
    assertEquals("test.A.f", ty.scopeOwner!!.qualifiedName)
  }

  @Test
  fun `self type`() = test {
    val ty = parse<PySelfType>("typing.Self@test.A")
    assertEquals("test.A", ty.pyClass.qualifiedName)
  }

  @Test
  fun `new type`() {
    val file = myFixture.configureByText("user_id.py", """
      from typing import NewType

      UserId = NewType("UserId", int)
    """.trimIndent())
    test {
      val type = parse<PyTypingNewType>("user_id.UserId", file)
      assertEquals("UserId", type.name)
      assertEquals(builtins.intType, type.classType)
    }
  }

  @Test
  fun `enum literal`() {
    val file = myFixture.configureByText("color.py", """
      from enum import Enum

      class Color(Enum):
        RED = 1
        GREEN = 2
        BLUE = 3
    """.trimIndent()) as PyFile
    test {

      val enumClass = file.findTopLevelClass("Color")
      val type = parse<PyUnionType>("typing.Literal[color.Color.BLUE, color.Color.RED]", file)
      val expectedNames = listOf("BLUE", "RED")
      assertEquals(expectedNames.size, type.members.size)
      for ((index, subType) in type.members.withIndex()) {
        assertInstanceOf<PyLiteralType>(subType)
        assertEquals(enumClass, subType.pyClass)
        assertEquals(expectedNames[index], subType.enumMemberName)
      }
    }
  }

  @Test
  fun overload() = test {
    val type = parse<PyOverloadType>("Overload[(int) -> int, (str) -> str]")
    assertEquals(2, type.items.size)
    for (item in type.items) {
      assertInstanceOf<PyCallableType>(item)
    }
  }

  @Test
  fun isSelf() {
    myFixture.configureByText("test.py", """
      class A:
          def f(self, a: A): ...
          @classmethod
          def m(cls, a: type[A]): ...
      """.trimIndent())
    test {
      val method = parse<PyFunctionType>("def test.A.f(self: test.A, a: test.A) -> None")
      val methodParameters = method.getParameters(typeEvalContext)!!
      assertTrue(methodParameters[0].isSelf)
      assertFalse(methodParameters[1].isSelf)

      val classmethod = parse<PyFunctionType>("def test.A.m(cls: builtins.type[test.A], a: builtins.type[test.A]) -> None")
      val classParameters = classmethod.getParameters(typeEvalContext)!!
      assertTrue(classParameters[0].isSelf)
      assertFalse(classParameters[1].isSelf)
    }
  }
}
