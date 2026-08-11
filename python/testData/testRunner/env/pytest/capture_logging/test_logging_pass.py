import logging

logger = logging.getLogger(__name__)

def test_pass_with_logging():
    logger.warning("warning_from_passing_test")
    assert True
