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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.server.authorization.dseecompat;

import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.workflowelement.localbackend.*;

/**
 * The AciLDAPOperationContainer is an AciContainer
 * extended class that wraps each LDAP operation being
 * evaluated or tested for target matched of an ACI.
 */
public class AciLDAPOperationContainer extends AciContainer  {

    /**
     * Constructor interface for all currently supported LDAP operations.
     * @param operation The compare operation to evaluate.
     * @param rights The rights of a compare operation.
     * @param entry The entry for evaluation.
     */
    public AciLDAPOperationContainer(Operation operation,
      int rights, Entry entry)
    {
      super(operation, rights, entry);
    }


    /**
     * Constructor interface for the compare operation.
     * @param operation The compare operation to evaluate.
     * @param rights  The rights of a compare operation.
     */
    public AciLDAPOperationContainer(LocalBackendCompareOperation operation,
        int rights)
    {
      super(operation, rights, operation.getEntryToCompare());
    }


    /**
     * Constructor interface for evaluation general purpose Operation, entry and
     * rights..
     *
     * @param operation The operation to use in the evaluation.
     * @param e The entry for evaluation.
     * @param authInfo The authentication information to use in the evaluation.
     * @param rights The rights of the operation.
     */
    public AciLDAPOperationContainer(Operation operation, Entry e,
                                     AuthenticationInfo authInfo,
                                     int rights) {
      super(operation, e, authInfo, rights);
    }


    /**
     * Constructor interface for evaluation of a control.
     *
     * @param operation The operation to use in the evaluation.
     * @param e An entry built especially for evaluation.
     * @param c The control to evaluate.
     * @param rights The rights of a control.
     */
    public AciLDAPOperationContainer(Operation operation, Entry e, Control c,
                                     int rights) {
      super(operation, rights, e );
      setControlOID(c.getOID());
    }

    /**
     * Constructor interface for evaluation of the extended operation.
     *
     * @param operation  The extended operation to evaluate.
     * @param e  An entry built especially for evaluation.
     * @param rights The rights of a extended operation.
     */
    public AciLDAPOperationContainer(ExtendedOperation operation, Entry e,
                                     int rights) {
      super(operation, rights, e );
      setExtOpOID(operation.getRequestOID());
    }

    /**
     * Constructor interface for the add operation.
     * @param operation The add operation to evaluate.
     * @param rights  The rights of an add operation.
     */
    public AciLDAPOperationContainer(LocalBackendAddOperation operation,
        int rights)
    {
        super(operation, rights, operation.getEntryToAdd());
    }

    /**
     * Constructor interface for the delete operation.
     * @param operation The add operation to evaluate.
     * @param rights  The rights of a delete operation.
     */
    public AciLDAPOperationContainer(LocalBackendDeleteOperation operation,
                                     int rights) {
        super(operation, rights, operation.getEntryToDelete());
    }

    /**
     * Constructor interface for the modify operation.
     * @param rights The rights of modify operation.
     * @param operation The add operation to evaluate.
     */
    public AciLDAPOperationContainer(LocalBackendModifyOperation operation,
        int rights)
    {
        super(operation, rights, operation.getCurrentEntry());
    }

    /**
     * Constructor interface for the modify DN operation.
     * @param operation  The modify DN operation.
     * @param rights  The rights of the modify DN operation.
     * @param entry  The entry to evaluated for this modify DN.
     */
    public AciLDAPOperationContainer(LocalBackendModifyDNOperation operation,
                                     int rights,
                                     Entry entry) {
        super(operation, rights,  entry);
    }
}
