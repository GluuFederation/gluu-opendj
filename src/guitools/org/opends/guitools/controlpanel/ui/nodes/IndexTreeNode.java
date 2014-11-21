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

package org.opends.guitools.controlpanel.ui.nodes;

import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;

/**
 * A node representing a 'normal' index.  It is used in the 'Manage Index' tree.
 *
 */
public class IndexTreeNode extends AbstractIndexTreeNode
{
  private static final long serialVersionUID = -3527916032758373700L;
  private IndexDescriptor index;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param index the index associated with the node.
   */
  public IndexTreeNode(String name, IndexDescriptor index)
  {
    super(name);
    this.index = index;
  }

  /**
   * Returns the index descriptor associated with the node.
   * @return the index descriptor associated with the node.
   */
  public IndexDescriptor getIndex()
  {
    return index;
  }
}
