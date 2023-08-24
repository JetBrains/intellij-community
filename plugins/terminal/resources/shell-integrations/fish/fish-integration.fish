if test -n "$JEDITERM_SOURCE"
  source "$JEDITERM_SOURCE"
  set -e JEDITERM_SOURCE
end

function override_jb_variables
  if not type "string" > /dev/null
    return
  end
  for variable in (env)
    set name_and_value (string split -m 1 "=" -- $variable)
    set name $name_and_value[1]
    set value $name_and_value[2]
    if string match -q -- "_INTELLIJ_FORCE_SET_*" $name
      set new_name (string sub -s 21 -- $name)
      if [ $new_name ]
        if [ $new_name = "PATH" ]; or [ $new_name = "CDPATH" ]; or [ $new_name = "MANPATH" ]
            set -x $new_name (string split ":" -- $value)
        else
            set -x $new_name $value
        end
      end
    end

    if string match -q -- "_INTELLIJ_FORCE_PREPEND_*" $name
      set new_name (string sub -s 25 -- $name)
      if [ $new_name ]
        if [ $new_name = "PATH" ]; or [ $new_name = "CDPATH" ]; or [ $new_name = "MANPATH" ]
            set -x $new_name (string split ":" -- "$value$$new_name")
        else
            set -x $new_name "$value$$new_name"
        end
      end
    end
  end
end

override_jb_variables

# (status dirname) is shorter, but it is available since Fish 3.2.0 (released March 1, 2021)
set -l thisScriptParentDir (dirname (status --current-filename))
set -l commandBlockSupportScript "$thisScriptParentDir/command-block-support.fish"
if test -e "$commandBlockSupportScript"
  source "$commandBlockSupportScript"
end
