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

  def test_profiler_artifacts_are_present(self):
    required = [
        "plugins/android/resources/perfa.jar",
        "plugins/android/resources/profilers-transform.jar",
      ];
    for abi in ["x86", "arm64-v8a", "armeabi-v7a"]:
      required += [
          "plugins/android/resources/simpleperf/%s/simpleperf" % abi,
          "plugins/android/resources/perfd/%s/perfd" % abi,
          "plugins/android/resources/perfa/%s/libperfa.so" % abi,
      ]

    name = os.path.join(dist_dir, "android-studio-" + build + ".win.zip")
    files = zipfile.ZipFile(name).namelist()
    for req in required:
      if "android-studio/" + req not in files:
        self.fail("Required file not found in distribution: " + req)

  def test_mac_attributes(self):
    name = os.path.join(dist_dir, "android-studio-" + build + ".mac.zip")
    file = zipfile.ZipFile(name)
    found = False
    for f in file.infolist():
      is_symlink = (f.external_attr & 0x20000000) > 0
      if f.filename.endswith("Contents/jre/jdk/Contents/MacOS/libjli.dylib"):
        found = True
        self.assertTrue(is_symlink, "Contents/jre/jdk/Contents/MacOS/libjli.dylib is not a symlink")
      elif f.filename.endswith("Contents/MacOS/studio"):
        self.assertFalse(f.external_attr == 0x1ED0000, "studio should be \"-rwxr-xr-x\"")
        self.assertFalse(is_symlink, f.filename + " should not be a symlink")
      else:
        self.assertFalse(f.external_attr == 0, "Unix attributes are missing from the entry")
        self.assertFalse(is_symlink, f.filename + " should not be a symlink")
    self.assertTrue(found, "Android Studio.*\.app/Contents/jre/jdk/Contents/MacOS/libjli.dylib not found")

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
