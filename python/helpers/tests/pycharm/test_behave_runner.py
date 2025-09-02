from pathlib import Path

from behave import configuration

from pycharm.behave_runner import _BehaveRunner
from pycharm.behave_runner import _register_null_formatter


def test_scenarios_to_run():
    my_config = configuration.Configuration()
    helpers_root = Path(__file__).parent.parent.parent
    path = helpers_root / "testResources" / "behave_examples" / "feature_with_rules"
    my_config.paths = [str(path / "rule.feature")]
    base_dir = str(path)

    format_name = "com.jetbrains.pycharm.formatter"
    _register_null_formatter(format_name)
    my_config.format = [format_name]

    br = _BehaveRunner(my_config, base_dir, use_old_runner=False)
    features = br._get_features_to_run()

    assert len(features) == 1
    assert len(list(features[0].scenarios)) == 5
