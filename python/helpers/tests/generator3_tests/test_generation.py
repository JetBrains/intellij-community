import os
import subprocess
import sys
import textwrap
import unittest

import generator3
from generator3_tests import GeneratorTestCase, python3_only, python2_only
from pycharm_generator_utils.constants import (
    CACHE_DIR_NAME,
    ENV_REQUIRED_GEN_VERSION_FILE,
    ENV_STANDALONE_MODE_FLAG,
    ENV_TEST_MODE_FLAG,
    ENV_VERSION,
)
from pycharm_generator_utils.util_methods import mkdir

# Such version implies that skeletons are always regenerated
TEST_GENERATOR_VERSION = '1000.0'


class SkeletonCachingTest(GeneratorTestCase):
    PYTHON_STUBS_DIR = 'python_stubs'
    SDK_SKELETONS_DIR = 'sdk_skeletons'

    def get_test_data_path(self, rel_path):
        return os.path.join(self.test_data_dir, rel_path)

    def run_generator(self, mod_qname=None, mod_path=None, builtins=False, extra_syspath_entry=None, gen_version=None,
                      required_gen_version_file_path=None, extra_env=None, extra_args=None, output_dir=None):
        if output_dir is None:
            output_dir = self.temp_skeletons_dir
        else:
            output_dir = os.path.join(self.temp_dir, output_dir)

        if not extra_syspath_entry:
            extra_syspath_entry = self.test_data_dir

        env = {
            ENV_TEST_MODE_FLAG: 'True',
            ENV_VERSION: gen_version or TEST_GENERATOR_VERSION,
        }
        if required_gen_version_file_path:
            env[ENV_REQUIRED_GEN_VERSION_FILE] = required_gen_version_file_path

        if extra_env:
            env.update(extra_env)

        generator3_path = os.path.abspath(generator3.__file__)
        base, ext = os.path.splitext(generator3_path)
        if ext == '.pyc':
            generator3_path = base + '.py'
        args = [
            sys.executable,
            generator3_path,
            '-d', output_dir,
            '-s', extra_syspath_entry,
        ]

        if extra_args:
            args.extend(extra_args)

        if builtins:
            args.append('-b')
        else:
            args.append(mod_qname)
            if mod_path:
                args.append(mod_path)

        self.log.info('Launching generator3 as: ' + ' '.join(args))
        process = subprocess.Popen(args,
                                   env=env,
                                   stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE,
                                   universal_newlines=True)
        stdout, stderr = process.communicate()
        sys.stdout.write(stdout)
        sys.stderr.write(stderr)
        return process.returncode == 0

    @property
    def temp_skeletons_dir(self):
        return os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR, self.SDK_SKELETONS_DIR)

    @property
    def temp_cache_dir(self):
        return os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR, CACHE_DIR_NAME)

    def test_layout_for_builtin_module(self):
        self.run_generator(mod_qname='_ast')
        self.assertDirLayoutEquals(os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR), """
        cache/
            {hash}/
                _ast.py
        sdk_skeletons/
            _ast.py
        """.format(hash=generator3.module_hash('_ast', None)))

    def test_builtins_generation_mode_stores_all_skeletons_in_same_cache_directory(self):
        self.run_generator(builtins=True)
        builtins_hash = generator3.module_hash('sys', None)
        builtins_cache_dir = os.path.join(self.temp_cache_dir, builtins_hash)
        self.assertTrue(os.path.isdir(builtins_cache_dir))
        builtin_mod_skeletons = os.listdir(builtins_cache_dir)
        self.assertIn('_ast.py', builtin_mod_skeletons)
        self.assertIn('sys.py', builtin_mod_skeletons)

    def test_layout_for_toplevel_physical_module(self):
        mod_path = os.path.join(self.test_data_dir, 'mod.py')
        self.run_generator(mod_qname='mod', mod_path=mod_path)
        self.assertDirLayoutEquals(os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR), """
        cache/
            {hash}/
                mod.py
        sdk_skeletons/
            mod.py
        """.format(hash=generator3.module_hash('mod', mod_path)))

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
        """.format(hash=generator3.module_hash('mod', mod_path)))

    # PY-36884
    def test_pregenerated_skeletons_mode(self):
        self.check_generator_output('mod', mod_path='mod.py', extra_env={
            'IS_PREGENERATED_SKELETONS': '1'
        })

    # PY-36884
    def test_pregenerated_skeletons_mode_for_builtin_module(self):
        self.run_generator('sys', builtins=True, extra_env={
            'IS_PREGENERATED_SKELETONS': '1'
        })
        sys_cached_skeleton_path = os.path.join(self.temp_cache_dir, generator3.module_hash('sys', None), 'sys.py')
        self.assertTrue(os.path.exists(sys_cached_skeleton_path))
        with open(sys_cached_skeleton_path, 'r') as f:
            self.assertTrue('# from (pre-generated)\n' in f.read())

        sys_skeleton_path = os.path.join(self.temp_skeletons_dir, 'sys.py')
        self.assertTrue(os.path.exists(sys_skeleton_path))
        with open(sys_skeleton_path, 'r') as f:
            self.assertTrue('# from (built-in)\n' in f.read())

    def test_skeleton_regenerated_for_changed_module(self):
        self.check_generator_output('mod', mod_path='mod.py', mod_root='versions/v2')

    def test_skeleton_regenerated_for_upgraded_generator_with_explicit_update_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_skeleton_not_regenerated_for_upgraded_generator_without_explicit_version_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_skeleton_not_regenerated_for_upgraded_generator_with_earlier_update_stamp(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True)

    def test_version_stamp_put_in_cache_directory_for_failed_module(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.1', success=False)

    def test_skeleton_regenerated_for_failed_module_on_generator_upgrade(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.2')

    def test_skeleton_not_regenerated_for_failed_module_on_same_generator_version(self):
        self.check_generator_output('failing', mod_path='failing.py', gen_version='0.1', success=False)

    def test_segmentation_fault_handling(self):
        self.check_generator_output('sigsegv', mod_path='sigsegv.py', gen_version='0.1', success=False)

    def test_cache_not_updated_when_sdk_skeleton_is_up_to_date(self):
        # We can't safely updated cache from SDK skeletons (backwards) because of binaries declaring
        # multiple modules. Skeletons for them are scattered across SDK skeletons directory, and we can't
        # collect them reliably.
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.2', custom_required_gen=True,
                                    standalone_mode=True)

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

    def test_cache_skeleton_not_regenerated_when_sdk_skeleton_generation_failed_for_same_version_and_same_binary(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.1', custom_required_gen=True,
                                    standalone_mode=True, success=False)

    def test_cache_skeleton_regenerated_when_sdk_skeleton_generation_failed_for_modified_binary(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.1', custom_required_gen=True)

    @python3_only
    def test_inaccessible_class_attribute_py3(self):
        self.check_generator_output('mod', mod_path='mod.py', success=False)

    @python2_only
    def test_inaccessible_class_attribute_py2(self):
        self.check_generator_output('mod', mod_path='mod.py', success=False)

    def test_binary_declares_multiple_modules(self):
        self.check_generator_output('mod', mod_path='mod.py')

    def test_binary_declares_extra_module_that_fails(self):
        self.check_generator_output('mod', mod_path='mod.py', success=False)

    def test_origin_stamp_in_skeleton_header_is_updated_on_copying(self):
        self.check_generator_output('mod', mod_path='mod.py')

    def test_origin_stamp_for_pregenerated_builtins_is_updated(self):
        mod_hash = generator3.module_hash('_abc', None)
        template = textwrap.dedent("""\
        # encoding: utf-8
        # module _ast
        # from {origin}
        # by generator 1000.0
        """)

        cache_entry_dir = os.path.join(self.temp_cache_dir, mod_hash)
        mkdir(cache_entry_dir)
        with open(os.path.join(cache_entry_dir, '_ast.py'), 'w') as f:
            f.write(template.format(origin='(pre-generated)'))

        self.run_generator('_ast', None, True)
        with open(os.path.join(self.temp_skeletons_dir, '_ast.py')) as f:
            self.assertEquals(template.format(origin='(built-in)'), f.read())

    def test_single_pyexpat_skeletons_layout(self):
        self.run_generator('pyexpat')
        self.assertFalse(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat.py')))
        self.assertTrue(os.path.isdir(os.path.join(self.temp_skeletons_dir, 'pyexpat')))
        self.assertNonEmptyFile(os.path.join(self.temp_skeletons_dir, 'pyexpat', '__init__.py'))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'model.py')))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'errors.py')))

    # TODO figure out why this is not true for some interpreters
    @unittest.skipUnless('pyexpat' in sys.builtin_module_names, "pyexpat must be a built-in module")
    def test_pyexpat_layout_in_builtins(self):
        self.run_generator(builtins=True)
        self.assertFalse(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat.py')))
        self.assertTrue(os.path.isdir(os.path.join(self.temp_skeletons_dir, 'pyexpat')))
        self.assertNonEmptyFile(os.path.join(self.temp_skeletons_dir, 'pyexpat', '__init__.py'))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'model.py')))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'errors.py')))

    @python3_only
    def test_introspecting_submodule_modifies_sys_modules(self):
        self.check_generator_output('mod', 'mod.py')

    # PY-37241
    # Python 2 version of the skeleton differs significantly
    # TODO investigate why
    @python3_only
    def test_non_string_dunder_module(self):
        self.check_generator_output('mod', 'mod.py')

    def check_generator_output(self, mod_name, mod_path=None, mod_root=None,
                               custom_required_gen=False, standalone_mode=False,
                               success=True, **kwargs):
        if custom_required_gen:
            kwargs.setdefault('required_gen_version_file_path',
                              os.path.join(self.test_data_dir, 'required_gen_version'))
        if standalone_mode:
            kwargs.setdefault('extra_env', {})[ENV_STANDALONE_MODE_FLAG] = 'True'

        if not mod_root:
            mod_root = self.test_data_dir
        elif not os.path.isabs(mod_root):
            mod_root = os.path.join(self.test_data_dir, mod_root)

        if mod_path:
            mod_path = os.path.join(mod_root, mod_path)

        with self.comparing_dirs(tmp_subdir=self.PYTHON_STUBS_DIR):
            result = self.run_generator(mod_name, mod_path=mod_path, extra_syspath_entry=mod_root, **kwargs)
            self.assertEqual(success, result)

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
