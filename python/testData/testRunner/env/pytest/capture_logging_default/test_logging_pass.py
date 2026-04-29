import os
import logging

logger = logging.getLogger(__name__)

def test_pass_with_logging(pytestconfig):
    assert "PYTEST_TEAMCITY_SKIP_PASSED_OUTPUT_DEFAULT" not in os.environ
    assert pytestconfig.getini("skippassedoutput") is True
    logger.warning("warning_from_passing_test")
    assert True
