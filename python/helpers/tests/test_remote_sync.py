# coding=utf-8
from __future__ import unicode_literals

import json
import os
import subprocess
import sys
import textwrap

import remote_sync
import six
from remote_sync import RemoteSync
from testing import HelpersTestCase

if six.PY2:
    from io import open


class RemoteSyncTest(HelpersTestCase):
    @property
    def test_data_root(self):
        return os.path.join(super(RemoteSyncTest, self).test_data_root, 'remote_sync')

    @property
    def class_test_data_dir(self):
        return self.test_data_root

    def assertZipContentEquals(self, path, expected):
        expected = textwrap.dedent(expected).strip()
        actual = '\n'.join(self.read_zip_entries(path))
        self.assertMultiLineEqual(actual + '\n', expected + '\n')

    def assertEmptyZip(self, path):
        self.assertEqual(len(self.read_zip_entries(path)), 0)

    def assertJsonEquals(self, path, expected_obj):
        with open(path, 'r', encoding='utf-8') as f:
            actual_obj = json.load(f)
            self.assertEqual(actual_obj, expected_obj)

    def test_basic_layout(self):
        self.collect_sources(['root1', 'root2', 'root3'])
        self.assertDirLayoutEquals(self.temp_dir, """
        .state.json
        root1.zip
        root2.zip
        root3.zip
        """)

    def test_roots_with_identical_name(self):
        self.collect_sources(['root', 'dir/root'])
        self.assertDirLayoutEquals(self.temp_dir, """
        .state.json
        root.zip
        root__1.zip
        """)

    def test_nested_roots(self):
        self.collect_sources(['root', 'root/pkg/nested'])
        self.assertDirLayoutEquals(self.temp_dir, """
        .state.json
        nested.zip
        root.zip
        """)
        self.assertZipContentEquals(self.resolve_in_temp_dir('root.zip'), """
        pkg/__init__.py
        """)
        self.assertZipContentEquals(self.resolve_in_temp_dir('nested.zip'), """
        mod.py
        """)

    def test_output_dir_cannot_be_root(self):
        with self.assertRaises(ValueError):
            self.collect_sources(['root'],
                                 output_dir=os.path.join(self.test_data_dir, 'root'))

    def test_output_dir_cannot_be_inside_root(self):
        with self.assertRaises(ValueError):
            self.collect_sources(['root'], output_dir=os.path.join(self.test_data_dir,
                                                                   'root/out'))

    def test_non_existing_roots_ignored(self):
        self.collect_sources(['root', 'non-existing'])
        self.assertDirLayoutEquals(self.temp_dir, """
        .state.json
        root.zip
        """)

    def test_non_dir_roots_ignored(self):
        self.collect_sources(['root', 'root.egg'])
        self.assertDirLayoutEquals(self.temp_dir, """
        .state.json
        root.zip
        """)

    def test_new_state_json_content(self):
        self.collect_sources(['root'])
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                        'mod.py': {
                            'mtime': self.mtime('root/mod.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_state_json_when_file_removed(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                        'mod.py': {
                            'mtime': 0,
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': [
                        'mod.py',
                    ]
                }
            ]
        })

    def test_state_json_when_dir_removed(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': 0,
                        },
                        'mod.py': {
                            'mtime': self.mtime('root/mod.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': self.mtime('root/mod.py'),
                        }
                    },
                    'invalid_entries': [
                        'dir/mod.py',
                    ]
                }
            ]
        })

    def test_state_json_when_file_added(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                        'new.py': {
                            'mtime': self.mtime('root/new.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_state_json_when_dir_added(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                        'new/__init__.py': {
                            'mtime': self.mtime('root/new/__init__.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_state_json_when_file_changed(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': 0,
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertZipContentEquals(self.resolve_in_temp_dir('root.zip'), "dir/mod.py")

    def test_state_json_invalid_entries_removed(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': [
                        'foo.py',
                        'bar/baz.py',
                    ]
                },
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_state_json_when_nothing_changed(self):
        orig_state_json = {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        }
        self.collect_sources(['root'], state_json=orig_state_json)
        new_state_json = self.resolve_in_temp_dir('.state.json')
        self.assertJsonEquals(new_state_json, orig_state_json)
        self.assertEmptyZip(self.resolve_in_temp_dir('root.zip'))

    def test_state_json_when_root_removed(self):
        self.collect_sources(['root'], state_json={
            'roots': [
                {
                    'path': 'removed',
                    'zip': 'removed.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': 0,
                        },
                    },
                    'invalid_entries': []
                },
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                },
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': [],
                }
            ]
        })

    def test_state_json_when_root_added(self):
        self.collect_sources(['root', 'new'], state_json={
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                }
            ]
        })
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'root',
                    'zip_name': 'root.zip',
                    'valid_entries': {
                        'dir/mod.py': {
                            'mtime': self.mtime('root/dir/mod.py'),
                        },
                    },
                    'invalid_entries': []
                },
                {
                    'path': 'new',
                    'zip_name': 'new.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': self.mtime('new/mod.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_state_json_original_order_of_roots_preserved(self):
        self.collect_sources(['a', 'c', 'b'])
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            'roots': [
                {
                    'path': 'a',
                    'zip_name': 'a.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': self.mtime('a/mod.py'),
                        }
                    },
                    'invalid_entries': []
                },
                {
                    'path': 'c',
                    'zip_name': 'c.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': self.mtime('c/mod.py'),
                        }
                    },
                    'invalid_entries': []
                },
                {
                    'path': 'b',
                    'zip_name': 'b.zip',
                    'valid_entries': {
                        'mod.py': {
                            'mtime': self.mtime('b/mod.py'),
                        }
                    },
                    'invalid_entries': []
                }
            ]
        })

    def test_output_state_json_non_ascii_paths(self):
        """Checks that non-ASCII paths are written without escaping in .state.json."""
        self.collect_sources(['по-русски'])
        out_state_json = self.resolve_in_temp_dir('.state.json')
        with open(out_state_json, 'r', encoding='utf-8') as state_file:
            self.assertIn('"по-русски.zip"', state_file.read())

    def test_input_state_json_with_non_ascii_paths(self):
        """Checks that .state.json with non-ASCII paths is correctly decoded."""
        # Run a real process to test input JSON decoding
        subprocess.check_output(
            [sys.executable, remote_sync.__file__,
             '--roots', self.resolve_in_test_data('по-русски'),
             '--state-file', self.resolve_in_test_data('.state.json'),
             self.temp_dir],
        )
        self.assertJsonEquals(self.resolve_in_temp_dir('.state.json'), {
            "roots": [
                {
                    "path": self.resolve_in_test_data("по-русски"),
                    "zip_name": "по-русски.zip",
                    "valid_entries": {
                        "балалайка.py": {
                            "mtime": self.mtime('по-русски/балалайка.py'),
                        }
                    },
                    "invalid_entries": []
                }
            ]
        })

    def test_smoke_check_command_line_parsing(self):
        """Checks that we don't use any Python 2-incompatible API in argparse."""
        output = subprocess.check_output([sys.executable, remote_sync.__file__, "-h"],
                                         universal_newlines=True)
        self.assertIn('usage: remote_sync.py', output)

    def collect_sources(self, roots_inside_test_data, output_dir=None, state_json=None):
        if output_dir is None:
            output_dir = self.temp_dir
        self.assertTrue(
            os.path.exists(self.test_data_dir),
            'Test data directory {} does not exist'.format(self.test_data_dir)
        )
        roots = [self.resolve_in_test_data(r) for r in roots_inside_test_data]
        rsync = RemoteSync(roots, output_dir, state_json)
        rsync._test_root = self.test_data_dir
        rsync.run()

    def mtime(self, test_data_path):
        path = self.resolve_in_test_data(test_data_path)
        return int(os.stat(path).st_mtime)
