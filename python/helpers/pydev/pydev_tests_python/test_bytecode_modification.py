import dis
import sys
import unittest
from io import StringIO
from types import CodeType
import pytest

from _pydevd_frame_eval.pydevd_modify_bytecode import insert_code
from opcode import EXTENDED_ARG

TRACE_MESSAGE = "Trace called"

def tracing():
    print(TRACE_MESSAGE)


def call_tracing():
    return tracing()


def bar(a, b):
    return a + b


IS_PY36_OR_GREATER = sys.version_info > (3, 6)
IS_PY37_OR_GREATER = sys.version_info > (3, 7)

LOAD_OPCODES = [dis.opmap[x] for x in ('LOAD_ATTR', 'LOAD_CONST', 'LOAD_FAST', 'LOAD_GLOBAL', 'LOAD_NAME')]

if IS_PY37_OR_GREATER:
    LOAD_OPCODES.append(dis.opmap['LOAD_METHOD'])


@pytest.mark.skipif(not IS_PY36_OR_GREATER, reason='Test requires Python 3.6 or greater')
class TestInsertCode(unittest.TestCase):
    lines_separator = "---Line tested---"

    def check_insert_every_line(self, func_to_modify, func_to_insert, number_of_lines):
        first_line = func_to_modify.__code__.co_firstlineno + 1
        last_line = first_line + number_of_lines
        for i in range(first_line, last_line):
            self.check_insert_to_line_with_exec(func_to_modify, func_to_insert, i)
            print(self.lines_separator)

    def check_insert_to_line_with_exec(self, func_to_modify, func_to_insert, line_number):
        code_orig = func_to_modify.__code__
        code_to_insert = func_to_insert.__code__
        success, result = insert_code(code_orig, code_to_insert, line_number)
        exec(result)
        output = sys.stdout.getvalue().strip().split(self.lines_separator)[-1]
        self.assertTrue(TRACE_MESSAGE in output)

    def check_insert_to_line_by_symbols(self, func_to_modify, func_to_insert, line_number, code_for_check):
        code_orig = func_to_modify.__code__
        code_to_insert = func_to_insert.__code__
        success, result = insert_code(code_orig, code_to_insert, line_number)
        self.compare_bytes_sequence(result, code_for_check, len(code_to_insert.co_code))

    def compare_bytes_sequence(self, code1, code2, inserted_code_size):
        """
        Compare code after modification and the real code
        Since we add POP_JUMP_IF_TRUE instruction, we can't compare modified code and the real code. That's why we
        allow some inaccuracies while code comparison
        :param code1: result code after modification
        :param code2: a real code for checking
        :param inserted_code_size: size of inserted code
        """
        seq1 = [(offset, op, arg) for offset, op, arg in dis._unpack_opargs(list(code1.co_code))]
        seq2 = [(offset, op, arg) for offset, op, arg in dis._unpack_opargs(list(code2.co_code))]
        assert len(seq1) == len(seq2), "Bytes sequences have different lengths %s != %s" % (len(seq1), len(seq2))
        for i in range(len(seq1)):
            of, op1, arg1 = seq1[i]
            _, op2, arg2 = seq2[i]
            if op1 != op2:
                if op1 == 115 and op2 == 1:
                    # it's ok, because we added POP_JUMP_IF_TRUE manually, but it's POP_TOP in the real code
                    # inserted code - 2 (removed return instruction) - real code inserted
                    # Jump should be done to the beginning of inserted fragment
                    self.assertEqual(arg1, of - (inserted_code_size - 2))
                    continue
                elif op1 == EXTENDED_ARG and op2 == 12:
                    # we added a real UNARY_NOT to balance EXTENDED_ARG added by new jump instruction
                    # i.e. inserted code size was increased as well
                    inserted_code_size += 2
                    continue

            self.assertEqual(op1, op2, "Different operators at offset {}".format(of))

            if op1 in LOAD_OPCODES:
                # When comparing arguments of the load operations we shouldn't rely only on arguments themselves,
                # because their order may change. It's better to compare the actual values instead.
                self.comapre_load_args(of, code1, code2, op1, arg1, arg2)
            elif arg1 != arg2:
                self.assertEquals(arg1, arg2, "Different arguments at offset {}".format(of))

    def comapre_load_args(self, offset, code1, code2, opcode, arg1, arg2):
        err_msg = "Different arguments at offset {}".format(offset)
        if opcode == dis.opmap['LOAD_ATTR']:
            assert code1.co_names[arg1] == code2.co_names[arg2], err_msg
        elif opcode == dis.opmap['LOAD_CONST']:
            const1, const2 = code1.co_consts[arg1], code2.co_consts[arg2]
            if isinstance(const1, CodeType) and isinstance(const2, CodeType):
                if const1.co_filename != const2.co_filename:
                    self.fail(err_msg)
        elif opcode == dis.opmap['LOAD_FAST']:
            assert code1.co_varnames[arg1] == code2.co_varnames[arg2], err_msg
        elif opcode == dis.opmap['LOAD_GLOBAL']:
            assert code1.co_names[arg1] == code2.co_names[arg2], err_msg
        elif opcode == dis.opmap['LOAD_NAME']:
            assert code1.co_names[arg1] == code2.co_names[arg2], err_msg

    def test_line(self):

        def foo():
            global global_loaded
            global_loaded()

        def method():
            a = 10
            b = 20
            c = 20

        success, result = insert_code(method.__code__, foo.__code__, method.__code__.co_firstlineno + 1)
        assert success
        assert list(result.co_lnotab) == [10, 1, 4, 1, 4, 1]

        success, result = insert_code(method.__code__, foo.__code__, method.__code__.co_firstlineno + 2)
        assert success
        assert list(result.co_lnotab) == [0, 1, 14, 1, 4, 1]

        success, result = insert_code(method.__code__, foo.__code__, method.__code__.co_firstlineno + 3)
        assert success
        assert list(result.co_lnotab) == [0, 1, 4, 1, 14, 1]

    def test_assignment(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                a = 1
                b = 2
                c = 3

            self.check_insert_every_line(original, tracing, 3)

        finally:
            sys.stdout = self.original_stdout

    def test_for_loop(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                n = 3
                sum = 0
                for i in range(n):
                    sum += i
                return sum

            self.check_insert_every_line(original, tracing, 5)

        finally:
            sys.stdout = self.original_stdout

    def test_if(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                if True:
                    a = 1
                else:
                    a = 0
                print(a)

            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 2)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 5)

        finally:
            sys.stdout = self.original_stdout

    def test_else(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                if False:
                    a = 1
                else:
                    a = 0
                print(a)

            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 4)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 5)

        finally:
            sys.stdout = self.original_stdout

    def test_for_else(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                sum = 0
                for i in range(3):
                    sum += i
                else:
                    print(sum)

            def check_line_1():
                tracing()
                sum = 0
                for i in range(3):
                    sum += i
                else:
                    print(sum)

            def check_line_3():
                sum = 0
                for i in range(3):
                    tracing()
                    sum += i
                else:
                    print(sum)

            def check_line_5():
                sum = 0
                for i in range(3):
                    sum += i
                else:
                    tracing()
                    print(sum)

            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 1)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 3)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 5)

            sys.stdout = self.original_stdout
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 1,
                                                 check_line_1.__code__)
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 3,
                                                 check_line_3.__code__)
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 5,
                                                 check_line_5.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_elif(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                a = 5
                b = 0
                if a < 0:
                    print("a < 0")
                elif a < 3:
                    print("a < 3")
                else:
                    print("a >= 3")
                    b = a
                return b

            def check_line_1():
                tracing()
                a = 5
                b = 0
                if a < 0:
                    print("a < 0")
                elif a < 3:
                    print("a < 3")
                else:
                    print("a >= 3")
                    b = a
                return b

            def check_line_8():
                a = 5
                b = 0
                if a < 0:
                    print("a < 0")
                elif a < 3:
                    print("a < 3")
                else:
                    tracing()
                    print("a >= 3")
                    b = a
                return b

            def check_line_9():
                a = 5
                b = 0
                if a < 0:
                    print("a < 0")
                elif a < 3:
                    print("a < 3")
                else:
                    print("a >= 3")
                    tracing()
                    b = a
                return b

            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 1)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 2)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 8)
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 9)

            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 1,
                                                 check_line_1.__code__)
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 8,
                                                 check_line_8.__code__)
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 9,
                                                 check_line_9.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_call_other_function(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def original():
                a = 1
                b = 3
                c = bar(a, b)
                return c

            def check_line_3():
                a = 1
                b = 3
                tracing()
                c = bar(a, b)
                return c

            def check_line_4():
                a = 1
                b = 3
                c = bar(a, b)
                tracing()
                return c

            self.check_insert_every_line(original, tracing, 4)
            sys.stdout = self.original_stdout

            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 3,
                                                 check_line_3.__code__)
            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 4,
                                                 check_line_4.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_class_method(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            class A(object):
                @staticmethod
                def foo():
                    print("i'm in foo")

                @staticmethod
                def check_line_2():
                    tracing()
                    print("i'm in foo")

            original = A.foo
            self.check_insert_to_line_with_exec(original, tracing, original.__code__.co_firstlineno + 2)

            self.check_insert_to_line_by_symbols(original, call_tracing, original.__code__.co_firstlineno + 2,
                                                 A.check_line_2.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_offset_overflow(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def foo():
                a = 1
                b = 2  # breakpoint
                c = 3
                a1 = 1 if a > 1 else 2
                a2 = 1 if a > 1 else 2
                a3 = 1 if a > 1 else 2
                a4 = 1 if a > 1 else 2
                a5 = 1 if a > 1 else 2
                a6 = 1 if a > 1 else 2
                a7 = 1 if a > 1 else 2
                a8 = 1 if a > 1 else 2
                a9 = 1 if a > 1 else 2
                a10 = 1 if a > 1 else 2
                a11 = 1 if a > 1 else 2
                a12 = 1 if a > 1 else 2
                a13 = 1 if a > 1 else 2

                for i in range(1):
                    if a > 0:
                        print("111")
                        # a = 1
                    else:
                        print("222")
                return b

            def check_line_2():
                a = 1
                tracing()
                b = 2
                c = 3
                a1 = 1 if a > 1 else 2
                a2 = 1 if a > 1 else 2
                a3 = 1 if a > 1 else 2
                a4 = 1 if a > 1 else 2
                a5 = 1 if a > 1 else 2
                a6 = 1 if a > 1 else 2
                a7 = 1 if a > 1 else 2
                a8 = 1 if a > 1 else 2
                a9 = 1 if a > 1 else 2
                a10 = 1 if a > 1 else 2
                a11 = 1 if a > 1 else 2
                a12 = 1 if a > 1 else 2
                a13 = 1 if a > 1 else 2

                for i in range(1):
                    if a > 0:
                        print("111")
                        # a = 1
                    else:
                        print("222")
                return b

            self.check_insert_to_line_with_exec(foo, tracing, foo.__code__.co_firstlineno + 2)

            self.check_insert_to_line_by_symbols(foo, call_tracing, foo.__code__.co_firstlineno + 2,
                                                 check_line_2.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_long_lines(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def foo():
                a = 1
                b = 1 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                c = 1 if b > 1 else 2 if b > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                d = 1 if c > 1 else 2 if c > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                e = d + 1
                return e

            def check_line_2():
                a = 1
                tracing()
                b = 1 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                c = 1 if b > 1 else 2 if b > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                d = 1 if c > 1 else 2 if c > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23 if a > 1 else 2 if a > 0 else 3 if a > 4 else 23
                e = d + 1
                return e

            self.check_insert_to_line_with_exec(foo, tracing, foo.__code__.co_firstlineno + 2)
            sys.stdout = self.original_stdout

            self.check_insert_to_line_by_symbols(foo, call_tracing, foo.__code__.co_firstlineno + 2,
                                                 check_line_2.__code__)

        finally:
            sys.stdout = self.original_stdout

    def test_many_names(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            from pydev_tests_python.resources._bytecode_many_names_example import foo
            self.check_insert_to_line_with_exec(foo, tracing, foo.__code__.co_firstlineno + 2)

        finally:
            sys.stdout = self.original_stdout

    def test_extended_arg_overflow(self):

        from pydev_tests_python.resources._bytecode_overflow_example import Dummy, DummyTracing
        self.check_insert_to_line_by_symbols(Dummy.fun, call_tracing, Dummy.fun.__code__.co_firstlineno + 3,
                                             DummyTracing.fun.__code__)

    def test_double_extended_arg(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()

        try:
            def foo():
                a = 1
                b = 2
                if b > 0:
                    d = a + b
                    d += 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                a = a + 1
                return a

            def foo_check():
                a = 1
                b = 2
                tracing()
                if b > 0:
                    d = a + b
                    d += 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                a = a + 1
                return a

            def foo_check_2():
                a = 1
                b = 2
                if b > 0:
                    d = a + b
                    d += 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    not tracing() #  add 'not' to balance EXTENDED_ARG when jumping
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                    b = b - 1 if a > 0 else b + 1
                a = a + 1
                return a

            self.check_insert_to_line_with_exec(foo, tracing, foo.__code__.co_firstlineno + 2)
            sys.stdout = self.original_stdout

            self.check_insert_to_line_by_symbols(foo, call_tracing, foo.__code__.co_firstlineno + 3,
                                                 foo_check.__code__)

            self.check_insert_to_line_by_symbols(foo, call_tracing, foo.__code__.co_firstlineno + 21,
                                                 foo_check_2.__code__)


        finally:
            sys.stdout = self.original_stdout
