from __future__ import print_function, unicode_literals

import errno
import os
import sys
import textwrap
import unittest

import generator3.core
import json
from generator3.constants import (
    CACHE_DIR_NAME,
    ENV_REQUIRED_GEN_VERSION_FILE,
    ENV_TEST_MODE_FLAG,
    ENV_VERSION,
    STATE_FILE_NAME)
from generator3.core import file_modification_timestamp
from generator3.util_methods import ignored_os_errors, mkdir
from generator3_tests import GeneratorTestCase, python2_only, python3_only, test_data_dir

# Such version implies that skeletons are always regenerated
TEST_GENERATOR_VERSION = '1000.0'
ARGPARSE_ERROR_CODE = 2
_helpers_root = os.path.dirname(os.path.dirname(os.path.abspath(generator3.__file__)))


class GeneratorResult(object):
    def __init__(self, process_result, skeletons_dir):
        self.skeletons_dir = skeletons_dir
        # self.__dict__.update(process_result._as_dict())
        self.exit_code = process_result.exit_code
        self.stdout = process_result.stdout
        self.stderr = process_result.stderr

    def control_messages(self, type_=None):
        result = []
        for line in self.stdout.splitlines(False):  # type: str
            if line.startswith('{'):
                message = json.loads(line)
                if type_ is None or message['type'] == type_:
                    result.append(message)
        return result

    @property
    def state_json(self):
        with ignored_os_errors(errno.ENOENT):
            with open(os.path.join(self.skeletons_dir, STATE_FILE_NAME)) as f:
                return json.load(f)
        # noinspection PyUnreachableCode
        return None


class FunctionalGeneratorTestCase(GeneratorTestCase):
    SDK_SKELETONS_DIR = 'sdk_skeletons'
    PYTHON_STUBS_DIR = 'python_stubs'

    default_generator_extra_args = []
    default_generator_extra_syspath = []

    @property
    def temp_python_stubs_root(self):
        return os.path.join(self.temp_dir, self.PYTHON_STUBS_DIR)

    @property
    def temp_skeletons_dir(self):
        return os.path.join(self.temp_python_stubs_root, self.SDK_SKELETONS_DIR)

    @property
    def temp_cache_dir(self):
        return os.path.join(self.temp_python_stubs_root, CACHE_DIR_NAME)

    def tearDownForFailedTest(self):
        print("\nLaunched processes stdout:\n" + self.process_stdout.getvalue() + '-' * 80)
        print("\nLaunched processes stderr:\n" + self.process_stderr.getvalue() + '-' * 80)

    def run_generator(self, mod_qname=None,
                      mod_path=None,
                      extra_syspath=None,
                      gen_version=None,
                      required_gen_version_file_path=None,
                      extra_env=None,
                      extra_args=None,
                      output_dir=None,
                      input=None):
        if output_dir is None:
            output_dir = self.temp_skeletons_dir
        else:
            output_dir = os.path.join(self.temp_dir, output_dir)

        if extra_syspath is None:
            extra_syspath = self.default_generator_extra_syspath
        if not extra_syspath:
            extra_syspath = [self.test_data_dir]

        extra_syspath = [p if os.path.isabs(p) else self.resolve_in_test_data(p)
                         for p in extra_syspath]

        if mod_path and not os.path.isabs(mod_path):
            mod_path = os.path.join(extra_syspath[0], mod_path)

        env = {
            ENV_TEST_MODE_FLAG: 'True',
            ENV_VERSION: gen_version or TEST_GENERATOR_VERSION,
            'PYTHONPATH': _helpers_root,
        }
        if required_gen_version_file_path:
            env[ENV_REQUIRED_GEN_VERSION_FILE] = required_gen_version_file_path

        if extra_env:
            env.update(extra_env)

        args = [
            sys.executable,
            '-m',
            'generator3',
            '-d', output_dir,
            '-s', os.pathsep.join(extra_syspath),
        ]

        if extra_args is None:
            extra_args = self.default_generator_extra_args

        if extra_args:
            args.extend(extra_args)

        elif mod_qname:
            args.append(mod_qname)
            if mod_path:
                args.append(mod_path)

        self.log.info('Launching generator3 as: ' + ' '.join(args))
        result = self.run_process(args, input=input, env=env)
        return GeneratorResult(result, skeletons_dir=output_dir)

    def check_generator_output(self, mod_name=None, mod_path=None, custom_required_gen=False, success=True, **kwargs):
        if custom_required_gen:
            kwargs.setdefault('required_gen_version_file_path',
                              self.resolve_in_test_data('required_gen_version'))

        with self.comparing_dirs(tmp_subdir=self.PYTHON_STUBS_DIR):
            result = self.run_generator(mod_name, mod_path=mod_path, **kwargs)
            self.assertEqual(success, result.exit_code == 0)


class SkeletonGenerationTest(FunctionalGeneratorTestCase):
    def test_layout_for_builtin_module(self):
        self.run_generator(mod_qname='_ast')
        self.assertDirLayoutEquals(self.temp_python_stubs_root, """
        cache/
            {hash}/
                _ast.py
        sdk_skeletons/
            _ast.py
        """.format(hash=generator3.core.module_hash('_ast', None)))

    def test_layout_for_toplevel_physical_module(self):
        mod_path = self.resolve_in_test_data('mod.py')
        self.run_generator(mod_qname='mod', mod_path=mod_path)
        self.assertDirLayoutEquals(self.temp_python_stubs_root, """
        cache/
            {hash}/
                mod.py
        sdk_skeletons/
            mod.py
        """.format(hash=generator3.core.module_hash('mod', mod_path)))

    def test_layout_for_physical_module_inside_package(self):
        mod_path = self.resolve_in_test_data('pkg/subpkg/mod.py')
        self.run_generator(mod_qname='pkg.subpkg.mod', mod_path=mod_path)
        self.assertDirLayoutEquals(self.temp_python_stubs_root, """
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
        """.format(hash=generator3.core.module_hash('mod', mod_path)))

    # PY-36884
    def test_pregenerated_skeletons_mode(self):
        self.check_generator_output('mod', mod_path='mod.py', extra_env={
            'IS_PREGENERATED_SKELETONS': '1'
        })

    # PY-36884
    def test_pregenerated_skeletons_mode_for_builtin_module(self):
        self.run_generator('sys', extra_env={
            'IS_PREGENERATED_SKELETONS': '1'
        })
        sys_cached_skeleton_path = os.path.join(self.temp_cache_dir, generator3.core.module_hash('sys', None), 'sys.py')
        self.assertTrue(os.path.exists(sys_cached_skeleton_path))
        with open(sys_cached_skeleton_path, 'r') as f:
            self.assertTrue('# from (pre-generated)\n' in f.read())

        sys_skeleton_path = os.path.join(self.temp_skeletons_dir, 'sys.py')
        self.assertTrue(os.path.exists(sys_skeleton_path))
        with open(sys_skeleton_path, 'r') as f:
            self.assertTrue('# from (built-in)\n' in f.read())

    def test_skeleton_regenerated_for_changed_module(self):
        self.check_generator_output('mod', mod_path='mod.py', extra_syspath=['versions/v2'])

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

    def test_cache_skeleton_not_regenerated_when_sdk_skeleton_generation_failed_for_same_version_and_same_binary(self):
        self.check_generator_output('mod', mod_path='mod.py', gen_version='0.1', custom_required_gen=True,
                                    success=False)

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
        mod_hash = generator3.core.module_hash('_abc', None)
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

        self.run_generator('_ast', None)
        with open(os.path.join(self.temp_skeletons_dir, '_ast.py')) as f:
            self.assertEqual(template.format(origin='(built-in)'), f.read())

    def test_single_pyexpat_skeletons_layout(self):
        self.run_generator('pyexpat')
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

    def test_results_in_single_module_mode(self):
        result = self.run_generator('mod', 'mod.py')
        self.assertIn({
            'type': 'generation_result',
            'module_name': 'mod',
            'module_origin': 'mod.py',
            'generation_status': 'GENERATED'
        }, result.control_messages())

    def test_trailing_slash_in_sdk_skeletons_path_does_not_affect_cache_location(self):
        self.run_generator('mod', 'mod.py', output_dir=self.temp_skeletons_dir + os.path.sep)
        self.assertDirLayoutEquals(self.temp_python_stubs_root, """
        cache/
            e3b0c44298/
                mod.py
        sdk_skeletons/
            mod.py
        """)

    @python3_only
    def test_keyword_only_arguments_in_signatures(self):
        self.check_generator_output('mod', 'mod.py')

    @python3_only
    def test_keyword_only_arguments_in_return_type_constructor(self):
        self.check_generator_output('mod', 'mod.py')

    def test_stdlib_modules_used_by_pyparsing_not_considered_submodules(self):
        result = self.run_generator('_ast')
        submodule_logs = [m for m in result.control_messages('log')
                          if 'generator3._vendor.' in m['message']]
        self.assertFalse(submodule_logs)

    # PY-60592
    def test_ignoring_extra_submodules_not_under_binary_own_qname(self):
        self.check_generator_output('pkg.mod', 'pkg/mod.py')

    # PY-60959
    def test_module_attributes_with_illegal_names_are_skipped(self):
        self.check_generator_output('mod', 'mod.py')

    # PY-49650
    def test_static_method(self):
        self.check_generator_output('mod', 'mod.py')


class MultiModuleGenerationTest(FunctionalGeneratorTestCase):
    default_generator_extra_args = ['--name-pattern', 'mod?']
    # This is a hack to keep the existing behavior where we keep discovering only binary files
    # (which can't be distributed with tests in a platform-independent manner), but user their .py
    # counterparts for actual importing and introspection
    default_generator_extra_syspath = ['mocks', 'binaries']

    def test_skeletons_for_builtins_are_stored_in_same_cache_directory(self):
        self.run_generator(extra_args=['--builtins-only'], extra_syspath=[])
        builtins_hash = generator3.core.module_hash('sys', None)
        builtins_cache_dir = os.path.join(self.temp_cache_dir, builtins_hash)
        self.assertTrue(os.path.isdir(builtins_cache_dir))
        builtin_mod_skeletons = os.listdir(builtins_cache_dir)
        self.assertIn('_ast.py', builtin_mod_skeletons)
        self.assertIn('sys.py', builtin_mod_skeletons)

    # TODO figure out why this is not true for some interpreters
    @unittest.skipUnless('pyexpat' in sys.builtin_module_names, "pyexpat must be a built-in module")
    def test_pyexpat_layout_in_builtins(self):
        self.run_generator(extra_args=['--builtins-only'], extra_syspath=[])
        self.assertFalse(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat.py')))
        self.assertTrue(os.path.isdir(os.path.join(self.temp_skeletons_dir, 'pyexpat')))
        self.assertNonEmptyFile(os.path.join(self.temp_skeletons_dir, 'pyexpat', '__init__.py'))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'model.py')))
        self.assertTrue(os.path.exists(os.path.join(self.temp_skeletons_dir, 'pyexpat', 'errors.py')))

    @test_data_dir('simple')
    def test_progress_indication(self):
        result = self.run_generator()
        self.assertContainsInRelativeOrder([
            {'type': 'progress', 'text': 'mod1', 'minor': True, 'fraction': 0.0},
            {'type': 'progress', 'text': 'mod2', 'minor': True, 'fraction': 0.5},
            {'type': 'progress', 'fraction': 1.0},
        ], result.control_messages())

    @test_data_dir('simple')
    def test_intermediate_results_reporting(self):
        result = self.run_generator()
        self.assertContainsInRelativeOrder([
            {'type': 'generation_result',
             'module_name': 'mod1',
             'module_origin': 'mod1.so',
             'generation_status': 'GENERATED'},
            {'type': 'generation_result',
             'module_name': 'mod2',
             'module_origin': 'mod2.so',
             'generation_status': 'GENERATED'}
        ], result.control_messages())

    @test_data_dir('simple')
    def test_general_results_and_layout(self):
        self.check_generator_output()

    @test_data_dir('simple')
    def test_logging_configured_and_propagates_from_worker_subprocess(self):
        result = self.run_generator()
        subprocess_messages = [m for m in result.control_messages('log')
                               if m['message'].startswith('Updating cache for mod')]
        self.assertEqual(2, len(subprocess_messages))


class StatePassingGenerationTest(FunctionalGeneratorTestCase):
    default_generator_extra_args = ['--state-file', '-',
                                    '--name-pattern', 'mod?']
    default_generator_extra_syspath = ['mocks', 'binaries']

    def test_existing_updated_due_to_required_gen_version(self):
        state = {
            'sdk_skeletons': {
                # shouldn't be updated
                'mod1': {
                    'gen_version': '0.1',
                    'status': 'GENERATED'
                },
                # should be updated because of "required_gen_version" file
                'mod2': {
                    'gen_version': '0.1',
                    'status': 'GENERATED'
                }
            }
        }
        self.check_generator_output(input=json.dumps(state),
                                    custom_required_gen=True,
                                    gen_version='0.2')

    def test_failed_updated_due_to_updated_generator_version(self):
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.2',
                    'status': 'FAILED',
                },
                'mod2': {
                    'gen_version': '0.1',
                    'status': 'FAILED'
                }
            }
        }
        self.check_generator_output(input=json.dumps(state),
                                    custom_required_gen=True,
                                    gen_version='0.2')

    def test_existing_updated_due_to_modified_binary(self):
        mod1_mtime = file_modification_timestamp(
            self.resolve_in_test_data('binaries/mod1.so'))
        mod2_mtime = file_modification_timestamp(
            self.resolve_in_test_data('binaries/mod2.so'))
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.1',
                    'bin_mtime': mod1_mtime,
                    'status': 'GENERATED',
                },
                'mod2': {
                    'gen_version': '0.1',
                    # generated for a binary modified ten seconds before its current state
                    'bin_mtime': mod2_mtime - 10,
                    'status': 'GENERATED'
                }
            }
        }
        self.check_generator_output(input=json.dumps(state),
                                    custom_required_gen=True,
                                    gen_version='0.2')

    def test_failed_updated_due_to_modified_binary(self):
        mod1_mtime = file_modification_timestamp(self.resolve_in_test_data('binaries/mod1.so'))
        mod2_mtime = file_modification_timestamp(self.resolve_in_test_data('binaries/mod2.so'))
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.1',
                    'bin_mtime': mod1_mtime,
                    'status': 'FAILED',
                },
                'mod2': {
                    'gen_version': '0.1',
                    # generated for a binary modified ten seconds before its current state
                    'bin_mtime': mod2_mtime - 10,
                    'status': 'FAILED'
                }
            }
        }
        self.check_generator_output(input=json.dumps(state),
                                    custom_required_gen=True,
                                    gen_version='0.1')

    def test_failed_skeleton_skipped(self):
        mod_mtime = file_modification_timestamp(self.resolve_in_test_data('binaries/mod.so'))
        state = {
            'sdk_skeletons': {
                'mod': {
                    'gen_version': '0.1',
                    'status': 'FAILED',
                    'bin_mtime': mod_mtime
                }
            }
        }
        self.check_generator_output(
            extra_args=['--state-file', '-',
                        '--name-pattern', 'mod'],
            gen_version='0.1',
            custom_required_gen=True,
            input=json.dumps(state)
        )

    def test_new_modules_are_added_to_state_json(self):
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.1',
                    'status': 'GENERATED',
                },
            }
        }
        self.check_generator_output(input=json.dumps(state),
                                    custom_required_gen=True,
                                    gen_version='0.1')

    def test_not_found_modules_are_removed_from_state_json(self):
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.1',
                    'status': 'GENERATED',
                },
                'mod2': {
                    'gen_version': '0.1',
                    'status': 'GENERATED',
                }
            }
        }
        self.check_generator_output(input=json.dumps(state), gen_version='0.1', custom_required_gen=True)

    def test_only_leaving_state_file_no_read(self):
        self.check_generator_output(extra_args=['--init-state-file',
                                                '--name-pattern', 'mod?'])

    def test_state_indication_for_builtin_module(self):
        result = self.run_generator(extra_args=['--init-state-file',
                                                '--name-pattern', '_ast'])
        self.assertIsNotNone(result.state_json)
        self.assertIn('_ast', result.state_json['sdk_skeletons'])
        self.assertEqual('GENERATED', result.state_json['sdk_skeletons']['_ast']['status'])

    def test_modification_time_left_in_state_json_for_new_binaries(self):
        result = self.run_generator(extra_args=['--init-state-file',
                                                '--name-pattern', 'mod'],
                                    extra_env={ENV_TEST_MODE_FLAG: None})
        self.assertIsNotNone(result.state_json)
        self.assertIn('bin_mtime', result.state_json['sdk_skeletons']['mod'])

    def test_state_json_for_cached_skeletons_retains_original_gen_version(self):
        # For mod1 we have a newer version in the cache.
        # For non-existing mod2 we have satisfying version in the cache.
        state = {
            'sdk_skeletons': {
                'mod1': {
                    'gen_version': '0.1',
                    'status': 'GENERATED',
                }
            }
        }
        self.check_generator_output(gen_version='0.3',
                                    custom_required_gen=True,
                                    input=json.dumps(state))

    def test_state_json_for_up_to_date_skeletons_retains_original_gen_version(self):
        self.check_generator_output(extra_args=['--init-state-file',
                                                '--name-pattern', 'mod'],
                                    gen_version='0.2',
                                    custom_required_gen=True)

    def test_state_json_accepted_as_path_to_file(self):
        self.check_generator_output(
            extra_args=[
                '--state-file', self.resolve_in_test_data('.state.json'),
                '--name-pattern', 'mod*',
            ],
        )