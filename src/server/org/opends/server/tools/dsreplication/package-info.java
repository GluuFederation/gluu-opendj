/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */



/**
 * Defines the  classes that are you used by the replication
 * command lines.  This includes the command line parsers
 * (ReplicationCliParser), the classes that actually execute the configuration
 * operations (ReplicationCliMain), the enumeration that defines the return
 * codes of the command-line (ReplicationCliReturnCode), a particular exception
 * used only for the package (ReplicationCliException) and the different data
 * models that represent the data provided by the user directly as command-line
 * parameters and also interactively.
 * */
package org.opends.server.tools.dsreplication;
