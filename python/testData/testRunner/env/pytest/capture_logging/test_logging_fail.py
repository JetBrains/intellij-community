import logging

logger = logging.getLogger(__name__)

def test_fail_with_logging():
    logger.warning("warning_from_failing_test")
    assert False, "intentional failure"
