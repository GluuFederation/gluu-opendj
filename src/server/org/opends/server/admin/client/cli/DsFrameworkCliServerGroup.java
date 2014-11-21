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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.cli;

import static org.opends.messages.AdminMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.ToolConstants.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.Rdn;


import org.opends.admin.ads.ADSContext;
import org.opends.admin.ads.ADSContextException;
import org.opends.admin.ads.ADSContext.ServerGroupProperty;
import org.opends.admin.ads.ADSContext.ServerProperty;
import org.opends.admin.ads.ADSContextException.ErrorType;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;

import static org.opends.server.admin.client.cli.DsFrameworkCliReturnCode.*;
/**
 * This class is handling server group CLI.
 */
public class DsFrameworkCliServerGroup implements DsFrameworkCliSubCommandGroup
{

  /**
   * End Of Line.
   */
  private String EOL = System.getProperty("line.separator");

  /**
   * The subcommand Parser.
   */
  DsFrameworkCliParser argParser ;

  /**
   * The verbose argument.
   */
  BooleanArgument verboseArg ;

  /**
   * The enumeration containing the different subCommand names.
   */
  private enum SubCommandNameEnum
  {
    /**
     * The create-group subcommand.
     */
    CREATE_GROUP("create-group"),

    /**
     * The delete-group subcommand.
     */
    DELETE_GROUP("delete-group"),

    /**
     * The modify-group subcommand.
     */
    MODIFY_GROUP("modify-group"),

    /**
     * The list-groups subcommand.
     */
    LIST_GROUPS("list-groups"),

    /**
     * The list-members subcommand.
     */
    LIST_MEMBERS("list-members"),

    /**
     * The list-membership subcommand.
     */
    LIST_MEMBERSHIP("list-membership"),

    /**
     * The add-to-group subcommand.
     */
    ADD_TO_GROUP("add-to-group"),

    /**
     * The remove-from-group subcommand.
     */
    REMOVE_FROM_GROUP("remove-from-group");

    // String representation of the value.
    private final String name;

    // Private constructor.
    private SubCommandNameEnum(String name)
    {
      this.name = name;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      return name;
    }

    // A lookup table for resolving a unit from its name.
    private static final List<String> nameToSubCmdName ;
    static
    {
      nameToSubCmdName = new ArrayList<String>();

      for (SubCommandNameEnum subCmd : SubCommandNameEnum.values())
      {
        nameToSubCmdName.add(subCmd.toString());
      }
    }
    public static boolean  isSubCommand(String name)
    {
      return nameToSubCmdName.contains(name);
    }
  }

  /**
   * The 'create-group' subcommand.
   */
  public SubCommand createGroupSubCmd;

  /**
   * The 'description' argument of the 'create-group' subcommand.
   */
  private StringArgument createGroupDescriptionArg;

  /**
   * The 'group-name' argument of the 'create-group' subcommand.
   */
  private StringArgument createGroupGroupNameArg;

  /**
   * The 'modify-group' subcommand.
   */
  private SubCommand modifyGroupSubCmd;

  /**
   * The 'description' argument of the 'modify-group' subcommand.
   */
  private StringArgument modifyGroupDescriptionArg;

  /**
   * The 'group-id' argument of the 'modify-group' subcommand.
   */
  private StringArgument modifyGroupGroupIdArg;

  /**
   * The 'group-name' argument of the 'modify-group' subcommand.
   */
  private StringArgument modifyGroupGroupNameArg;

  /**
   * The 'delete-group' subcommand.
   */
  private SubCommand deleteGroupSubCmd;

  /**
   * The 'group-name' argument of the 'delete-group' subcommand.
   */
  private StringArgument deleteGroupGroupNameArg;

  /**
   * The 'list-group' subcommand.
   */
  private SubCommand listGroupSubCmd;

  /**
   * The 'add-to-group' subcommand.
   */
  private SubCommand addToGroupSubCmd;

  /**
   * The 'group-name' argument of the 'add-to-group' subcommand.
   */
  private StringArgument addToGroupGroupNameArg;

  /**
   * The 'member-id' argument of the 'add-to-group' subcommand.
   */
  private StringArgument addToGoupMemberNameArg;

  /**
   * The 'remove-from-group' subcommand.
   */
  private SubCommand removeFromGroupSubCmd;

  /**
   * The 'group-name' argument of the 'remove-from-group' subcommand.
   */
  private StringArgument removeFromGroupGroupNameArg;

  /**
   * The 'member-id' argument of the 'remove-from-group' subcommand.
   */
  private StringArgument removeFromGoupMemberNameArg;

  /**
   * The 'list-members' subcommand.
   */
  private SubCommand listMembersSubCmd;

  /**
   * The 'group-name' argument of the 'list-members' subcommand.
   */
  private StringArgument listMembersGroupNameArg;

  /**
   * The 'list-membership' subcommand.
   */
  private SubCommand listMembershipSubCmd;

  /**
   * The 'member-name' argument of the 'list-membership' subcommand.
   */
  private StringArgument listMembershipMemberNameArg;


  /**
   * Association between ADSContext enum and display field.
   */
  private HashMap<ServerGroupProperty, String> attributeDisplayName;

  /**
   * The subcommand list.
   */
  private HashSet<SubCommand> subCommands = new HashSet<SubCommand>();

  /**
   * Indicates whether this subCommand should be hidden in the usage
   * information.
   */
  private boolean isHidden;

  /**
   * The subcommand group name.
   */
  private String groupName;

  /**
   * Get the display attribute name for a given attribute.
   * @param prop The server property
   * @return the display attribute name for a given attribute
   */
  public String getAttributeDisplayName(ServerGroupProperty prop)
  {
    return attributeDisplayName.get(prop);
  }

  /**
   * {@inheritDoc}
   */
  public Set<SubCommand> getSubCommands()
  {
    return subCommands;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isHidden()
  {
    return isHidden;
  }

  /**
   * {@inheritDoc}
   */
  public String getGroupName()
  {
    return groupName ;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeCliGroup(DsFrameworkCliParser argParser,
      BooleanArgument verboseArg)
      throws ArgumentException
  {
    this.verboseArg = verboseArg ;
    isHidden = false ;
    groupName = "server-group";
    this.argParser = argParser;


    // Create-group subcommand
    createGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.CREATE_GROUP.toString(),
        INFO_ADMIN_SUBCMD_CREATE_GROUP_DESCRIPTION.get());
    subCommands.add(createGroupSubCmd);

    createGroupDescriptionArg = new StringArgument("description",
        OPTION_SHORT_DESCRIPTION, OPTION_LONG_DESCRIPTION, false, false,
        true, INFO_DESCRIPTION_PLACEHOLDER.get(), "", null,
        INFO_ADMIN_ARG_DESCRIPTION_DESCRIPTION.get());
    createGroupSubCmd.addArgument(createGroupDescriptionArg);

    createGroupGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_CREATE_GROUP_GROUPNAME_DESCRIPTION.get());
    createGroupSubCmd.addArgument(createGroupGroupNameArg);

    // modify-group
    modifyGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.MODIFY_GROUP.toString(),
        INFO_ADMIN_SUBCMD_MODIFY_GROUP_DESCRIPTION.get());
    subCommands.add(modifyGroupSubCmd);

    modifyGroupDescriptionArg = new StringArgument("new-description",
        OPTION_SHORT_DESCRIPTION, OPTION_LONG_DESCRIPTION, false, false,
        true, INFO_DESCRIPTION_PLACEHOLDER.get(), "", null,
        INFO_ADMIN_ARG_NEW_DESCRIPTION_DESCRIPTION.get());
    modifyGroupSubCmd.addArgument(modifyGroupDescriptionArg);

    modifyGroupGroupIdArg = new StringArgument("new-groupName",
        OPTION_SHORT_NEWGROUPNAME, OPTION_LONG_NEWGROUPNAME, false, false, true,
        INFO_GROUPNAME_PLACEHOLDER.get(), "", null,
        INFO_ADMIN_ARG_NEW_GROUPNAME_DESCRIPTION.get());
    modifyGroupSubCmd.addArgument(modifyGroupGroupIdArg);

    modifyGroupGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_GROUPNAME_DESCRIPTION.get());
    modifyGroupSubCmd.addArgument(modifyGroupGroupNameArg);

    // delete-group
    deleteGroupSubCmd = new SubCommand(argParser,SubCommandNameEnum.DELETE_GROUP
        .toString(), INFO_ADMIN_SUBCMD_DELETE_GROUP_DESCRIPTION.get());
    subCommands.add(deleteGroupSubCmd);

    deleteGroupGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_GROUPNAME_DESCRIPTION.get());
    deleteGroupSubCmd.addArgument(deleteGroupGroupNameArg);

    // list-groups
    listGroupSubCmd = new SubCommand(argParser, "list-groups",
        INFO_ADMIN_SUBCMD_LIST_GROUPS_DESCRIPTION.get());
    subCommands.add(listGroupSubCmd);

    // add-to-group
    addToGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.ADD_TO_GROUP.toString(),
        INFO_ADMIN_SUBCMD_ADD_TO_GROUP_DESCRIPTION.get());
    subCommands.add(addToGroupSubCmd);

    addToGoupMemberNameArg = new StringArgument("memberName",
        OPTION_SHORT_MEMBERNAME, OPTION_LONG_MEMBERNAME, true, true,
        INFO_MEMBERNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_ADD_MEMBERNAME_DESCRIPTION.get());
    addToGroupSubCmd.addArgument(addToGoupMemberNameArg);

    addToGroupGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_GROUPNAME_DESCRIPTION.get());
    addToGroupSubCmd.addArgument(addToGroupGroupNameArg);

    // remove-from-group
    removeFromGroupSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.REMOVE_FROM_GROUP.toString(),
        INFO_ADMIN_SUBCMD_REMOVE_FROM_GROUP_DESCRIPTION.get());
    subCommands.add(removeFromGroupSubCmd);

    removeFromGoupMemberNameArg = new StringArgument("memberName",
        OPTION_SHORT_MEMBERNAME, OPTION_LONG_MEMBERNAME, true, true,
        INFO_MEMBERNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_REMOVE_MEMBERNAME_DESCRIPTION.get());
    removeFromGroupSubCmd.addArgument(removeFromGoupMemberNameArg);

    removeFromGroupGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_GROUPNAME_DESCRIPTION.get());
    removeFromGroupSubCmd.addArgument(removeFromGroupGroupNameArg);


    // list-members
    listMembersSubCmd = new SubCommand(argParser,SubCommandNameEnum.LIST_MEMBERS
        .toString(), INFO_ADMIN_SUBCMD_LIST_MEMBERS_DESCRIPTION.get());
    subCommands.add(listMembersSubCmd);

    listMembersGroupNameArg = new StringArgument("groupName",
        OPTION_SHORT_GROUPNAME, OPTION_LONG_GROUPNAME, true, true,
        INFO_GROUPNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_GROUPNAME_DESCRIPTION.get());
    listMembersSubCmd.addArgument(listMembersGroupNameArg);

    // list-membership
    listMembershipSubCmd = new SubCommand(argParser,
        SubCommandNameEnum.LIST_MEMBERSHIP.toString(),
        INFO_ADMIN_SUBCMD_LIST_MEMBERSHIP_DESCRIPTION.get());
    subCommands.add(listMembershipSubCmd);

    listMembershipMemberNameArg = new StringArgument("memberName",
        OPTION_SHORT_MEMBERNAME, OPTION_LONG_MEMBERNAME, true, true,
        INFO_MEMBERNAME_PLACEHOLDER.get(),
        INFO_ADMIN_ARG_MEMBERNAME_DESCRIPTION.get());
    listMembershipSubCmd.addArgument(listMembershipMemberNameArg);

    // Create association between ADSContext enum and display field
    attributeDisplayName = new HashMap<ServerGroupProperty, String>();
    attributeDisplayName.put(ServerGroupProperty.UID, OPTION_LONG_GROUPNAME);
    attributeDisplayName.put(ServerGroupProperty.DESCRIPTION,
        OPTION_LONG_DESCRIPTION);
    attributeDisplayName.put(ServerGroupProperty.MEMBERS,
        OPTION_LONG_MEMBERNAME);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isSubCommand(SubCommand subCmd)
  {
      return SubCommandNameEnum.isSubCommand(subCmd.getName());
  }


  /**
   * {@inheritDoc}
   */
  public DsFrameworkCliReturnCode performSubCommand(SubCommand subCmd,
      OutputStream outStream, OutputStream errStream)
      throws ADSContextException, ArgumentException
  {
    ADSContext adsCtx = null ;
    InitialLdapContext ctx = null ;

    DsFrameworkCliReturnCode returnCode = ERROR_UNEXPECTED;
    try
    {
      // -----------------------
      // create-group subcommand
      // -----------------------
      if (subCmd.getName().equals(createGroupSubCmd.getName()))
      {
        String groupId = createGroupGroupNameArg.getValue();
        HashMap<ServerGroupProperty, Object> serverGroupProperties =
          new HashMap<ServerGroupProperty, Object>();

        // get the GROUP_NAME
        serverGroupProperties.put(ServerGroupProperty.UID, groupId);

        // get the Description
        if (createGroupDescriptionArg.isPresent())
        {
          serverGroupProperties.put(ServerGroupProperty.DESCRIPTION,
              createGroupDescriptionArg.getValue());
        }

        // Create the group
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;
        adsCtx.createServerGroup(serverGroupProperties);
        returnCode = SUCCESSFUL;
      }
      // -----------------------
      // delete-group subcommand
      // -----------------------
      else if (subCmd.getName().equals(deleteGroupSubCmd.getName()))
      {
        returnCode = SUCCESSFUL;
        String groupId = deleteGroupGroupNameArg.getValue();
        if (groupId.equals(ADSContext.ALL_SERVERGROUP_NAME))
        {
          return ACCESS_PERMISSION ;
        }
        HashMap<ServerGroupProperty, Object> serverGroupProperties =
          new HashMap<ServerGroupProperty, Object>();

        // Get ADS context
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        // update server Property "GROUPS"
        Set<String> serverList = adsCtx.getServerGroupMemberList(groupId);
        for (String serverId : serverList)
        {
          // serverId contains "cn=" string, just remove it.
          removeServerFromGroup(adsCtx, groupId,serverId.substring(3));
        }

        // Delete the group
        serverGroupProperties.put(ServerGroupProperty.UID, groupId);
        adsCtx.deleteServerGroup(serverGroupProperties);
      }
      // -----------------------
      // list-groups subcommand
      // -----------------------
      else if (subCmd.getName().equals(listGroupSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        Set<Map<ServerGroupProperty, Object>> result = adsCtx
            .readServerGroupRegistry();
        StringBuilder buffer = new StringBuilder();

        // if not verbose mode, print group name (1 per line)
        if (! verboseArg.isPresent())
        {
          for (Map<ServerGroupProperty, Object> groupProps : result)
          {
            // Get the group name
            buffer.append(groupProps.get(ServerGroupProperty.UID));
            buffer.append(EOL);
          }
        }
        else
        {
          // Look for the max group identifier length
          int uidLength = 0 ;
          for (ServerGroupProperty sgp : ServerGroupProperty.values())
          {
            int cur = attributeDisplayName.get(sgp).toString().length();
            if (cur > uidLength)
            {
              uidLength = cur;
            }
          }
          uidLength++;

          for (Map<ServerGroupProperty, Object> groupProps : result)
          {
            // Get the group name
            buffer.append(attributeDisplayName.get(ServerGroupProperty.UID));
            // add space
            int curLen = attributeDisplayName.get(ServerGroupProperty.UID)
                .length();
            for (int i = curLen; i < uidLength; i++)
            {
              buffer.append(" ");
            }
            buffer.append(": ");
            buffer.append(groupProps.get(ServerGroupProperty.UID));
            buffer.append(EOL);

            // Write other props
            for (ServerGroupProperty propName : ServerGroupProperty.values())
            {
              if (propName.compareTo(ServerGroupProperty.UID) == 0)
              {
                // We have already displayed the group Id
                continue;
              }
              buffer.append(attributeDisplayName.get(propName));
              // add space
              curLen = attributeDisplayName.get(propName).length();
              for (int i = curLen; i < uidLength; i++)
              {
                buffer.append(" ");
              }
              buffer.append(": ");

              if (propName.compareTo(ServerGroupProperty.MEMBERS) == 0)
              {
                Set atts = (Set) groupProps.get(propName);
                if (atts != null)
                {
                  boolean indent = false;
                  for (Object att : atts)
                  {
                    if (indent)
                    {
                      buffer.append(EOL);
                      for (int i = 0; i < uidLength + 2; i++)
                      {
                        buffer.append(" ");
                      }
                    }
                    else
                    {
                      indent = true;
                    }
                    buffer.append(att.toString().substring(3));
                  }
                }
              }
              else
              {
                if (groupProps.get(propName) != null)
                {
                  buffer.append(groupProps.get(propName));
                }
              }
              buffer.append(EOL);
            }
            buffer.append(EOL);
          }
        }
        try
        {
          outStream.write(buffer.toString().getBytes());
        }
        catch (IOException e)
        {
        }
        returnCode = SUCCESSFUL;
      }
      // -----------------------
      // modify-group subcommand
      // -----------------------
      else if (subCmd.getName().equals(modifyGroupSubCmd.getName()))
      {
        String groupId = modifyGroupGroupNameArg.getValue();
        HashMap<ServerGroupProperty, Object> serverGroupProperties =
          new HashMap<ServerGroupProperty, Object>();
        HashSet<ServerGroupProperty> serverGroupPropertiesToRemove =
          new HashSet<ServerGroupProperty>();

        Boolean updateRequired = false;
        Boolean removeRequired = false;
        // get the GROUP_ID
        if (modifyGroupGroupIdArg.isPresent())
        {
          // rename the entry !
          serverGroupProperties.put(ServerGroupProperty.UID,
              modifyGroupGroupIdArg.getValue());
          updateRequired = true;
        }
        else
        {
          serverGroupProperties.put(ServerGroupProperty.UID, groupId) ;
        }


        // get the Description
        if (modifyGroupDescriptionArg.isPresent())
        {
          String newDesc = modifyGroupDescriptionArg.getValue();
          if (newDesc.length() == 0)
          {
            serverGroupPropertiesToRemove.add(ServerGroupProperty.DESCRIPTION);
            removeRequired = true;
          }
          else
          {
            serverGroupProperties.put(ServerGroupProperty.DESCRIPTION,
                modifyGroupDescriptionArg.getValue());
            updateRequired = true;
          }
        }


        // Update the server group
        if ( ! (updateRequired || removeRequired ) )
        {
          returnCode = SUCCESSFUL_NOP;
        }

        // We need to perform an update
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        if (updateRequired)
        {
          adsCtx.updateServerGroup(groupId, serverGroupProperties);
        }
        if (removeRequired)
        {
          adsCtx.removeServerGroupProp(groupId,
              serverGroupPropertiesToRemove);
        }

        returnCode = SUCCESSFUL;
      }
      // -----------------------
      // add-to-group subcommand
      // -----------------------
      else if (subCmd.getName().equals(addToGroupSubCmd.getName()))
      {
        String groupId = addToGroupGroupNameArg.getValue();

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        // Check if the server is registered inside to ADS
        Set<Map<ServerProperty, Object>> serverList = adsCtx
            .readServerRegistry();
        boolean found = false ;
        Map<ServerProperty, Object> foundServerProperties = null ;
        for (Map<ServerProperty, Object> serverProperties : serverList)
        {
          String serverId = ADSContext
              .getServerIdFromServerProperties(serverProperties);
          if (addToGoupMemberNameArg.getValue().equals(serverId))
          {
            found = true;
            foundServerProperties = serverProperties ;
            break;
          }
        }
        if ( !found )
        {
          throw new ADSContextException (ErrorType.NOT_YET_REGISTERED) ;
        }

        // Add the server inside the group
        returnCode = addServerTogroup(adsCtx, groupId, foundServerProperties);
      }
      // -----------------------
      // remove-from-group subcommand
      // -----------------------
      else if (subCmd.getName().equals(removeFromGroupSubCmd.getName()))
      {
        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        returnCode = removeServerFromGroup(adsCtx,
            removeFromGroupGroupNameArg.getValue(),
            removeFromGoupMemberNameArg.getValue());
      }
      // -----------------------
      // list-members subcommand
      // -----------------------
      else if (subCmd.getName().equals(listMembersSubCmd.getName()))
      {
        String groupId = listMembersGroupNameArg.getValue();

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        // get the current member list
        Set<String> memberList = adsCtx.getServerGroupMemberList(groupId);
        if (memberList == null)
        {
          returnCode = SUCCESSFUL;
        }
        StringBuilder buffer = new StringBuilder();
        for (String member : memberList)
        {
          // We shouldn't print out the "cn="
          buffer.append(member.substring(3));
          buffer.append(EOL);
        }
        try
        {
          outStream.write(buffer.toString().getBytes());
        }
        catch (IOException e)
        {
        }

        returnCode = SUCCESSFUL;
      }
      // -----------------------
      // list-membership subcommand
      // -----------------------
      else if (subCmd.getName().equals(listMembershipSubCmd.getName()))
      {

        ctx = argParser.getContext(outStream, errStream);
        if (ctx == null)
        {
          return CANNOT_CONNECT_TO_ADS;
        }
        adsCtx = new ADSContext(ctx) ;

        Set<Map<ServerGroupProperty, Object>> result = adsCtx
            .readServerGroupRegistry();
        String MemberId = listMembershipMemberNameArg.getValue();

        StringBuilder buffer = new StringBuilder();
        for (Map<ServerGroupProperty, Object> groupProps : result)
        {
          // Get the group name;
          String groupId = groupProps.get(ServerGroupProperty.UID).toString();

          // look for member list attribute
          for (ServerGroupProperty propName : groupProps.keySet())
          {
            if (propName.compareTo(ServerGroupProperty.MEMBERS) != 0)
            {
              continue;
            }
            // Check if the member list contains the member-id
            Set atts = (Set) groupProps.get(propName);
            for (Object att : atts)
            {
              if (att.toString().substring(3).toLowerCase().equals(
                  MemberId.toLowerCase()))
              {
                buffer.append(groupId);
                buffer.append(EOL);
                break;
              }
            }
            break;
          }
        }
        try
        {
          outStream.write(buffer.toString().getBytes());
        }
        catch (IOException e)
        {
        }
        returnCode = SUCCESSFUL;
      }
      else
      {
        // Should never occurs: If we are here, it means that the code to
        // handle to subcommand is not yet written.
        returnCode = ERROR_UNEXPECTED;
      }
    }
    catch (ADSContextException e)
    {
     if (ctx != null)
     {
       try
       {
         ctx.close();
       }
       catch (NamingException x)
       {
       }
     }
     throw e;
    }

    // Close the connection, if needed
    if (ctx != null)
    {
      try
      {
        ctx.close();
      }
      catch (NamingException x)
      {
      }
    }

    // return part
    return returnCode;

  }

  /**
   * Remove a server from a group.
   *
   * @param adsCtx
   *          The ADS context to use.
   * @param groupId
   *          The group identifier from which a server has to be
   *          remove.
   * @param serverId
   *          The server identifier to be removed.
   * @return The return code.
   * @throws ADSContextException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  static DsFrameworkCliReturnCode removeServerFromGroup(ADSContext adsCtx,
      String groupId, String serverId)
      throws ADSContextException
  {
    DsFrameworkCliReturnCode returnCode = SUCCESSFUL;

    // get the current group member list
    Set<String> memberList = adsCtx.getServerGroupMemberList(groupId);
    if (memberList == null)
    {
      returnCode = NOT_YET_REGISTERED;
    }
    String memberToRemove = "cn="
        + Rdn.escapeValue(serverId);
    if (!memberList.contains(memberToRemove))
    {
      returnCode = NOT_YET_REGISTERED;
    }

    memberList.remove(memberToRemove);
    HashMap<ServerGroupProperty, Object> serverGroupProperties =
      new HashMap<ServerGroupProperty, Object>();
    serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);

    // Update the server group
    adsCtx.updateServerGroup(groupId, serverGroupProperties);

    // Update the server property "GROUPS"
    Set<Map<ServerProperty,Object>> serverList = adsCtx.readServerRegistry() ;
    boolean found = false;
    Map<ServerProperty,Object> serverProperties = null ;
    for (Map<ServerProperty,Object> elm : serverList)
    {
      if (serverId.equals(elm.get(ServerProperty.ID)))
      {
        found = true ;
        serverProperties = elm ;
        break ;
      }
    }
    if ( ! found )
    {
      return SERVER_NOT_REGISTERED ;
    }

    Set rawGroupList = (Set) serverProperties.get(ServerProperty.GROUPS);
    Set<String> groupList = new HashSet<String>();
    if (rawGroupList != null)
    {
      for (Object elm :rawGroupList.toArray() )
      {
        if (groupId.equals(elm))
        {
          continue ;
        }
        groupList.add(elm.toString());
      }
    }
    serverProperties.put(ServerProperty.GROUPS, groupList);
    adsCtx.updateServer(serverProperties, null);

    return returnCode;
  }

  /**
   * Add a server inside a group.
   *
   * @param adsCtx
   *          The ADS context to use.
   * @param groupId
   *          The group identifier in which a server has to be added.
   * @param map
   *          The properties of the server that have to be added to the
   *          group.
   * @return the return code.
   * @throws ADSContextException
   *           If there is a problem with any of the parameters used
   *           to create this argument.
   */
  static DsFrameworkCliReturnCode addServerTogroup(ADSContext adsCtx,
      String groupId, Map<ServerProperty, Object> map)
      throws ADSContextException
  {
    DsFrameworkCliReturnCode returnCode = SUCCESSFUL;
    String serverId = (String) map.get(ServerProperty.ID);

    // Add the server inside the group
    HashMap<ServerGroupProperty, Object> serverGroupProperties =
      new HashMap<ServerGroupProperty, Object>();
    Set<String> memberList = adsCtx.getServerGroupMemberList(groupId);
    if (memberList == null)
    {
      memberList = new HashSet<String>();
    }
    String newMember = "cn="
        + Rdn.escapeValue(serverId);
    if (memberList.contains(newMember))
    {
      returnCode = ALREADY_REGISTERED;
    }
    memberList.add(newMember);
    serverGroupProperties.put(ServerGroupProperty.MEMBERS, memberList);

    adsCtx.updateServerGroup(groupId, serverGroupProperties);


    // Update the server property "GROUPS"
    Set rawGroupList = (Set) map.get(ServerProperty.GROUPS);
    Set<String> groupList = new HashSet<String>();
    if (rawGroupList != null)
    {
      for (Object elm :rawGroupList.toArray() )
      {
        groupList.add(elm.toString());
      }
    }
    groupList.add(groupId) ;
    map.put(ServerProperty.GROUPS, groupList);
    adsCtx.updateServer(map, null);

    return returnCode;
  }
}
