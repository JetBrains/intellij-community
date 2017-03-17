import argparse
import glob
import os
import re
import sys
import unittest
import zipfile

out_dir = None
dist_dir = None
build = None

class TestStringMethods(unittest.TestCase):

  def test_artifacts_are_present(self):
    name = "android-studio-" + build
    for suffix in [ ".mac.zip", ".tar.gz", ".win.zip", ".win32.zip"]:
      file = os.path.join(dist_dir, name + suffix)
      self.assertTrue(os.path.exists(file), "Artifact " + file + " not found.")

  def test_mac_contents_clean(self):
    name = os.path.join(dist_dir, "android-studio-" + build + ".mac.zip")
    file = zipfile.ZipFile(name)
    for f in file.namelist():
      m = re.search("Android Studio.*\.app/Contents/([^/]+)$", f)
      if m:
        self.assertEquals(m.group(1), "Info.plist", "Only Info.plist should be present in Contents (Found " + m.group(1) + ")")

  def test_no_build_files(self):
    for root, dirnames, filenames in os.walk(out_dir):
      for filename in filenames:
        self.assertFalse(filename == "BUILD" or filename == "BUILD.bazel",
              "Unexpected BUILD file in output dir: " + root + "/" + filename)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required = True)
    parser.add_argument('--dist', required = True)
    parser.add_argument('--build', required = True)
    parser.add_argument('unittest_args', nargs='*')

    args = parser.parse_args()
    out_dir = args.out
    dist_dir = args.dist
    build = args.build

    sys.argv[1:] = args.unittest_args
    unittest.main()
