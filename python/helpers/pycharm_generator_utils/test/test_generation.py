import logging
import os
import subprocess
import sys
import textwrap
import unittest

import generator3
import six
from pycharm_generator_utils.constants import (
    ENV_TEST_MODE_FLAG,
    ENV_VERSION,
    ENV_REQUIRED_GEN_VERSION_FILE,
)
from pycharm_generator_utils.test import GeneratorTestCase

logging.basicConfig(level=logging.DEBUG)

# Such version implies that skeletons are always regenerated
TEST_GENERATOR_VERSION = '1000.0'

_run_generator_in_separate_process = True
_log = logging.getLogger(__name__)


class SkeletonCachingTest(GeneratorTestCase):
    PYTHON_STUBS_DIR = 'python_stubs'
    SDK_SKELETONS_DIR = 'sdk_skeletons'

    def get_test_data_path(self, rel_path):
        return os.path.join(self.test_data_dir, rel_path)

    def run_generator(self, mod_qname, mod_path=None,
                      extra_syspath_entry=None,
                      gen_version=None,
                      required_gen_version_file_path=None):
        output_dir = self.temp_skeletons_dir

        if not extra_syspath_entry:
            extra_syspath_entry = self.test_data_dir

        if not mod_path:
            mod_path = self.imported_module_path(mod_qname, extra_syspath_entry)

        env = {
            ENV_TEST_MODE_FLAG: 'True',
            ENV_VERSION: gen_version or TEST_GENERATOR_VERSION,
        }
        if required_gen_version_file_path:
            env[ENV_REQUIRED_GEN_VERSION_FILE] = required_gen_version_file_path

        if _run_generator_in_separate_process:
            generator3_path = os.path.abspath(generator3.__file__)
            base, ext = os.path.splitext(generator3_path)
            if ext == '.pyc':
                generator3_path = base + '.py'
            args = [
                sys.executable,
                generator3_path,
                '-d', output_dir,
                '-s', extra_syspath_entry,
                mod_qname
            ]
            if mod_path:
                args.append(mod_path)

            _log.info('Launching generator3 as: ' + ' '.join(args))
            subprocess.call(args, env=env)
        else:
            os.environ.update(env)
            sys.path.append(extra_syspath_entry)
            try:
                generator3.process_one(mod_qname, mod_path, mod_qname in sys.builtin_module_names, output_dir)
            except:
                _log.error('Raised inside generator', exc_info=True)
            finally:
                if mod_qname != 'sys':
                    sys.modules.pop(mod_qname, None)
                sys.path.pop()
                for name in env:
                    del os.environ[name]

        return mod_path

    @property
    def temp_skeletons_dir(self):
        return os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR, self.SDK_SKELETONS_DIR)

    @staticmethod
    def imported_module_path(mod_qname, extra_syspath_entry):
        sys.path.insert(0, extra_syspath_entry)
        try:
            return os.path.abspath(getattr(__import__(mod_qname), '__file__'))
        except AttributeError:
            pass
        finally:
            if mod_qname != 'sys':
                sys.modules.pop(mod_qname, None)
            sys.path.pop(0)
        return None

    def test_layout_for_builtin_module(self):
        self.run_generator(mod_qname='_ast')
        self.assertDirLayoutEquals(os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR), """
        cache/
            {hash}/
                _ast.py
        sdk_skeletons/
            _ast.py
        """.format(hash=generator3.builtin_module_hash('_ast')))

    def test_layout_for_toplevel_physical_module(self):
        mod_path = os.path.join(self.test_data_dir, 'mod.py')
        self.run_generator(mod_qname='mod', mod_path=mod_path)
        self.assertDirLayoutEquals(os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR), """
        cache/
            {hash}/
                mod.py
        sdk_skeletons/
            mod.py
        """.format(hash=generator3.physical_module_hash(mod_path)))

    def test_layout_for_physical_module_inside_package(self):
        mod_path = self.get_test_data_path('pkg/subpkg/mod.py')
        self.run_generator(mod_qname='pkg.subpkg.mod', mod_path=mod_path)
        self.assertDirLayoutEquals(os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR), """
        cache/
            {hash}/
                pkg/
                    __init__.py
                    subpkg/
                        __init__.py
                        mod.py
        sdk_skeletons/
            pkg/
                __init__.py
                subpkg/
                    __init__.py
                    mod.py
        """.format(hash=generator3.physical_module_hash(mod_path)))

    def test_skeleton_regenerated_for_changed_module(self):
        self.check_generator_output('mod', mod_path='mod.py', mod_root='versions/v2')

    def test_skeleton_regenerated_for_upgraded_generator_with_explicit_update_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_skeleton_not_regenerated_for_upgraded_generator_without_explicit_version_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_skeleton_not_regenerated_for_upgraded_generator_with_earlier_update_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_version_stamp_put_in_cache_directory_for_failed_module(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.1')

    def test_skeleton_regenerated_for_failed_module_on_generator_upgrade(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.2')

    def test_skeleton_not_regenerated_for_failed_module_on_same_generator_version(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.1')

    @unittest.skipIf(not _run_generator_in_separate_process,
                     'Importing module causing SIGSEGV cannot be done in the same interpreter')
    def test_segmentation_fault_handling(self):
        self.check_generator_output('sigsegv', mod_path='sigsegv.py', gen_version='0.1')

    def test_cache_not_updated_when_sdk_skeleton_is_up_to_date(self):
        # We can't safely updated cache from SDK skeletons (backwards) because of binaries declaring
        # multiple modules. Skeletons for them are scattered across SDK skeletons directory, and we can't
        # collect them reliably.
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_cache_skeleton_reused_when_sdk_skeleton_is_missing(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_cache_skeleton_reused_when_sdk_skeleton_is_outdated(self):
        # SDK skeleton version is 0.1, cache skeleton version is 0.2, skeletons need to be updated starting from 0.2
        # New skeleton would have version 0.3, but we shouldn't generate any.
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.3', custom_required_gen=True)

    def test_cache_skeleton_generated_and_reused_when_sdk_skeleton_is_outdated(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_cache_skeleton_reused_when_sdk_skeleton_generation_failed(self):
        # Generation failed for version 0.1, cache skeleton version is 0.2
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.3', custom_required_gen=True)

    def test_cache_skeleton_not_regenerated_when_sdk_skeleton_generation_failed_for_same_version(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.1', custom_required_gen=True)

    @unittest.skipUnless(six.PY3, "Python 3 version of the test")
    def test_inaccessible_class_attribute_py3(self):
        self.check_generator_output('mod', mod_path='mod.py')

    @unittest.skipUnless(six.PY2, "Python 2 version of the test")
    def test_inaccessible_class_attribute_py2(self):
        self.check_generator_output('mod', mod_path='mod.py')

    def test_binary_declares_multiple_modules(self):
        self.check_generator_output('mod', mod_path='mod.py')

    def test_binary_declares_extra_module_that_fails(self):
        self.check_generator_output('mod', mod_path='mod.py')

    def check_generator_output(self, mod_name, mod_path=None, mod_root=None, custom_required_gen=False, **kwargs):
        if custom_required_gen:
            kwargs.setdefault('required_gen_version_file_path',
                              os.path.join(self.test_data_dir, 'required_gen_version'))

        if not mod_root:
            mod_root = self.test_data_dir
        elif not os.path.isabs(mod_root):
            mod_root = os.path.join(self.test_data_dir, mod_root)

        if mod_path:
            mod_path = os.path.join(mod_root, mod_path)

        with self.comparing_dirs(tmp_subdir=self.PYTHON_STUBS_DIR):
            self.run_generator(mod_name, mod_path=mod_path, extra_syspath_entry=mod_root, **kwargs)

    def assertDirLayoutEquals(self, dir_path, expected_layout):
        def format_dir(dir_path, indent=''):
            for child_name in sorted(os.listdir(dir_path)):
                child_path = os.path.join(dir_path, child_name)
                if os.path.isdir(child_path):
                    yield indent + child_name + '/'
                    for line in format_dir(child_path, indent + '    '):
                        yield line
                else:
                    yield indent + child_name

        formatted_dir_tree = '\n'.join(format_dir(dir_path))
        expected = textwrap.dedent(expected_layout).strip()
        actual = formatted_dir_tree.strip()
        self.assertMultiLineEqual(expected, actual)
