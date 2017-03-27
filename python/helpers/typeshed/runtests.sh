#!/bin/sh

./tests/mypy_test.py
./tests/pytype_test.py
echo "Running flake8..."
flake8 && echo "flake8 run clean."
