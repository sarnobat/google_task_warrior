cp .groovy/lib/*jar  ~/.groovy/lib/
mv ~/.groovy/lib/json-20090211.jar ~/.groovy/lib/json-20090211.jar.disabled 2> /dev/null
groovy list_display.groovy
#groovy list_update.groovy &
