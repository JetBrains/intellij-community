import unittest

from _pydevd_bundle.pydevd_signature import *

class TestSignature(unittest.TestCase):
    def test_type_of_value(self):
        def cmp(type, value):
            self.assertEqual(type, get_type_of_value(value, recursive=True))

        cmp('int', 1)
        cmp('str', 'str')
        cmp('NoneType', None)
        cmp('test_signature.TestSignature', self)
        cmp('List', [])
        cmp('List[int]', [1, 2, 3])
        cmp('Dict', {})
        cmp('Dict[str, int]', {'x':1, 'y':2})
        cmp('Tuple', ())
        cmp('Tuple[int, str]', (1234, '4321'))
        cmp('List[Tuple[Dict[int, str], str]]', [({1:'1'}, 'abc')])
