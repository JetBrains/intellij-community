# encoding: utf-8
"""
Tests basic things that generator3 consists of.
NOTE: does not work in Jython 2.2 or IronPython 1.x, because pyparsing does not.
"""

import unittest
from generator3 import *

M = ModuleRedeclarator

import sys

IS_CLI = sys.platform == 'cli'
VERSION = sys.version_info[:2] # only (major, minor)

class TestRestoreFuncByDocComment(unittest.TestCase):
    """
    Tries to restore function signatures by doc strings.
    """

    def setUp(self):
        self.m = ModuleRedeclarator(None, None, '/dev/null')

    def testTrivial(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, b, c) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, b, c)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testTrivialNested(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, (b, c), d) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, (b, c), d)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testWithDefault(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, b, c=1) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, b, c=1)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testNestedWithDefault(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, (b1, b2), c=1) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, (b1, b2), c=1)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testAbstractDefault(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah f(a, b=obscuredefault) ololo', "f", "f", None)
        self.assertEquals(result, "f(a, b=None)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testWithReserved(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(class, object, def) ololo", "f", "f", None)
        self.assertEquals(result, "f(p_class, p_object, p_def)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testWithReservedOpt(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(foo, bar[, def]) ololo", "f", "f", None)
        self.assertEquals(result, "f(foo, bar, p_def=None)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testPseudoNested(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, (b1, b2, ...)) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, b_tuple)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testImportLike(self):
        # __import__
        result, ret_sig, note = self.m.parse_func_doc("blah f(name, globals={}, locals={}, fromlist=[], level=-1) ololo",
                                                      "f", "f", None)
        self.assertEquals(result, "f(name, globals={}, locals={}, fromlist=[], level=-1)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testOptionalBracket(self):
        # reduce
        result, ret_sig, note = self.m.parse_func_doc("blah f(function, sequence[, initial]) ololo", "f", "f", None)
        self.assertEquals(result, "f(function, sequence, initial=None)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testWithMore(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(foo [, bar1, bar2, ...]) ololo", "f", "f", None)
        self.assertEquals(result, "f(foo, *bar)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testNestedOptionals(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(foo [, bar1 [, bar2]]) ololo", "f", "f", None)
        self.assertEquals(result, "f(foo, bar1=None, bar2=None)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testInnerTuple(self):
        result, ret_sig, note = self.m.parse_func_doc("blah load_module(name, file, filename, (suffix, mode, type)) ololo"
            , "load_module", "load_module", None)
        self.assertEquals(result, "load_module(name, file, filename, (suffix, mode, type))")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testIncorrectInnerTuple(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(a, (b=1, c=2)) ololo", "f", "f", None)
        self.assertEquals(result, "f(a, p_b)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testNestedOnly(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f((foo, bar, baz)) ololo", "f", "f", None)
        self.assertEquals(result, "f((foo, bar, baz))")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testTwoPseudoNested(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f((a1, a2, ...), (b1, b2,..)) ololo", "f", "f", None)
        self.assertEquals(result, "f(a_tuple, b_tuple)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testTwoPseudoNestedWithLead(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(x, (a1, a2, ...), (b1, b2,..)) ololo", "f", "f", None)
        self.assertEquals(result, "f(x, a_tuple, b_tuple)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testPseudoNestedRange(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f((a1, ..., an), b) ololo", "f", "f", None)
        self.assertEquals(result, "f(a_tuple, b)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testIncorrectList(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(x, y, 3, $) ololo", "f", "f", None)
        self.assertEquals(result, "f(x, y, *args, **kwargs)")
        self.assertEquals(note, M.SIG_DOC_UNRELIABLY)

    def testIncorrectStarredList(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(x, *y, 3, $) ololo", "f", "f", None)
        self.assertEquals(result, "f(x, *y, **kwargs)")
        self.assertEquals(note, M.SIG_DOC_UNRELIABLY)

    def testClashingNames(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(x, y, (x, y), z) ololo", "f", "f", None)
        self.assertEquals(result, "f(x, y, (x_1, y_1), z)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testQuotedParam(self):
        # like __delattr__
        result, ret_sig, note = self.m.parse_func_doc("blah getattr('name') ololo", "getattr", "getattr", None)
        self.assertEquals(result, "getattr(name)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testQuotedParam2(self):
        # like __delattr__, too
        result, ret_sig, note = self.m.parse_func_doc('blah getattr("name") ololo', "getattr", "getattr", None)
        self.assertEquals(result, "getattr(name)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testOptionalTripleDot(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah f(foo, ...) ololo', "f", "f", None)
        self.assertEquals(result, "f(foo, *more)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testUnderscoredName(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah f(foo_one, _bar_two) ololo', "f", "f", None)
        self.assertEquals(result, "f(foo_one, _bar_two)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testDashedName(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah f(something-else, for-a-change) ololo', "f", "f", None)
        self.assertEquals(result, "f(something_else, for_a_change)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testSpacedDefault(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah f(a, b = 1) ololo', "f", "f", None)
        self.assertEquals(result, "f(a, b=1)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testSpacedName(self):
        # like new(S, ...)
        result, ret_sig, note = self.m.parse_func_doc('blah femme(skirt or pants) ololo', "femme", "femme", None)
        self.assertEquals(result, "femme(skirt_or_pants)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testKeyword(self):
        result, ret_sig, note = self.m.parse_func_doc('blah femme(from, to) ololo', "femme", "femme", None)
        self.assertEquals(result, "femme(from_, to)")
        self.assertEquals(note, M.SIG_DOC_NOTE)


class TestRestoreMethodByDocComment(unittest.TestCase):
    """
    Restoring with a class name set
    """

    def setUp(self):
        self.m = ModuleRedeclarator(None, None, '/dev/null')

    def testPlainMethod(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(self, foo, bar) ololo", "f", "f", "SomeClass")
        self.assertEquals(result, "f(self, foo, bar)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testInsertSelf(self):
        result, ret_sig, note = self.m.parse_func_doc("blah f(foo, bar) ololo", "f", "f", "SomeClass")
        self.assertEquals(result, "f(self, foo, bar)")
        self.assertEquals(note, M.SIG_DOC_NOTE)


class TestAnnotatedParameters(unittest.TestCase):
    """
    f(foo: int) and friends; in doc comments, happen in 2.x world, too.
    """

    def setUp(self):
        self.m = ModuleRedeclarator(None, None, '/dev/null')

    def testMixed(self):
        result, ret_sig, note = self.m.parse_func_doc('blah f(i: int, foo) ololo', "f", "f", None)
        self.assertEquals(result, "f(i, foo)")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testNested(self):
        result, ret_sig, note = self.m.parse_func_doc('blah f(i: int, (foo: bar, boo: Decimal)) ololo', "f", "f", None)
        self.assertEquals(result, "f(i, (foo, boo))")
        self.assertEquals(note, M.SIG_DOC_NOTE)

    def testSpaced(self):
        result, ret_sig, note = self.m.parse_func_doc('blah f(i: int, j :int, k : int) ololo', "f", "f", None)
        self.assertEquals(result, "f(i, j, k)")
        self.assertEquals(note, M.SIG_DOC_NOTE)


if not IS_CLI and VERSION < (3, 0):
    class TestInspect(unittest.TestCase):
        """
        See that inspect actually works if needed
        """

        def setUp(self):
            self.m = ModuleRedeclarator(None, None, '/dev/null')

        def testSimple(self):
            def target(a, b, c=1, *d, **e):
                return a, b, c, d, e

            result = restore_by_inspect(target)
            self.assertEquals(result, "(a, b, c=1, *d, **e)")

        def testNested(self):
            # NOTE: Py3k can't handle nested tuple args, thus we compile it conditionally
            code = (
                "def target(a, (b, c), d, e=1):\n"
                "    return a, b, c, d, e"
            )
            namespace = {}
            eval(compile(code, "__main__", "single"), namespace)
            target = namespace['target']

            result = restore_by_inspect(target)
            self.assertEquals(result, "(a, (b, c), d, e=1)")

class _DiffPrintingTestCase(unittest.TestCase):
    def assertEquals(self, etalon, specimen, msg=None):
        if type(etalon) == str and type(specimen) == str and etalon != specimen:
            print("%s" % "\n")
            # print side by side
            ei = iter(etalon.split("\n"))
            si = iter(specimen.split("\n"))
            if VERSION < (3, 0):
                si_next = si.next
            else:
                si_next = si.__next__
            for el in ei:
                try: sl = si_next()
                except StopIteration: break # I wish the exception would just work as break
                if el != sl:
                    print("!%s" % el)
                    print("?%s" % sl)
                else:
                    print(">%s" % sl)
                    # one of the iters might not end yet
            for el in ei:
                print("!%s" % el)
            for sl in si:
                print("?%s" % sl)
            raise self.failureException(msg)
        else:
            self.failUnlessEqual(etalon, specimen, msg)


class TestSpecialCases(unittest.TestCase):
    """
    Tests cases where predefined overrides kick in
    """

    def setUp(self):
        import sys

        if VERSION >= (3, 0):
            import builtins as the_builtins

            self.builtins_name = the_builtins.__name__
        else:
            import __builtin__ as the_builtins

            self.builtins_name = the_builtins.__name__
        self.m = ModuleRedeclarator(the_builtins, None, None, doing_builtins=True)

    def _testBuiltinFuncName(self, func_name, expected):
        class_name = None
        self.assertTrue(self.m.is_predefined_builtin(self.builtins_name, class_name, func_name))
        result, note = restore_predefined_builtin(class_name, func_name)
        self.assertEquals(result, func_name + expected)
        self.assertEquals(note, "known special case of " + func_name)

    def testZip(self):
        self._testBuiltinFuncName("zip", "(seq1, seq2, *more_seqs)")

    def testLocalImports(self):
        if VERSION >= (3, 0):
            self.m.redo("builtins", False)
        else:
            self.m.redo("__builtin__", False)
        for classes_buff in self.m.classes_buffs:
            for data in classes_buff.data:
                self.assertFalse("from object import object" in data)
                self.assertFalse("from .object import object" in data)

    def testRange(self):
        self._testBuiltinFuncName("range", "(start=None, stop=None, step=None)")

    def testFilter(self):
        self._testBuiltinFuncName("filter", "(function_or_none, sequence)")

        # we could want to test a class without __dict__, but it takes a C extension to really create one,

class TestDataOutput(_DiffPrintingTestCase):
    """
    Tests for sanity of output of data members
    """

    def setUp(self):
        self.m = ModuleRedeclarator(self, None, 4) # Pass anything with __dict__ as module

    def checkFmtValue(self, data, expected):
        buf = Buf(self.m)
        self.m.fmt_value(buf.out, data, 0)
        result = "".join(buf.data).strip()
        self.assertEquals(expected, result)

    def testRecursiveDict(self):
        data = {'a': 1}
        data['b'] = data
        expected = "\n".join((
            "{",
            "    'a': 1,",
            "    'b': '<value is a self-reference, replaced by this string>',",
            "}"
        ))
        self.checkFmtValue(data, expected)

    def testRecursiveList(self):
        data = [1]
        data.append(data)
        data.append(2)
        data.append([10, data, 20])
        expected = "\n".join((
            "[",
            "    1,",
            "    '<value is a self-reference, replaced by this string>',",
            "    2,",
            "    [",
            "        10,",
            "        '<value is a self-reference, replaced by this string>',",
            "        20,",
            "    ],",
            "]"
        ))
        self.checkFmtValue(data, expected)

if not IS_CLI:
    class TestReturnTypes(unittest.TestCase):
        """
        Tests for sanity of output of data members
        """

        def setUp(self):
            self.m = ModuleRedeclarator(None, None, 4)

        def checkRestoreFunction(self, doc, expected):
            spec, ret_literal, note = self.m.parse_func_doc(doc, "foo", "foo", None)
            self.assertEqual(expected, ret_literal, "%r != %r; spec=%r, note=%r" % (expected, ret_literal, spec, note))
            pass

        def testSimpleArrowInt(self):
            doc = "This is foo(bar) -> int"
            self.checkRestoreFunction(doc, "0")

        def testSimpleArrowList(self):
            doc = "This is foo(bar) -> list"
            self.checkRestoreFunction(doc, "[]")

        def testArrowListOf(self):
            doc = "This is foo(bar) -> list of int"
            self.checkRestoreFunction(doc, "[]")

        #    def testArrowTupleOf(self):
        #      doc = "This is foo(bar) -> (a, b,..)"
        #      self.checkRestoreFunction(doc, "()")

        def testSimplePrefixInt(self):
            doc = "This is int foo(bar)"
            self.checkRestoreFunction(doc, "0")

        def testSimplePrefixObject(self):
            doc = "Makes an instance: object foo(bar)"
            self.checkRestoreFunction(doc, "object()")

        if VERSION < (3, 0):
            # TODO: we only support it in 2.x; must update when we do it in 3.x, too
            def testSimpleArrowFile(self):
                doc = "Opens a file: foo(bar) -> file"
                self.checkRestoreFunction(doc, "file('/dev/null')")

        def testUnrelatedPrefix(self):
            doc = """
        Consumes a list of int
        foo(bar)
      """
            self.checkRestoreFunction(doc, None)


###
if __name__ == '__main__':
    unittest.main()
