import os
import unittest

import six
from generator3.constants import ENV_TEST_MODE_FLAG
from testing import HelpersTestCase

python3_only = unittest.skipUnless(six.PY3, 'Python 3 only test')
python2_only = unittest.skipUnless(six.PY2, 'Python 2 only test')


def test_data_dir(name):
    """
    Decorator allowing to customize test data directory for a test.

    The specified name will be used only as the last component of the path
    following the directory corresponding to the test class (by default
    test name itself is used for this purpose).

    Example::

        @test_data_dir('common_test_data')
        def test_scenario():
            ...
    """

    def decorator(f):
        f._test_data_dir = name
        return f

    return decorator


class GeneratorTestCase(HelpersTestCase):
    @classmethod
    def setUpClass(cls):
        super(GeneratorTestCase, cls).setUpClass()
        os.environ[ENV_TEST_MODE_FLAG] = 'True'

    @classmethod
    def tearDownClass(cls):
        os.environ.pop(ENV_TEST_MODE_FLAG)
        super(GeneratorTestCase, cls).tearDownClass()

    @property
    def test_data_root(self):
        return os.path.join(super(GeneratorTestCase, self).test_data_root, 'generator3')
