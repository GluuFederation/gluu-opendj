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
 *      Portions Copyright 2013 ForgeRock AS.
 */

package org.opends.quicksetup.installer.ui;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.UserData;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.installer.AuthenticationData;
import org.opends.quicksetup.installer.DataReplicationOptions;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.LabelFieldDescriptor;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;

/**
 * This class is used to display the replication options for the server
 * that is being installed.
 */
public class DataReplicationPanel extends QuickSetupStepPanel
{
  private static final long serialVersionUID = -1721551487477733587L;
  private Component lastFocusComponent;
  private UserData defaultUserData;

  private JRadioButton rbStandalone;
  private JRadioButton rbReplicated;
  private JCheckBox cbSecureReplication;
  private JCheckBox cbTopologyExists;
  private HashMap<FieldName, JLabel> hmLabels =
    new HashMap<FieldName, JLabel>();
  private HashMap<FieldName, JTextComponent> hmFields =
    new HashMap<FieldName, JTextComponent>();

  /**
   * Constructor of the panel.
   * @param application Application represented by this panel and used to
   * initialize the fields of the panel.
   */
  public DataReplicationPanel(GuiApplication application)
  {
    super(application);
    this.defaultUserData = application.getUserData();
    populateComponentMaps();
    addDocumentListeners();
    addFocusListeners();
    addActionListeners();
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName)
  {
    Object value = null;

    if (fieldName == FieldName.REPLICATION_OPTIONS)
    {
      if (rbStandalone.isSelected())
      {
        value = DataReplicationOptions.Type.STANDALONE;
      }
      else if (cbTopologyExists.isSelected())
      {
        value =
          DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY;
      }
      else
      {
        value = DataReplicationOptions.Type.FIRST_IN_TOPOLOGY;
      }
    }
    else if (fieldName == FieldName.REPLICATION_SECURE)
    {
      if (cbSecureReplication.isSelected())
      {
        value = Boolean.TRUE;
      }
      else
      {
        value = Boolean.FALSE;
      }
    }
    else
    {
      JTextComponent field = getField(fieldName);
      if (field != null)
      {
        value = field.getText();
      }
    }

    return value;
  }

  /**
   * {@inheritDoc}
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    JLabel label = getLabel(fieldName);
    if (label != null)
    {
      UIFactory.TextStyle style;

      if (invalid)
      {
        style = UIFactory.TextStyle.SECONDARY_FIELD_INVALID;
      } else
      {
        style = UIFactory.TextStyle.SECONDARY_FIELD_VALID;
      }

      UIFactory.setTextStyle(label, style);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel()
  {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setOpaque(false);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    panel.add(rbStandalone, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_RADIOBUTTON;
    panel.add(rbReplicated, gbc);

    gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    JPanel auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    panel.add(auxPanel, gbc);
    panel.add(cbTopologyExists, gbc);
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.gridwidth = 4;
    gbc.weightx = 0.0;
    gbc.insets.left = 0;
    gbc.anchor = GridBagConstraints.WEST;
    auxPanel.add(getLabel(FieldName.REPLICATION_PORT), gbc);

    gbc.gridwidth--;
    gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    auxPanel.add(getField(FieldName.REPLICATION_PORT), gbc);

    gbc.gridwidth = GridBagConstraints.RELATIVE;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 0.0;
    auxPanel.add(cbSecureReplication, gbc);

    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets.left = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    auxPanel.add(Box.createHorizontalGlue(), gbc);

    auxPanel = new JPanel(new GridBagLayout());
    auxPanel.setOpaque(false);
    gbc.insets.left = 2 * UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    panel.add(auxPanel, gbc);

    // Add the server location widgets
    FieldName[] fields =
    {
      FieldName.REMOTE_SERVER_HOST,
      FieldName.REMOTE_SERVER_PORT,
      FieldName.REMOTE_SERVER_DN,
      FieldName.REMOTE_SERVER_PWD
    };

    gbc.insets = UIFactory.getEmptyInsets();
    for (int i=0; i<fields.length; i++)
    {
      if (i != 0)
      {
        gbc.insets.top = UIFactory.TOP_INSET_SECONDARY_FIELD;
      }
      else
      {
        gbc.insets.top = 0;
      }
      gbc.gridwidth = GridBagConstraints.RELATIVE;
      gbc.weightx = 0.0;
      gbc.insets.left = 0;
      gbc.anchor = GridBagConstraints.WEST;
      auxPanel.add(getLabel(fields[i]), gbc);

      JPanel aux2Panel = new JPanel(new GridBagLayout());
      aux2Panel.setOpaque(false);

      if (fields[i] == FieldName.REMOTE_SERVER_PORT)
      {
        gbc.gridwidth = 3;
      }
      else
      {
        gbc.gridwidth = GridBagConstraints.RELATIVE;
      }
      gbc.insets.left = UIFactory.LEFT_INSET_SECONDARY_FIELD;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 0.0;
      aux2Panel.add(getField(fields[i]), gbc);

      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.insets.left = 0;
      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      aux2Panel.add(Box.createHorizontalGlue(), gbc);

      gbc.weightx = 1.0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.insets = UIFactory.getEmptyInsets();
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      auxPanel.add(aux2Panel, gbc);
    }

    addVerticalGlue(panel);

    return panel;
  }

  /**
   * {@inheritDoc}
   */
  protected Message getInstructions()
  {
    return INFO_DATA_REPLICATION_OPTIONS_PANEL_INSTRUCTIONS.get();
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTitle()
  {
    return INFO_DATA_REPLICATION_OPTIONS_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void endDisplay()
  {
    if (lastFocusComponent != null)
    {
      lastFocusComponent.requestFocusInWindow();
    }
  }

  /**
   * {@inheritDoc}
   */
  protected Message getTextForIcon(UIFactory.IconType iconType)
  {
    Message text;
    if ((iconType == UIFactory.IconType.WAIT) &&
        rbReplicated.isSelected() && cbTopologyExists.isSelected())
    {
      text = INFO_CONTACTING_SERVER_LABEL.get();
    }
    else
    {
      text = super.getTextForIcon(iconType);
    }
    return text;
  }

  /**
   * Returns the default value for the provided field Name.
   * @param fieldName the field name for which we want to get the default
   * value.
   * @return the default value for the provided field Name.
   */
  private Object getDefaultValue(FieldName fieldName)
  {
    Object value;
    AuthenticationData auth =
      defaultUserData.getReplicationOptions().getAuthenticationData();
    switch (fieldName)
    {
    case REPLICATION_PORT:
      value = defaultUserData.getReplicationOptions().getReplicationPort();
      break;

    case REMOTE_SERVER_DN:
      value = auth.getDn();
      break;

    case REMOTE_SERVER_PWD:
      value = auth.getPwd();
      break;

    case REMOTE_SERVER_HOST:
      value = auth.getHostName();
      break;

    case REMOTE_SERVER_PORT:
      value = auth.getPort();
      break;

    case REPLICATION_OPTIONS:
      value = defaultUserData.getReplicationOptions().getType();
      break;

    default:
      throw new IllegalArgumentException("Unknown field name: " +
          fieldName);
    }

    return value;
  }

  /**
   * Returns the default string value for the provided field Name.
   * @param fieldName the field name for which we want to get the default
   * string value.
   * @return the default value for the provided field Name.
   */
  private String getDefaultStringValue(FieldName fieldName)
  {
    String value = null;

    Object v = getDefaultValue(fieldName);
    if (v != null)
    {
      if (v instanceof String)
      {
        value = (String) v;
      } else
      {
        value = String.valueOf(v);
      }
    }
    return value;
  }

  /**
   * Creates the components and populates the Maps with them.
   */
  private void populateComponentMaps()
  {
    HashMap<FieldName, LabelFieldDescriptor> hm =
        new HashMap<FieldName, LabelFieldDescriptor>();

    hm.put(FieldName.REPLICATION_PORT, new LabelFieldDescriptor(
        INFO_REPLICATION_PORT_LABEL.get(),
        INFO_REPLICATION_PORT_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY,
        UIFactory.PORT_FIELD_SIZE));

    hm.put(FieldName.REMOTE_SERVER_DN, new LabelFieldDescriptor(
        INFO_REMOTE_SERVER_DN_LABEL.get(),
        INFO_REMOTE_SERVER_DN_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY, UIFactory.DN_FIELD_SIZE));

    hm.put(FieldName.REMOTE_SERVER_PWD, new LabelFieldDescriptor(
        INFO_REMOTE_SERVER_PWD_LABEL.get(),
        INFO_REMOTE_SERVER_PWD_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.PASSWORD,
        LabelFieldDescriptor.LabelType.SECONDARY,
        UIFactory.PASSWORD_FIELD_SIZE));

    hm.put(FieldName.REMOTE_SERVER_HOST, new LabelFieldDescriptor(
        INFO_REMOTE_SERVER_HOST_LABEL.get(),
        INFO_REMOTE_SERVER_HOST_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY,
        UIFactory.HOST_FIELD_SIZE));

    hm.put(FieldName.REMOTE_SERVER_PORT, new LabelFieldDescriptor(
        INFO_REMOTE_SERVER_PORT_LABEL.get(),
        INFO_REMOTE_SERVER_PORT_TOOLTIP.get(),
        LabelFieldDescriptor.FieldType.TEXTFIELD,
        LabelFieldDescriptor.LabelType.SECONDARY,
        UIFactory.PORT_FIELD_SIZE));

    for (FieldName fieldName : hm.keySet())
    {
      JTextComponent field;
      LabelFieldDescriptor desc = hm.get(fieldName);

      String defaultValue = getDefaultStringValue(fieldName);
      field = UIFactory.makeJTextComponent(desc, defaultValue);

      hmFields.put(fieldName, field);

      JLabel l = UIFactory.makeJLabel(desc);

      l.setLabelFor(field);

      hmLabels.put(fieldName, l);
    }

    ButtonGroup buttonGroup = new ButtonGroup();
    rbStandalone =
      UIFactory.makeJRadioButton(INFO_STANDALONE_SERVER_LABEL.get(),
          INFO_STANDALONE_SERVER_TOOLTIP.get(),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbStandalone.setOpaque(false);
    rbReplicated =
      UIFactory.makeJRadioButton(INFO_REPLICATED_SERVER_LABEL.get(),
          INFO_REPLICATED_SERVER_TOOLTIP.get(),
          UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    rbReplicated.setOpaque(false);
    buttonGroup.add(rbStandalone);
    buttonGroup.add(rbReplicated);

    DataReplicationOptions.Type type =
      defaultUserData.getReplicationOptions().getType();
    cbTopologyExists = UIFactory.makeJCheckBox(INFO_TOPOLOGY_EXISTS_LABEL.get(),
        INFO_TOPOLOGY_EXISTS_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    cbTopologyExists.setOpaque(false);
    rbStandalone.setSelected(type ==
      DataReplicationOptions.Type.STANDALONE);
    rbReplicated.setSelected(type !=
      DataReplicationOptions.Type.STANDALONE);
    cbSecureReplication = UIFactory.makeJCheckBox(
        INFO_SECURE_REPLICATION_LABEL.get(),
        INFO_SECURE_REPLICATION_TOOLTIP.get(),
        UIFactory.TextStyle.SECONDARY_FIELD_VALID);
    cbSecureReplication.setSelected(
        defaultUserData.getReplicationOptions().useSecureReplication());
    cbTopologyExists.setSelected(type ==
      DataReplicationOptions.Type.IN_EXISTING_TOPOLOGY);
    checkEnablingState();
  }

  /**
   * Adds all the required document listeners to the fields.
   */
  private void addDocumentListeners()
  {
    FieldName[] fields = {
        FieldName.REMOTE_SERVER_DN,
        FieldName.REMOTE_SERVER_PWD,
        FieldName.REMOTE_SERVER_HOST,
        FieldName.REMOTE_SERVER_PORT
    };
    for (FieldName field : fields) {
      JTextComponent tf = getField(field);
      tf.getDocument().addDocumentListener(new DocumentListener() {
        public void changedUpdate(DocumentEvent ev) {
          if (!rbReplicated.isSelected()) {
            rbReplicated.setSelected(true);
          }
          if (!cbTopologyExists.isSelected()) {
            cbTopologyExists.setSelected(true);
          }
        }

        public void insertUpdate(DocumentEvent ev) {
          changedUpdate(ev);
        }

        public void removeUpdate(DocumentEvent ev) {
          changedUpdate(ev);
        }
      });
    }
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addFocusListeners()
  {
    final FocusListener l = new FocusListener()
    {
      public void focusGained(FocusEvent e)
      {
        lastFocusComponent = e.getComponent();
        if (lastFocusComponent instanceof JTextComponent)
        {
          rbReplicated.setSelected(true);
          if (lastFocusComponent != getField(FieldName.REPLICATION_PORT))
          {
            cbTopologyExists.setSelected(true);
          }
        }
      }

      public void focusLost(FocusEvent e)
      {
      }
    };

    for (JTextComponent tf : hmFields.values())
    {
      tf.addFocusListener(l);
    }
    rbReplicated.addFocusListener(l);
    rbStandalone.addFocusListener(l);
    cbTopologyExists.addFocusListener(l);
    cbSecureReplication.addFocusListener(l);

    lastFocusComponent = rbStandalone;
  }

  /**
   * Adds the required focus listeners to the fields.
   */
  private void addActionListeners()
  {
    final ActionListener l = new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        checkEnablingState();
        ButtonEvent be = new ButtonEvent(ev.getSource(),
            ButtonName.INPUT_PANEL_BUTTON);
        notifyButtonListeners(be);
      }
    };
    rbReplicated.addActionListener(l);
    rbStandalone.addActionListener(l);
    cbTopologyExists.addActionListener(l);
    cbTopologyExists.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        if (cbTopologyExists.isSelected())
        {
          rbReplicated.setSelected(true);
        }
      }
    });
  }

  /**
   * Enables/disables the fields.
   */
  private void checkEnablingState()
  {
    boolean enableFields = rbReplicated.isSelected() &&
    cbTopologyExists.isSelected();

    for (JTextComponent tf : hmFields.values())
    {
      tf.setEnabled(enableFields);
    }

    for (JLabel l : hmLabels.values())
    {
      l.setEnabled(enableFields);
    }

    cbTopologyExists.setEnabled(rbReplicated.isSelected());
    getLabel(FieldName.REPLICATION_PORT).setEnabled(rbReplicated.isSelected());
    getField(FieldName.REPLICATION_PORT).setEnabled(rbReplicated.isSelected());
    cbSecureReplication.setEnabled(rbReplicated.isSelected());
  }

  /**
   * Returns the label associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the JLabel.
   * @return the label associated with the given field name.
   */
  private JLabel getLabel(FieldName fieldName)
  {
    return hmLabels.get(fieldName);
  }

  /**
   * Returns the JTextComponent associated with the given field name.
   * @param fieldName the field name for which we want to retrieve the
   * JTextComponent.
   * @return the JTextComponent associated with the given field name.
   */
  private JTextComponent getField(FieldName fieldName)
  {
    return hmFields.get(fieldName);
  }
}
