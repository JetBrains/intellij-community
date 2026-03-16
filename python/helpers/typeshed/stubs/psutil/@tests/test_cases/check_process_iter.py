"""Test cases for psutil.process_iter and its cache_clear method."""

from __future__ import annotations

import psutil

# Test that process_iter can be called as a function
for proc in psutil.process_iter():
    break

# Test that process_iter has cache_clear method
psutil.process_iter.cache_clear()

# Test that cache_clear is callable
clear_method = psutil.process_iter.cache_clear
clear_method()
