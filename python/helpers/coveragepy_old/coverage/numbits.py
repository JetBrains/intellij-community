# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""
Functions to manipulate packed binary representations of number sets.

To save space, coverage stores sets of line numbers in SQLite using a packed
binary representation called a numbits.  A numbits is a set of positive
integers.

A numbits is stored as a blob in the database.  The exact meaning of the bytes
in the blobs should be considered an implementation detail that might change in
the future.  Use these functions to work with those binary blobs of data.

"""
import json

from coverage import env
from coverage.backward import byte_to_int, bytes_to_ints, binary_bytes, zip_longest
from coverage.misc import contract, new_contract

if env.PY3:
    def _to_blob(b):
        """Convert a bytestring into a type SQLite will accept for a blob."""
        return b

    new_contract('blob', lambda v: isinstance(v, bytes))
else:
    def _to_blob(b):
        """Convert a bytestring into a type SQLite will accept for a blob."""
        return buffer(b)                                    # pylint: disable=undefined-variable

    new_contract('blob', lambda v: isinstance(v, buffer))   # pylint: disable=undefined-variable


@contract(nums='Iterable', returns='blob')
def nums_to_numbits(nums):
    """Convert `nums` into a numbits.

    Arguments:
        nums: a reusable iterable of integers, the line numbers to store.

    Returns:
        A binary blob.
    """
    try:
        nbytes = max(nums) // 8 + 1
    except ValueError:
        # nums was empty.
        return _to_blob(b'')
    b = bytearray(nbytes)
    for num in nums:
        b[num//8] |= 1 << num % 8
    return _to_blob(bytes(b))


@contract(numbits='blob', returns='list[int]')
def numbits_to_nums(numbits):
    """Convert a numbits into a list of numbers.

    Arguments:
        numbits: a binary blob, the packed number set.

    Returns:
        A list of ints.

    When registered as a SQLite function by :func:`register_sqlite_functions`,
    this returns a string, a JSON-encoded list of ints.

    """
    nums = []
    for byte_i, byte in enumerate(bytes_to_ints(numbits)):
        for bit_i in range(8):
            if (byte & (1 << bit_i)):
                nums.append(byte_i * 8 + bit_i)
    return nums


@contract(numbits1='blob', numbits2='blob', returns='blob')
def numbits_union(numbits1, numbits2):
    """Compute the union of two numbits.

    Returns:
        A new numbits, the union of `numbits1` and `numbits2`.
    """
    byte_pairs = zip_longest(bytes_to_ints(numbits1), bytes_to_ints(numbits2), fillvalue=0)
    return _to_blob(binary_bytes(b1 | b2 for b1, b2 in byte_pairs))


@contract(numbits1='blob', numbits2='blob', returns='blob')
def numbits_intersection(numbits1, numbits2):
    """Compute the intersection of two numbits.

    Returns:
        A new numbits, the intersection `numbits1` and `numbits2`.
    """
    byte_pairs = zip_longest(bytes_to_ints(numbits1), bytes_to_ints(numbits2), fillvalue=0)
    intersection_bytes = binary_bytes(b1 & b2 for b1, b2 in byte_pairs)
    return _to_blob(intersection_bytes.rstrip(b'\0'))


@contract(numbits1='blob', numbits2='blob', returns='bool')
def numbits_any_intersection(numbits1, numbits2):
    """Is there any number that appears in both numbits?

    Determine whether two number sets have a non-empty intersection. This is
    faster than computing the intersection.

    Returns:
        A bool, True if there is any number in both `numbits1` and `numbits2`.
    """
    byte_pairs = zip_longest(bytes_to_ints(numbits1), bytes_to_ints(numbits2), fillvalue=0)
    return any(b1 & b2 for b1, b2 in byte_pairs)


@contract(num='int', numbits='blob', returns='bool')
def num_in_numbits(num, numbits):
    """Does the integer `num` appear in `numbits`?

    Returns:
        A bool, True if `num` is a member of `numbits`.
    """
    nbyte, nbit = divmod(num, 8)
    if nbyte >= len(numbits):
        return False
    return bool(byte_to_int(numbits[nbyte]) & (1 << nbit))


def register_sqlite_functions(connection):
    """
    Define numbits functions in a SQLite connection.

    This defines these functions for use in SQLite statements:

    * :func:`numbits_union`
    * :func:`numbits_intersection`
    * :func:`numbits_any_intersection`
    * :func:`num_in_numbits`
    * :func:`numbits_to_nums`

    `connection` is a :class:`sqlite3.Connection <python:sqlite3.Connection>`
    object.  After creating the connection, pass it to this function to
    register the numbits functions.  Then you can use numbits functions in your
    queries::

        import sqlite3
        from coverage.numbits import register_sqlite_functions

        conn = sqlite3.connect('example.db')
        register_sqlite_functions(conn)
        c = conn.cursor()
        # Kind of a nonsense query: find all the files and contexts that
        # executed line 47 in any file:
        c.execute(
            "select file_id, context_id from line_bits where num_in_numbits(?, numbits)",
            (47,)
        )
    """
    connection.create_function("numbits_union", 2, numbits_union)
    connection.create_function("numbits_intersection", 2, numbits_intersection)
    connection.create_function("numbits_any_intersection", 2, numbits_any_intersection)
    connection.create_function("num_in_numbits", 2, num_in_numbits)
    connection.create_function("numbits_to_nums", 1, lambda b: json.dumps(numbits_to_nums(b)))
