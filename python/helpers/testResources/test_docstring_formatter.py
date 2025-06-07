# coding=utf-8
from __future__ import unicode_literals, print_function

import errno
import json
import os
import sys
import textwrap

import six
from docstring_formatter import format_fragments
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

    @property
    def fragments_mode(self):
        name_split = self.test_name.split('_')
        return len(name_split) > 1 and name_split[1] == "fragments"

    def _test(self):
        docstring_format = self.docstring_format
        fragments_mode = self.fragments_mode

        if fragments_mode:
            try:
                json_path = os.path.join(self.test_data_root, self.test_name + '.json')
                with open(json_path, encoding='utf-8') as f:
                    docstring_content = f.read()
                input_json = json.loads(docstring_content)
                input_body = input_json['body']
                input_fragments = input_json['fragments']
            except ValueError:
                raise AssertionError("Incorrect format of JSON input")

            actual_json = json.dumps(format_fragments(input_fragments),
                                     ensure_ascii=False, separators=(',', ':'),
                                     sort_keys=True)
            expected_json_path = os.path.join(self.test_data_root,
                                              self.test_name + '_out.json')
            self.assertEqualsToFileContent(actual_json, expected_json_path)
        else:
            try:
                json_path = os.path.join(self.test_data_root, self.test_name + '.txt')
                with open(json_path, encoding='utf-8') as f:
                    docstring_content = f.read()
            except IOError as e:
                if e.errno != errno.ENOENT:
                    raise
                module_path = os.path.join(self.test_data_dir, self.test_name + '.py')
                docstring_content = self.read_docstring_from_module(module_path)
            input_body = docstring_content

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
            raise AssertionError("Unknown docstring type '{}'".format(docstring_format))

        if format_to_rest and not fragments_mode:
            actual_rest = format_to_rest(input_body)
            expected_rest_path = os.path.join(self.test_data_dir,
                                              self.test_name + '.rest')
            self.assertEqualsToFileContent(actual_rest, expected_rest_path)

        actual_html = format_to_html(input_body)
        expected_html_path = os.path.join(self.test_data_dir, self.test_name + '.html')
        self.assertEqualsToFileContent(actual_html, expected_html_path)

    def launch_helper(self, stdin):
        helper_path = os.path.join(_helpers_root, 'docstring_formatter.py')
        args = [sys.executable, helper_path, '--format', self.docstring_format]
        if self.fragments_mode:
            args.append('--fragments')
        result = self.run_process(args,
                                  input=stdin,
                                  env={'PYTHONPATH': docutils_root})
        if result.stderr:
            raise AssertionError("Non-empty stderr for docstring_formatter.py:\n"
                                 "{}".format(result.stderr))
        return result.stdout

    def launch_helper_with_fragments(self):
        json_input_path = os.path.join(self.test_data_root, self.test_name + '.json')
        with open(json_input_path, encoding='utf-8') as f:
            input_json = f.read()
        output = self.launch_helper(input_json)
        expected_json_path = os.path.join(self.test_data_root,
                                          self.test_name + '_out.json')
        self.assertEqualsToFileContent(output, expected_json_path)

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

    def test_google_fragments_simple(self):
        self._test()

    def test_google_fragments_only_multiline_return_section(self):
        self._test()

    def test_rest_fragments_unicode(self):
        self._test()

    def test_numpy_fragments_params(self):
        self._test()

    def test_rest_fragments_only_body(self):
        self._test()

    def test_google_fragments_math(self):
        self._test()

    def test_numpy_fragments_table(self):
        self._test()

    def test_rest_fragments_functional(self):
        self.launch_helper_with_fragments()
