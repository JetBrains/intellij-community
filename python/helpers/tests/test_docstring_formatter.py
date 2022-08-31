# coding=utf-8
from __future__ import unicode_literals, print_function

import errno
import os
import sys
import textwrap

import six
from sphinxcontrib.napoleon import GoogleDocstring, NumpyDocstring
from testing import HelpersTestCase, _helpers_root

if six.PY2:
    from io import open

    docutils_root = os.path.join(_helpers_root, 'py2only')
else:
    docutils_root = os.path.join(_helpers_root, 'py3only')


class DocstringFormatterTest(HelpersTestCase):
    @classmethod
    def setUpClass(cls):
        super(DocstringFormatterTest, cls).setUpClass()
        sys.path.append(docutils_root)

    @classmethod
    def tearDownClass(cls):
        sys.path.remove(docutils_root)
        super(DocstringFormatterTest, cls).tearDownClass()

    @property
    def test_data_root(self):
        orig_test_data_root = super(DocstringFormatterTest, self).test_data_root
        return os.path.join(orig_test_data_root, 'docstrings')

    @property
    def test_data_dir(self):
        return self.test_data_root

    @property
    def docstring_format(self):
        return self.test_name.split('_')[0]

    def _test(self):
        try:
            docstring_path = os.path.join(self.test_data_root, self.test_name + '.txt')
            with open(docstring_path, encoding='utf-8') as f:
                docstring_content = f.read()
        except IOError as e:
            if e.errno != errno.ENOENT:
                raise
            module_path = os.path.join(self.test_data_dir, self.test_name + '.py')
            docstring_content = self.read_docstring_from_module(module_path)
        docstring_format = self.docstring_format
        if docstring_format == 'google':
            from docstring_formatter import format_google as format_to_html
            format_to_rest = lambda x: six.text_type(GoogleDocstring(x))  # noqa
        elif docstring_format == 'numpy':
            from docstring_formatter import format_numpy as format_to_html
            format_to_rest = lambda x: six.text_type(NumpyDocstring(x))  # noqa
        elif docstring_format == 'rest':
            from docstring_formatter import format_rest as format_to_html
            format_to_rest = None
        else:
            raise AssertionError("Unknown docstring type '{}'".format(self.test_name))

        if format_to_rest:
            actual_rest = format_to_rest(docstring_content)
            expected_rest_path = os.path.join(self.test_data_dir,
                                              self.test_name + '.rest')
            self.assertEqualsToFileContent(actual_rest, expected_rest_path)

        actual_html = format_to_html(docstring_content)
        expected_html_path = os.path.join(self.test_data_dir, self.test_name + '.html')
        self.assertEqualsToFileContent(actual_html, expected_html_path)

    def launch_helper(self, stdin):
        helper_path = os.path.join(_helpers_root, 'docstring_formatter.py')
        args = [sys.executable, helper_path, self.docstring_format]
        result = self.run_process(args,
                                  input=stdin,
                                  env={'PYTHONPATH': docutils_root})
        if result.stderr:
            raise AssertionError("Non-empty stderr for docstring_formatter.py:\n"
                                 "{}".format(result.stderr))
        return result.stdout

    @staticmethod
    def read_docstring_from_module(module_path):
        with open(module_path, encoding='utf-8') as f:
            compiled = compile(f.read(), 'sample.py', mode='exec')
            top_level = {}
            six.exec_(compiled, top_level)
            docstring_content = textwrap.dedent(top_level['func'].__doc__)
        return docstring_content

    def test_google_sections(self):
        self._test()

    def test_numpy_vararg(self):
        self._test()

    def test_numpy_no_summary(self):
        self._test()

    def test_rest_type_in_backticks(self):
        self._test()

    def test_rest_gimpfu_docstring(self):
        self._test()

    def test_unicode(self):
        self.launch_helper(stdin='哈哈')

    def test_numpy_ZD846170(self):
        self._test()

    def test_rest_bad_rtype_annotation(self):
        self._test()

    def test_rest_rtype_type_without_return_param(self):
        self._test()

    def test_rest_returns_and_rtype_combined(self):
        self._test()

    def test_rest_simple(self):
        self._test()

    def test_rest_no_empty_line_between_text_and_param(self):
        self._test()

    def test_rest_no_empty_line_between_math_and_param(self):
        self._test()

    def test_google_no_empty_line_between_text_and_seealso(self):
        self._test()

    def test_google_no_empty_line_between_text_and_attributes(self):
        self._test()

    def test_numpy_no_empty_line_between_text_and_param(self):
        self._test()