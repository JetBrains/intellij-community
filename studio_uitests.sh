#!/bin/bash -x

readonly out_dir="$1"
readonly dist_dir="$2"
readonly build_number="$3"

readonly script_dir="$(realpath "$(dirname "$0")")"
readonly xvfb_script="$(realpath "${script_dir}/../vendor/google/testing/display/launch_xvfb.sh")"
readonly java="$(realpath "${script_dir}/../../prebuilts/studio/jdk/linux/jre/bin/java")"
readonly ant_jar="${script_dir}/lib/ant/lib/ant-launcher.jar"
readonly test_groups="${UI_TEST_GROUPS:-DEFAULT,UNRELIABLE}"

export DISPLAY=:10

"${xvfb_script}" "${DISPLAY}" "$(realpath "${script_dir}/../..")" &
readonly xvfb_script_pid=$!

(cd "${script_dir}" && "${java}" -jar "${ant_jar}" -Dbundle.gradle.plugin=true "-Dui.test.groups=${test_groups}" uitest)
readonly ant_exit_status=$?

if [[ -d "${dist_dir}" ]]; then
  mkdir -p "${dist_dir}/studio_uitests"
  for f in \
      "${script_dir}"/*_pid* \
      "${script_dir}/androidStudio/gui-tests/failures" \
      "${script_dir}/androidStudio/gui-tests/system/log"; do
    if [[ -e "${f}" ]]; then
      cp -a "${f}" "${dist_dir}/studio_uitests"
    fi
  done

  # on AB/ATP, put JUnit XML in place for junit-xml-forwarding
  mkdir -p "${dist_dir}"/host{,-unreliable}-test-reports
  mv "${script_dir}/TEST-UNRELIABLE.xml" "${dist_dir}/host-unreliable-test-reports"
  mv "${script_dir}"/TEST-*.xml "${dist_dir}/host-test-reports"
fi

kill -1 "${xvfb_script_pid}"
exit "${ant_exit_status}"
