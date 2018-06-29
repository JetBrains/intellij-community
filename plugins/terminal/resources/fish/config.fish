if test -n "$OLD_XDG_CONFIG_HOME"
  set XDG_CONFIG_HOME "$OLD_XDG_CONFIG_HOME"
else
  set -e XDG_CONFIG_HOME
end

if test -d ~/.config/fish/functions
  for f in ~/.config/fish/functions/*.fish
    source $f
  end
end

if test -f ~/.config/fish/config.fish
  . ~/.config/fish/config.fish
end

if test -n "$JEDITERM_USER_RCFILE"
  . "$JEDITERM_USER_RCFILE"
  set -e JEDITERM_USER_RCFILE
end

if test -n "$JEDITERM_SOURCE"
  . "$JEDITERM_SOURCE"
  set -e JEDITERM_SOURCE
end

function override_jb_variables
  for variable in (env)
  	set name_and_value (string split -m 2 "=" -- $variable)
  	set name $name_and_value[1]
  	set value $name_and_value[2]
  	if string match -q -- "_INTELLIJ_FORCE_SET_*" $name
  		set new_name (string sub -s 21 -- $name)
  		if [ $new_name ]
  			set -x $new_name $value
  		end
  	end
  end
end

override_jb_variables