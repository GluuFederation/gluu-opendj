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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.admin;



import org.opends.server.types.DN;



/**
 * A common base interface for all server managed object
 * configurations.
 */
public interface Configuration {

  /**
   * Gets the DN of the LDAP entry associated with this configuration.
   *
   * @return Returns the DN of the LDAP entry associated with this
   *         configuration.
   */
  DN dn();



  /**
   * Gets the configuration class associated with this configuration.
   *
   * @return Returns the configuration class associated with this
   *         configuration.
   */
  Class<? extends Configuration> configurationClass();
}
