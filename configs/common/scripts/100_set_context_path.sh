#!/bin/bash

if [[ -n "$JOD_OHJAAJA_CMS_CONTEXT" ]]; then

  echo "Set CMS context to ${JOD_OHJAAJA_CMS_CONTEXT}"
  mv ${LIFERAY_HOME}/tomcat/webapps/ROOT/ ${LIFERAY_HOME}/tomcat/webapps/${JOD_OHJAAJA_CMS_CONTEXT} \
  && mv ${LIFERAY_HOME}/tomcat/conf/Catalina/localhost/ROOT.xml ${LIFERAY_HOME}/tomcat/conf/Catalina/localhost/${JOD_OHJAAJA_CMS_CONTEXT}.xml \
  && rm -rf ${LIFERAY_HOME}/tomcat/work/Catalina/localhost/ROOT/ \
  && sed -i "s|/webapps/ROOT|/webapps/$JOD_OHJAAJA_CMS_CONTEXT|" tomcat/conf/catalina.properties
else
  echo "Environment variable JOD_OHJAAJA_CMS_CONTEXT is not set. "
fi
