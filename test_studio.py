import argparse
import glob
import os
import re
import sys
import tarfile
import unittest
import zipfile

out_dir = None
dist_dir = None
build = None
aswb = False

class StudioTests(unittest.TestCase):
  longMessage = True

  def artifact_prefix(self):
    if aswb:
      return "aswb-"
    else:
      return "android-studio-"

  def test_artifacts_are_present(self):
    name = self.artifact_prefix() + build
    for suffix in [ ".mac.zip", ".tar.gz", ".win.zip", ".win32.zip"]:
      file = os.path.join(dist_dir, name + suffix)
      self.assertTrue(os.path.exists(file), "Artifact " + file + " not found.")

  def test_mac_contents_clean(self):
    name = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    file = zipfile.ZipFile(name)
    for f in file.namelist():
      m = re.search("Android Studio.*\.app/Contents/([^/]+)$", f)
      if m:
        self.assertEquals(m.group(1), "Info.plist", "Only Info.plist should be present in Contents (Found " + m.group(1) + ")")

  def find_member_path(self, zip_file, pattern):
    regex = re.compile(pattern)

    for path in zip_file.namelist():
      if regex.match(path):
        return path

    return None

  def test_aswb_mac_custom_properties(self):
    if not aswb:
      return

    zip_path = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")

    with zipfile.ZipFile(zip_path) as mac_zip:
      properties_path = self.find_member_path(mac_zip, "Android Studio.*\.app/Contents/bin/idea.properties")
      self.assertIsNotNone(properties_path, "Mac artifact is missing idea.properties file.")

      with mac_zip.open(properties_path, "r") as properties:
        seen_properties = set()
        expected_values = {
          "idea.case.sensitive.fs": "true",
          "idea.updates.url": "https://dl.google.com/android/aswb/patches/updates.xml",
          "idea.patches.url": "https://dl.google.com/android/aswb/patches"
        }

        for property, value in [line.strip().split("=") for line in properties.readlines()]:
          self.assertNotIn(property, seen_properties, "idea.properties has multiple definitions for " + property)
          seen_properties.add(property)

          if property in expected_values:
            self.assertEquals(value, expected_values[property], "idea.properties sets %s incorrectly" % property)

        missing_properties = [property for property in expected_values if property not in seen_properties]
        self.assertEquals(len(missing_properties), 0, "idea.properties missing definitions for " + ", ".join(missing_properties))

  def test_no_build_files(self):
    if aswb:  # aswb plugin includes a BUILD.bazel in the aspect folder
      return

    name = self.artifact_prefix() + build
    for suffix in [ ".mac.zip", ".win.zip", ".win32.zip"]:
      file_name = os.path.join(dist_dir, name + suffix)
      with zipfile.ZipFile(file_name) as file:
        for f in file.infolist():
          self.assertFalse(f.filename.endswith("/BUILD") or f.filename.endswith("/BUILD.bazel"),
              "Unexpected BUILD file in zip " + file_name + ": " + f.filename)

  def test_profiler_artifacts_are_present(self):
    required = [
        "plugins/android/resources/perfa.jar",
        "plugins/android/resources/profilers-transform.jar",
      ];
    for abi in ["x86", "arm64-v8a", "armeabi-v7a"]:
      required += [
          "plugins/android/resources/perfetto/%s/libperfetto.so" % abi,
          "plugins/android/resources/perfetto/%s/perfetto" % abi,
          "plugins/android/resources/perfetto/%s/traced" % abi,
          "plugins/android/resources/perfetto/%s/traced_probes" % abi,
          "plugins/android/resources/simpleperf/%s/simpleperf" % abi,
          "plugins/android/resources/transport/%s/transport" % abi,
          "plugins/android/resources/transport/agent/%s/libjvmtiagent.so" % abi,
      ]

    name = os.path.join(dist_dir, self.artifact_prefix() + build + ".win.zip")
    files = zipfile.ZipFile(name).namelist()
    for req in required:
      if "android-studio/" + req not in files:
        self.fail("Required file not found in distribution: " + req)

  def test_mac_attributes(self):
    name = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    with zipfile.ZipFile(name) as file:
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

  def test_aswb_includes_blaze_plugin(self):
    if not aswb:
      return

    required = [
        "plugins/aswb-blaze/lib/aswb.jar",
        "plugins/aswb-blaze/aspect/intellij_info_bundled.bzl",
      ];

    name = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    files = zipfile.ZipFile(name).namelist()
    for req in required:
      self.assertTrue(any(fname.endswith(req) for fname in files), req + " not present in " + name)

  def test_studio_does_not_contain_aswb(self):
    if aswb:
      return

    name = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    files = zipfile.ZipFile(name).namelist()
    self.assertFalse(any("plugins/aswb" in fname for fname in files), name + " contains plugins/aswb")

  def test_custom_vmoptions_mac(self):
    """Tests that vmoptions specific to ASWB on Mac are only present in the ASWB Mac build"""

    mac_artifact = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    mac_zip = zipfile.ZipFile(mac_artifact)
    studio_vmoptions = mac_zip.read(next(i for i in mac_zip.infolist() if i.filename.endswith("studio.vmoptions")))

    vmoption = "-Dandroid.adb.path=/usr/bin/adb"
    self.assertEqual(vmoption in studio_vmoptions, aswb)

  def test_custom_vmoptions_linux(self):
    """Tests that vmoptions specific to ASWB on Linux are only present in the ASWB Linux build"""

    linux_artifact = os.path.join(dist_dir, self.artifact_prefix() + build + ".tar.gz")
    tar = tarfile.open(linux_artifact, "r:gz")
    studio_sh_contents = tar.extractfile(tar.getmember("android-studio/bin/studio.sh")).read().split(" ")

    vmoption = "-Dandroid.adb.path=/usr/bin/adb"
    self.assertEqual(vmoption in studio_sh_contents, aswb)
    if aswb:
      self.assertEqual("-Dstudio.projectview=true" in studio_sh_contents, True)

  def test_kotlin_plugin_not_duplicated(self):
    # Motive: bundling the Kotlin plugin is handled specially in BaseIdeaProperties.groovy
    mac_artifact = os.path.join(dist_dir, self.artifact_prefix() + build + ".mac.zip")
    mac_zip = zipfile.ZipFile(mac_artifact)
    kotlin_plugin_count = sum(name.endswith("kotlin-plugin.jar") for name in mac_zip.namelist())
    self.assertEqual(kotlin_plugin_count, 1)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', required = True)
    parser.add_argument('--dist', required = True)
    parser.add_argument('--build', required = True)
    parser.add_argument('--aswb', required = False)
    parser.add_argument('unittest_args', nargs='*')

    args = parser.parse_args()
    out_dir = args.out
    dist_dir = args.dist
    build = args.build
    aswb = args.aswb == "true"

    sys.argv[1:] = args.unittest_args
    unittest.main()
