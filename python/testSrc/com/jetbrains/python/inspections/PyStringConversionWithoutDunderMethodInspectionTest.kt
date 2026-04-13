// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyInspectionTestCase

class PyStringConversionWithoutDunderMethodInspectionTest : PyInspectionTestCase() {

  override fun getInspectionClass() = PyStringConversionWithoutDunderMethodInspection::class.java

  fun `test generator`() = doTestByText("""
    f"{<weak_warning descr="Type 'Generator' doesn't define '__str__', '__repr__', or '__format__', so the result might not be useful">(_ for _ in range(10))</weak_warning>}"
  """.trimIndent())

  fun `test function`() = doTestByText("""
    def f(): ...
    f"{<weak_warning descr="Type 'FunctionType' string value might not be useful">f</weak_warning>}"
  """.trimIndent())

  fun `test zip`() = doTestByText("""
    def f(): ...
    f"{<weak_warning descr="Type 'zip' doesn't define '__str__', '__repr__', or '__format__', so the result might not be useful">zip()</weak_warning>}"
  """.trimIndent())

  fun `test super`() = doTestByText("""
    def f(): ...
    f"{<weak_warning descr="Type 'super' doesn't define '__str__', '__repr__', or '__format__', so the result might not be useful">super()</weak_warning>}"
  """.trimIndent())

  fun `test str call without dunder methods`() = doTestByText("""
    class A:
        pass

    str(<weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test str call with dunder str`() = doTestByText("""
    class A:
        def __str__(self):
            return "A instance"

    str(A())
    """.trimIndent())

  fun `test str call with dunder repr`() = doTestByText("""
    class A:
        def __repr__(self):
            return "A()"

    str(A())
    """.trimIndent())

  fun `test str call with dunder format`() = doTestByText("""
    class A:
        def __format__(self, format_spec):
            return f"A formatted with {format_spec}"

    str(<weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test format call without dunder methods`() = doTestByText("""
    class A:
        pass

    format(<weak_warning descr="Type 'A' doesn't define '__str__', '__repr__', or '__format__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test format call with repr method`() = doTestByText("""
    class A:
        def __repr__(self): ...

    format(A())
    """.trimIndent())

  fun `test format call with str method`() = doTestByText("""
    class A:
        def __str__(self): ...

    format(A())
    """.trimIndent())

  fun `test inherited dunder methods`() = doTestByText("""
    class Base:
        def __str__(self):
            return "Base"

    class Derived(Base):
        pass

    str(Derived())
    """.trimIndent())

  fun `test should not warn for builtin types`() = doTestByText("""
    # see default ignore list for explanation
    repr(42)
    repr((1, 2, 3))
    repr([1, 2, 3])
    repr({"key": "value"})
    repr(None)
    repr(True)
    repr("asdf")
    """.trimIndent())

  @TestFor(issues = ["PY-89082"])
  fun `test should not warn for pathlib`() = doTestByText("""
    # see default ignore list for explanation
    from pathlib import PurePath
    
    repr(PurePath())
    str(PurePath())
    format(PurePath())
    """.trimIndent())

  fun `test should warn for object`() = doTestByText("""
    repr(<weak_warning descr="Type 'object' string value might not be useful">object()</weak_warning>)
    """.trimIndent())

  fun `test should warn for type`() = doTestByText("""
    str(<weak_warning descr="Type 'type' string value might not be useful">int</weak_warning>)
    """.trimIndent())

  fun `test repr call without dunder methods`() = doTestByText("""
    class A:
        pass

    repr(<weak_warning descr="Type 'A' doesn't define '__repr__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test repr call with dunder repr`() = doTestByText("""
    class A:
        def __repr__(self):
            return "A()"

    repr(A())
    """.trimIndent())

  fun `test repr call with dunder str`() = doTestByText("""
    class A:
        def __str__(self):
            return "A instance"

    repr(<weak_warning descr="Type 'A' doesn't define '__repr__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test f-string without dunder methods`() = doTestByText("""
    class A:
        pass

    f"{<weak_warning descr="Type 'A' doesn't define '__str__', '__repr__', or '__format__', so the result might not be useful">A()</weak_warning>}"
    """.trimIndent())

  fun `test f-string string`() = doTestByText("""
    class A:
        def __format__(self, format_spec): ...

    f"{<weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>!s}"
    """.trimIndent())

  fun `test f-string repr`() = doTestByText("""
    class A:
        def __str__(self): ...
        
        def __format__(self, format_spec): ...

    f"{<weak_warning descr="Type 'A' doesn't define '__repr__', so the result might not be useful">A()</weak_warning>!r}"
    """.trimIndent())

  fun `test f-string debug`() = doTestByText("""
    class A:
        def __str__(self): ...
        
        def __format__(self, format_spec): ...

    f"{<weak_warning descr="Type 'A' doesn't define '__repr__', so the result might not be useful">A()</weak_warning>=}"
    """.trimIndent())

  fun `test f-string with dunder str`() = doTestByText("""
    class A:
        def __str__(self):
            return "A instance"

    f"{A()}"
    """.trimIndent())

  fun `test f-string with dunder repr`() = doTestByText("""
    class A:
        def __repr__(self):
            return "A()"

    f"{A()}"
    """.trimIndent())

  fun `test f-string with dunder format`() = doTestByText("""
    class A:
        def __format__(self):
            return "A()"

    f"{A()}"
    """.trimIndent())

  fun `test print call without dunder methods`() = doTestByText("""
    class A:
        pass

    print(<weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>, <weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>)
    """.trimIndent())

  fun `test print call with dunder str`() = doTestByText("""
    class A:
        def __str__(self):
            return "A instance"

    print(A())
    """.trimIndent())

  fun `test print with keyword arguments`() = doTestByText("""
    class A:
        pass

    print(
        <weak_warning descr="Type 'A' doesn't define '__str__' or '__repr__', so the result might not be useful">A()</weak_warning>,
        file=A(),
    )
    """.trimIndent())

  fun `test union reports`() = doTestByText("""
    class A:
        def __str__(self): pass
    
    class B:
        pass
    
    def f(ab: A | B):
        str(<weak_warning descr="Type 'B' doesn't define '__str__' or '__repr__', so the result might not be useful">ab</weak_warning>)
    """.trimIndent())

  fun `test union doesn't report`() = doTestByText("""
    class A:
        def __str__(self): pass
    
    class B:
        def __str__(self): pass
    
    def f(ab: A | B):
        str(ab)
    """.trimIndent())

  fun `test derived from ignored`() = doTestByText("""
    class A(int): ...
    
    str(A())    
    """.trimIndent())

  fun `test quickfix add to ignored types removes warning`() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
      class A: ...
      
      str(<caret>A())
    """.trimIndent())
    myFixture.enableInspections(getAllInspectionClasses())

    val action = myFixture.findSingleIntention(
      PyPsiBundle.message("INSP.string.conversion.add.to.ignored.types", "A"))
    myFixture.launchAction(action)

    myFixture.checkHighlighting(true, false, true)
  }

  fun `test quickfix remove from reported types removes warning`() {
    myFixture.configureByText(PythonFileType.INSTANCE, """
      def f(): ...
      f"{<caret>f}"
    """.trimIndent())
    myFixture.enableInspections(getAllInspectionClasses())

    val action = myFixture.findSingleIntention(
      PyPsiBundle.message("INSP.string.conversion.remove.from.reported.types", "FunctionType"))
    myFixture.launchAction(action)

    myFixture.checkHighlighting(true, false, true)
  }
}
