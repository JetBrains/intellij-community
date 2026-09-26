import sys
from typing_extensions import assert_type

if sys.version_info >= (3, 14):
    assert_type(sys._jit.is_available(), bool)
    assert_type(sys._jit.is_enabled(), bool)
    assert_type(sys._jit.is_active(), bool)

    def sys_is_not_a_package() -> None:
        # This has to be put into a function, because otherwise the presence
        # of this import statement causes errors on the above usages of `sys._jit`.
        import sys._jit  # type: ignore
