# encoding: utf-8
"""
Tests basic things that generator3 consists of.
NOTE: does not work in Jython 2.2 or IronPython 1.x, because pyparsing does not.
"""

import unittest
from generator3 import *

M = ModuleRedeclarator

class TestRestoreFuncByDocComment(unittest.TestCase):
  """
  Tries to restore function signatures by doc strings.
  """

  def setUp(self):
    self.m = ModuleRedeclarator(None, None)

  def testTrivial(self):
    result, note = self.m.parseFuncDoc("blah f(a, b, c) ololo", "f", None)
    self.assertEquals(result, "f(a, b, c)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testTrivialNested(self):
    result, note = self.m.parseFuncDoc("blah f(a, (b, c), d) ololo", "f", None)
    self.assertEquals(result, "f(a, (b, c), d)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testWithDefault(self):
    result, note = self.m.parseFuncDoc("blah f(a, b, c=1) ololo", "f", None)
    self.assertEquals(result, "f(a, b, c=1)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testNestedWithDefault(self):
    result, note = self.m.parseFuncDoc("blah f(a, (b1, b2), c=1) ololo", "f", None)
    self.assertEquals(result, "f(a, (b1, b2), c=1)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testAbstractDefault(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah f(a, b=obscuredefault) ololo', "f", None)
    self.assertEquals(result, "f(a, b=None)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testWithReserved(self):
    result, note = self.m.parseFuncDoc("blah f(class, object, def) ololo", "f", None)
    self.assertEquals(result, "f(p_class, p_object, p_def)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testWithReservedOpt(self):
    result, note = self.m.parseFuncDoc("blah f(foo, bar[, def]) ololo", "f", None)
    self.assertEquals(result, "f(foo, bar, p_def=None)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testPseudoNested(self):
    result, note = self.m.parseFuncDoc("blah f(a, (b1, b2, ...)) ololo", "f", None)
    self.assertEquals(result, "f(a, b_tuple)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testImportLike(self):
    # __import__
    result, note = self.m.parseFuncDoc("blah f(name, globals={}, locals={}, fromlist=[], level=-1) ololo", "f", None)
    self.assertEquals(result, "f(name, globals={}, locals={}, fromlist=[], level=-1)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testOptionalBracket(self):
    # reduce
    result, note = self.m.parseFuncDoc("blah f(function, sequence[, initial]) ololo", "f", None)
    self.assertEquals(result, "f(function, sequence, initial=None)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testWithMore(self):
    result, note = self.m.parseFuncDoc("blah f(foo [, bar1, bar2, ...]) ololo", "f", None)
    self.assertEquals(result, "f(foo, *bar)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testNestedOptionals(self):
    result, note = self.m.parseFuncDoc("blah f(foo [, bar1 [, bar2]]) ololo", "f", None)
    self.assertEquals(result, "f(foo, bar1=None, bar2=None)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testInnerTuple(self):
    result, note = self.m.parseFuncDoc("blah load_module(name, file, filename, (suffix, mode, type)) ololo", "load_module", None)
    self.assertEquals(result, "load_module(name, file, filename, (suffix, mode, type))")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testIncorrectInnerTuple(self):
    result, note = self.m.parseFuncDoc("blah f(a, (b=1, c=2)) ololo", "f", None)
    self.assertEquals(result, "f(a, p_b)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testNestedOnly(self):
    result, note = self.m.parseFuncDoc("blah f((foo, bar, baz)) ololo", "f", None)
    self.assertEquals(result, "f((foo, bar, baz))")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testTwoPseudoNested(self):
    result, note = self.m.parseFuncDoc("blah f((a1, a2, ...), (b1, b2,..)) ololo", "f", None)
    self.assertEquals(result, "f(a_tuple, b_tuple)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testTwoPseudoNestedWithLead(self):
    result, note = self.m.parseFuncDoc("blah f(x, (a1, a2, ...), (b1, b2,..)) ololo", "f", None)
    self.assertEquals(result, "f(x, a_tuple, b_tuple)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testPseudoNestedRange(self):
    result, note = self.m.parseFuncDoc("blah f((a1, ..., an), b) ololo", "f", None)
    self.assertEquals(result, "f(a_tuple, b)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testIncorrectList(self):
    result, note = self.m.parseFuncDoc("blah f(x, y, 3, $) ololo", "f", None)
    self.assertEquals(result, "f(x, y, *args, **kwargs)")
    self.assertEquals(note, M.SIG_DOC_UNRELIABLY)

  def testIncorrectStarredList(self):
    result, note = self.m.parseFuncDoc("blah f(x, *y, 3, $) ololo", "f", None)
    self.assertEquals(result, "f(x, *y, **kwargs)")
    self.assertEquals(note, M.SIG_DOC_UNRELIABLY)

  def testClashingNames(self):
    result, note = self.m.parseFuncDoc("blah f(x, y, (x, y), z) ololo", "f", None)
    self.assertEquals(result, "f(x, y, (x_1, y_1), z)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testQuotedParam(self):
    # like __delattr__
    result, note = self.m.parseFuncDoc("blah getattr('name') ololo", "getattr", None)
    self.assertEquals(result, "getattr(name)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testQuotedParam2(self):
    # like __delattr__, too
    result, note = self.m.parseFuncDoc('blah getattr("name") ololo', "getattr", None)
    self.assertEquals(result, "getattr(name)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testOptionalTripleDot(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah f(foo, ...) ololo', "f", None)
    self.assertEquals(result, "f(foo, *more)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testUnderscoredName(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah f(foo_one, _bar_two) ololo', "f", None)
    self.assertEquals(result, "f(foo_one, _bar_two)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testDashedName(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah f(something-else, for-a-change) ololo', "f", None)
    self.assertEquals(result, "f(something_else, for_a_change)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testSpacedDefault(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah f(a, b = 1) ololo', "f", None)
    self.assertEquals(result, "f(a, b=1)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testSpacedName(self):
    # like new(S, ...)
    result, note = self.m.parseFuncDoc('blah femme(skirt or pants) ololo', "femme", None)
    self.assertEquals(result, "femme(skirt_or_pants)")
    self.assertEquals(note, M.SIG_DOC_NOTE)


class TestRestoreMethodByDocComment(unittest.TestCase):
  "Restoring with a class name set"

  def setUp(self):
    self.m = ModuleRedeclarator(None, None)

  def testPlainMethod(self):
    result, note = self.m.parseFuncDoc("blah f(self, foo, bar) ololo", "f", "SomeClass")
    self.assertEquals(result, "f(self, foo, bar)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testInsertSelf(self):
    result, note = self.m.parseFuncDoc("blah f(foo, bar) ololo", "f", "SomeClass")
    self.assertEquals(result, "f(self, foo, bar)")
    self.assertEquals(note, M.SIG_DOC_NOTE)


class TestAnnotatedParameters(unittest.TestCase):
  "f(foo: int) and friends; in doc comments, happen in 2.x world, too."

  def setUp(self):
    self.m = ModuleRedeclarator(None, None)

  def testMixed(self):
    result, note = self.m.parseFuncDoc('blah f(i: int, foo) ololo', "f", None)
    self.assertEquals(result, "f(i, foo)")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testNested(self):
    result, note = self.m.parseFuncDoc('blah f(i: int, (foo: bar, boo: Decimal)) ololo', "f", None)
    self.assertEquals(result, "f(i, (foo, boo))")
    self.assertEquals(note, M.SIG_DOC_NOTE)

  def testSpaced(self):
    result, note = self.m.parseFuncDoc('blah f(i: int, j :int, k : int) ololo', "f", None)
    self.assertEquals(result, "f(i, j, k)")
    self.assertEquals(note, M.SIG_DOC_NOTE)


class TestInspect(unittest.TestCase):
  "See that inspect actually works if needed"
  def setUp(self):
    self.m = ModuleRedeclarator(None, None)
    
  def testSimple(self):
    def target(a,b,c=1, *d, **e):
      pass
    result = self.m.restoreByInspect(target)
    self.assertEquals(result, "(a, b, c=1, *d, **e)")
    
  def testNested(self):
    def target(a, (b, c), d, e=1):
      pass
    result = self.m.restoreByInspect(target)
    self.assertEquals(result, "(a, (b, c), d, e=1)")
    


class TestSpecialCases(unittest.TestCase):
  "Tests cases where predefined overrides kick in"

  def setUp(self):
    import sys
    major_ver = sys.version_info[0]
    if major_ver > 2:
      import builtins as the_builtins
      self.builtins_name = the_builtins.__name__
    else:
      import __builtin__ as the_builtins
      self.builtins_name = the_builtins.__name__
    self.m = ModuleRedeclarator(the_builtins, None, doing_builtins=True)

  def _testBuiltinFuncName(self, func_name, expected):
    class_name = None
    self.assertTrue(self.m.isPredefinedBuiltin(self.builtins_name, class_name, func_name))
    result, note = self.m.restorePredefinedBuiltin(class_name, func_name)
    self.assertEquals(result, func_name + expected)
    self.assertEquals(note, "known special case of " + func_name)

  def testZip(self):
    self._testBuiltinFuncName("zip", "(seq1, seq2, *more_seqs)")

  def testRange(self):
    self._testBuiltinFuncName("range", "(start=None, stop=None, step=None)")

  def testFilter(self):
    self._testBuiltinFuncName("filter", "(function_or_none, sequence)")
    

    

###
if __name__ == '__main__':
  unittest.main()
