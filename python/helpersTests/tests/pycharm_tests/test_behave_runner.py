import os
import sys
import types
import unittest

from behave import configuration
from behave.step_registry import registry as the_step_registry

from behave_runner import _EXT_SUFFIXES
from behave_runner import _BehaveRunner
from behave_runner import _modules_to_reimport
from behave_runner import _register_null_formatter
from behave_runner import _step_definition_files
from testing import _helpers_test_resources_root

_EXAMPLES = os.path.join(_helpers_test_resources_root, "behave_examples")


def _clear_step_registry():
    # behave <= 1.2.6 has no StepRegistry.clear(), see PY-86339
    if hasattr(the_step_registry, 'clear'):
        the_step_registry.clear()
    else:
        the_step_registry.steps = dict(given=[], when=[], then=[], step=[])


def _make_config(example, feature):
    my_config = configuration.Configuration()
    base_dir = os.path.join(_EXAMPLES, example)
    my_config.paths = [os.path.join(base_dir, feature)]

    format_name = "com.jetbrains.pycharm.formatter"
    _register_null_formatter(format_name)
    my_config.format = [format_name]
    my_config.reporters = []
    my_config.stdout_capture = False
    my_config.stderr_capture = False
    return my_config, base_dir


class BehaveRunnerTest(unittest.TestCase):
    def test_scenarios_to_run(self):
        my_config, base_dir = _make_config("feature_with_rules", "rule.feature")

        br = _BehaveRunner(my_config, base_dir, use_old_runner=False)
        features = br._get_features_to_run()

        self.assertEqual(len(features), 1)
        self.assertEqual(len(list(features[0].scenarios)), 5)
        # Read twice on purpose: "scenarios" must not be a one-shot iterator
        self.assertEqual(len(list(features[0].scenarios)), 5)

    def test_number_of_tests_includes_background_steps(self):
        # rule.feature runs 17 steps: 1 feature background step per scenario (5),
        # 2 steps of the R1 background for each of the 2 scenarios of that rule (4),
        # and the steps of the scenarios themselves (2 + 2 + 2 + 1 + 1 = 8)
        my_config, base_dir = _make_config("feature_with_rules", "rule.feature")

        br = _BehaveRunner(my_config, base_dir, use_old_runner=False)

        self.assertEqual(br._get_number_of_tests(), 17)


class BehaveRunnerModuleReloadTest(unittest.TestCase):
    """
    The step registry is cleared between the dry run and the real run, so modules that
    registered steps have to be reimported (PY-86174). Nothing else may be reimported:
    reexecuting a module body resets module-level state (PY-89530, PY-90761), redefines
    stdlib types other packages captured at import time (PY-90800), reinitializes C
    extensions (PY-89290) and desynchronizes partially dropped packages (PY-91210).
    """

    _FIXTURE_MODULES = ('nested_pkg', 'nested_pkg.counters', 'nested_pkg.more_steps')

    def setUp(self):
        self._saved_path = list(sys.path)
        self._drop_fixture_modules()
        _clear_step_registry()

    def tearDown(self):
        self._drop_fixture_modules()
        _clear_step_registry()
        sys.path[:] = self._saved_path
        if hasattr(sys, '_pycharm_behave_fixture_counters'):
            delattr(sys, '_pycharm_behave_fixture_counters')

    def _drop_fixture_modules(self):
        for name in self._FIXTURE_MODULES:
            sys.modules.pop(name, None)

    def test_only_step_modules_are_reimported(self):
        cwd = os.getcwd()
        my_config, base_dir = _make_config("nested_steps", "nested.feature")
        try:
            _BehaveRunner(my_config, base_dir, use_old_runner=False).run()
        finally:
            os.chdir(cwd)

        counters = getattr(sys, '_pycharm_behave_fixture_counters', {})

        # Dropped after the dry run, so its steps register again for the real run
        self.assertEqual(counters.get('nested_pkg.more_steps'), 2,
                         "Step module was not reimported, its steps would be undefined")
        # Reached only by an import from a step module: must run once, like in a plain
        # "behave" launch
        self.assertEqual(counters.get('nested_pkg.counters'), 1,
                         "A module without step definitions was reimported")
        # The package of a dropped step module must stay, otherwise the reimported
        # parent no longer exposes its already imported children
        self.assertEqual(counters.get('nested_pkg'), 1,
                         "The package of a step module was reimported")

    def test_nested_steps_are_defined_on_the_real_run(self):
        cwd = os.getcwd()
        my_config, base_dir = _make_config("nested_steps", "nested.feature")
        try:
            _BehaveRunner(my_config, base_dir, use_old_runner=False).run()
        finally:
            os.chdir(cwd)

        # The registry is cleared after the dry run, so these are the registrations of
        # the real run: without them every step of the feature would be undefined.
        # behave 1.3.x keeps the anchored regexp in "pattern", 1.2.6 stores the original
        patterns = set(matcher.pattern.strip('^$') for matchers in the_step_registry.steps.values()
                       for matcher in matchers)
        self.assertEqual(patterns, {"I am set up by a nested step",
                                    "the nested step module is loaded"})


class _FakeLocation(object):
    def __init__(self, filename):
        self.filename = filename


class _FakeMatcher(object):
    def __init__(self, location=None, func=None):
        self.location = location
        self.func = func


class _FakeRegistry(object):
    def __init__(self, matchers):
        self.steps = {'given': list(matchers), 'when': [], 'then': [], 'step': []}


class StepModuleSelectionTest(unittest.TestCase):
    """
    Unit tests for the sys.modules selection itself. Synthetic modules are used so that
    layouts which can't be committed as fixtures -- compiled submodules, namespace
    packages, Python 2 placeholders -- can be covered as well.
    """

    _STEP_FILE = os.path.join('pkg', 'steps.py')
    _EXTENSION_FILE = 'native' + _EXT_SUFFIXES[0]

    @staticmethod
    def _key(path):
        return os.path.normcase(os.path.abspath(path))

    def setUp(self):
        self._added = []

    def tearDown(self):
        for name in self._added:
            sys.modules.pop(name, None)

    def _add_module(self, name, path):
        module = types.ModuleType(name)
        if path is not None:
            module.__file__ = os.path.abspath(path)
        sys.modules[name] = module
        self._added.append(name)
        return module

    def _select(self, *step_files):
        old_modules = dict((name, module) for (name, module) in sys.modules.items()
                           if name not in self._added)
        keys = set(self._key(path) for path in (step_files or (self._STEP_FILE,)))
        return _modules_to_reimport(old_modules, keys)

    def test_registry_locations_are_collected(self):
        registry = _FakeRegistry([_FakeMatcher(location=_FakeLocation(self._STEP_FILE))])

        self.assertEqual(_step_definition_files(registry), {self._key(self._STEP_FILE)})

    def test_step_function_is_used_when_a_matcher_has_no_location(self):
        def step_impl(context):
            pass

        registry = _FakeRegistry([_FakeMatcher(func=step_impl)])

        self.assertEqual(_step_definition_files(registry),
                         {self._key(step_impl.__code__.co_filename)})

    def test_module_without_step_definitions_is_kept(self):
        self._add_module('fixture_library', os.path.join('pkg', 'library.py'))

        self.assertEqual(self._select(), set())

    def test_step_module_is_selected(self):
        self._add_module('fixture_steps', self._STEP_FILE)

        self.assertEqual(self._select(), {'fixture_steps'})

    def test_descendants_of_a_step_module_are_selected_too(self):
        self._add_module('fixture_pkg', self._STEP_FILE)
        self._add_module('fixture_pkg.child', os.path.join('pkg', 'child.py'))

        self.assertEqual(self._select(), {'fixture_pkg', 'fixture_pkg.child'})

    def test_subtree_with_an_extension_module_is_kept_whole(self):
        # The PY-91210 layout: dropping the pure Python parent while keeping the
        # compiled child leaves the parent without that child as an attribute
        self._add_module('fixture_ext_pkg', self._STEP_FILE)
        self._add_module('fixture_ext_pkg.native', self._EXTENSION_FILE)

        self.assertEqual(self._select(self._STEP_FILE, self._EXTENSION_FILE), set())

    def test_subtree_with_a_fileless_module_is_kept_whole(self):
        # Builtin, frozen and namespace modules can't be reexecuted either
        self._add_module('fixture_ns_pkg', self._STEP_FILE)
        self._add_module('fixture_ns_pkg.child', None)

        self.assertEqual(self._select(), set())

    def test_none_placeholder_is_ignored(self):
        # Python 2 leaves None in sys.modules for failed relative imports
        sys.modules['fixture_placeholder'] = None
        self._added.append('fixture_placeholder')

        self.assertEqual(self._select(), set())
