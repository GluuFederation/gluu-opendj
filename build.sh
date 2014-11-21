#!/bin/sh

# CDDL HEADER START
#
# The contents of this file are subject to the terms of the
# Common Development and Distribution License, Version 1.0 only
# (the "License").  You may not use this file except in compliance
# with the License.
#
# You can obtain a copy of the license at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE
# or https://OpenDS.dev.java.net/OpenDS.LICENSE.
# See the License for the specific language governing permissions
# and limitations under the License.
#
# When distributing Covered Code, include this CDDL HEADER in each
# file and include the License file at
# trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
# add the following below this CDDL HEADER, with the fields enclosed
# by brackets "[]" replaced with your own identifying information:
#      Portions Copyright [yyyy] [name of copyright owner]
#
# CDDL HEADER END
#
#
#      Copyright 2008-2010 Sun Microsystems, Inc.


# Change to the location of this build script.
cd `dirname $0`


# See if JAVA_HOME is set.  If not, then see if there is a java executable in
# the path and try to figure it out.
if test -z "${JAVA_HOME}"
then
  JAVA_HOME=`java -cp resource FindJavaHome`
  if test -z "${JAVA_HOME}"
  then
    echo "Please set JAVA_HOME to the root of a Java 5.0 installation."
    exit 1
  else
    export JAVA_HOME
  fi
fi


# Make sure that the JUnit JAR file is in the CLASSPATH so that ant will be
# able to find it.
CLASSPATH=`pwd`/ext/junit.jar
export CLASSPATH


ANT_HOME=`pwd`/ext/ant
export ANT_HOME
# Execute the ant script and pass it any additional command-line arguments.
ext/ant/bin/ant --noconfig ${*}

