from datetime import datetime

import numpy as np
import pandas as pd
import pytest

from datalore.display.supported_data_type import _standardize_dict
from datalore.display.supported_data_type import _standardize_value


@pytest.mark.parametrize('value, expected, result_type', [
    (np.array([1, 2]), [1, 2], list),
    (pd.Series([1, 2]), [1, 2], list),
    (np.float64(0.25), 0.25, float),
    (('a', 1), ('a', 1), tuple),
], ids=['np.array', 'pd.Series', 'np.float', 'tuple'])
def test_standardize_simple_values(value, expected, result_type):
    check_standardization(value, expected, result_type)


def test_datetime_standardization():
    value = datetime(2000, 1, 1)
    expected = value.timestamp() * 1000
    check_standardization(value, expected, float)


def check_standardization(value, expected, result_type):
    standardized_np_array = _standardize_value(value)
    assert standardized_np_array == expected
    assert type(standardized_np_array) == result_type  # we should the check exact type, not inheritance


class TestStandardizeDictionaries:
    # noinspection PyAttributeOutsideInit
    @pytest.fixture(autouse=True)
    def setup(self):
        self.dictionary = {'column': [1, 2, 3]}

    def test_standardize_nested_df(self):
        nested_df = {'a': pd.DataFrame(self.dictionary)}
        standardized = _standardize_dict(nested_df)
        assert isinstance(standardized, dict)
        self.check_dictionary(standardized['a'])

    def test_standardize_df(self):
        df = pd.DataFrame(self.dictionary)
        standardized = _standardize_dict(df)
        self.check_dictionary(standardized)

    def test_standardize_several_nested_objects(self):
        nested_df = {
            'a': self.dictionary.copy(),
            'b': pd.Series([1, 2, 3])
        }
        standardized = _standardize_dict(nested_df)
        assert type(standardized) == dict
        self.check_dictionary(standardized['a'])

        assert type(standardized['b']) == list
        assert standardized['b'] == [1, 2, 3]

    def check_dictionary(self, value):
        assert value == self.dictionary
        assert type(value) == dict
        assert type(value['column']) == list
