# export GMAIL_PASSWORD=password123 && sh run.sh

groovy list.groovy   
groovy list_calendars.groovy | column -t -s "::" | sort -h

groovy gcaltaskwarrior.groovy | column -t -s "::" | sort -h
#groovy mail.groovy   