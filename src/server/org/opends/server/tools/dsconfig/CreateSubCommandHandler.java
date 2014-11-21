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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.dsconfig;



import static org.opends.messages.DSConfigMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.dsconfig.ArgumentExceptionFactory.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.messages.Message;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectOption;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyDefinitionUsageBuilder;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SetRelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.condition.Condition;
import org.opends.server.admin.condition.ContainsCondition;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.ClientException;
import org.opends.server.tools.ToolConstants;
import org.opends.server.util.args.Argument;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.StringArgument;
import org.opends.server.util.args.SubCommand;
import org.opends.server.util.args.SubCommandArgumentParser;
import org.opends.server.util.cli.CLIException;
import org.opends.server.util.cli.ConsoleApplication;
import org.opends.server.util.cli.HelpCallback;
import org.opends.server.util.cli.MenuBuilder;
import org.opends.server.util.cli.MenuResult;
import org.opends.server.util.cli.ValidationCallback;
import org.opends.server.util.table.TableBuilder;
import org.opends.server.util.table.TextTablePrinter;



/**
 * A sub-command handler which is used to create new managed objects.
 * <p>
 * This sub-command implements the various create-xxx sub-commands.
 *
 * @param <C>
 *          The type of managed object which can be created.
 * @param <S>
 *          The type of server managed object which can be created.
 */
final class CreateSubCommandHandler<C extends ConfigurationClient,
    S extends Configuration> extends SubCommandHandler {

  /**
   * A property provider which uses the command-line arguments to
   * provide initial property values.
   */
  private static class MyPropertyProvider implements PropertyProvider {

    // Decoded set of properties.
    private final Map<PropertyDefinition<?>, Collection<?>> properties =
      new HashMap<PropertyDefinition<?>, Collection<?>>();



    /**
     * Create a new property provider using the provided set of
     * property value arguments.
     *
     * @param d
     *          The managed object definition.
     * @param namingPropertyDefinition
     *          The naming property definition if there is one.
     * @param args
     *          The property value arguments.
     * @throws ArgumentException
     *           If the property value arguments could not be parsed.
     */
    public MyPropertyProvider(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<?> namingPropertyDefinition, List<String> args)
        throws ArgumentException {
      for (String s : args) {
        // Parse the property "property:value".
        int sep = s.indexOf(':');

        if (sep < 0) {
          throw ArgumentExceptionFactory.missingSeparatorInPropertyArgument(s);
        }

        if (sep == 0) {
          throw ArgumentExceptionFactory.missingNameInPropertyArgument(s);
        }

        String propertyName = s.substring(0, sep);
        String value = s.substring(sep + 1, s.length());
        if (value.length() == 0) {
          throw ArgumentExceptionFactory.missingValueInPropertyArgument(s);
        }

        // Check the property definition.
        PropertyDefinition<?> pd;
        try {
          pd = d.getPropertyDefinition(propertyName);
        } catch (IllegalArgumentException e) {
          throw ArgumentExceptionFactory.unknownProperty(d, propertyName);
        }

        // Make sure that the user is not attempting to set the naming
        // property.
        if (pd.equals(namingPropertyDefinition)) {
          throw ArgumentExceptionFactory.unableToSetNamingProperty(d, pd);
        }

        // Add the value.
        addPropertyValue(d, pd, value);
      }
    }



    /**
     * Get the set of parsed property definitions that have values
     * specified.
     *
     * @return Returns the set of parsed property definitions that
     *         have values specified.
     */
    public Set<PropertyDefinition<?>> getProperties() {
      return properties.keySet();
    }



    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public <T> Collection<T> getPropertyValues(PropertyDefinition<T> d)
        throws IllegalArgumentException {
      Collection<T> values = (Collection<T>) properties.get(d);
      if (values == null) {
        return Collections.emptySet();
      } else {
        return values;
      }
    }



    // Add a single property value.
    @SuppressWarnings("unchecked")
    private <T> void addPropertyValue(ManagedObjectDefinition<?, ?> d,
        PropertyDefinition<T> pd, String s) throws ArgumentException {
      T value;
      try {
        value = pd.decodeValue(s);
      } catch (IllegalPropertyValueStringException e) {
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      Collection<T> values = (Collection<T>) properties.get(pd);
      if (values == null) {
        values = new LinkedList<T>();
      }
      values.add(value);

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        PropertyException e = new PropertyIsSingleValuedException(pd);
        throw ArgumentExceptionFactory.adaptPropertyException(e, d);
      }

      properties.put(pd, values);
    }
  }



  /**
   * A help call-back which displays help about available component
   * types.
   */
  private static final class TypeHelpCallback
      <C extends ConfigurationClient, S extends Configuration>
      implements HelpCallback {

    // The abstract definition for which to provide help on its sub-types.
    private final AbstractManagedObjectDefinition<C, S> d;



    // Create a new type help call-back.
    private TypeHelpCallback(AbstractManagedObjectDefinition<C, S> d) {
      this.d = d;
    }



    /**
     * {@inheritDoc}
     */
    public void display(ConsoleApplication app) {
      app.println(INFO_DSCFG_CREATE_TYPE_HELP_HEADING.get(d
          .getUserFriendlyPluralName()));

      app.println();
      app.println(d.getSynopsis());

      if (d.getDescription() != null) {
        app.println();
        app.println(d.getDescription());
      }

      app.println();
      app.println();

      // Create a table containing a description of each component
      // type.
      TableBuilder builder = new TableBuilder();

      builder.appendHeading(INFO_DSCFG_DESCRIPTION_CREATE_HELP_HEADING_TYPE
          .get());

      builder.appendHeading(INFO_DSCFG_DESCRIPTION_CREATE_HELP_HEADING_DESCR
          .get());

      boolean isFirst = true;
      for (ManagedObjectDefinition<?, ?> mod : getSubTypes(d).values()) {
        // Only display advanced types and custom types in advanced mode.
        if (!app.isAdvancedMode()) {
          if (mod.hasOption(ManagedObjectOption.ADVANCED)) {
            continue;
          }

          if (CLIProfile.getInstance().isForCustomization(mod)) {
            continue;
          }
        }

        Message ufn = mod.getUserFriendlyName();
        Message synopsis = mod.getSynopsis();
        Message description = mod.getDescription();
        if (CLIProfile.getInstance().isForCustomization(mod)) {
          ufn = INFO_DSCFG_CUSTOM_TYPE_OPTION.get(ufn);
          synopsis = INFO_DSCFG_CUSTOM_TYPE_SYNOPSIS.get(ufn);
          description = null;
        } else if (mod == d) {
          ufn = INFO_DSCFG_GENERIC_TYPE_OPTION.get(ufn);
          synopsis = INFO_DSCFG_GENERIC_TYPE_SYNOPSIS.get(ufn);
          description = null;
        }

        if (!isFirst) {
          builder.startRow();
          builder.startRow();
        } else {
          isFirst = false;
        }

        builder.startRow();
        builder.appendCell(ufn);
        builder.appendCell(synopsis);
        if (description != null) {
          builder.startRow();
          builder.startRow();
          builder.appendCell();
          builder.appendCell(description);
        }
      }

      TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
      printer.setColumnWidth(1, 0);
      printer.setColumnSeparator(ToolConstants.LIST_TABLE_SEPARATOR);
      builder.print(printer);
      app.println();
      app.pressReturnToContinue();
    }
  }



  /**
   * The value for the long option set.
   */
  private static final String OPTION_DSCFG_LONG_SET = "set";

  /**
   * The value for the long option type.
   */
  private static final String OPTION_DSCFG_LONG_TYPE = "type";

  /**
   * The value for the short option property.
   */
  private static final Character OPTION_DSCFG_SHORT_SET = null;

  /**
   * The value for the short option type.
   */
  private static final Character OPTION_DSCFG_SHORT_TYPE = 't';


  /**
   * The value for the long option remove (this is used only internally).
   */
  private static final String OPTION_DSCFG_LONG_REMOVE = "remove";

  /**
   * The value for the long option reset (this is used only internally).
   */
  private static final String OPTION_DSCFG_LONG_RESET = "reset";


  /**
   * Creates a new create-xxx sub-command for an instantiable
   * relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The instantiable relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      CreateSubCommandHandler<C, S> create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      InstantiableRelationDefinition<C, S> r) throws ArgumentException {
    return new CreateSubCommandHandler<C, S>(parser, p, r, r
        .getNamingPropertyDefinition(), p.child(r, "DUMMY"));
  }



  /**
   * Creates a new create-xxx sub-command for a sets
   * relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The set relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      CreateSubCommandHandler<C, S> create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      SetRelationDefinition<C, S> r) throws ArgumentException {
    return new CreateSubCommandHandler<C, S>(parser, p, r, null, p.child(r));
  }



  /**
   * Creates a new create-xxx sub-command for an optional relation.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param parser
   *          The sub-command argument parser.
   * @param p
   *          The parent managed object path.
   * @param r
   *          The optional relation.
   * @return Returns the new create-xxx sub-command.
   * @throws ArgumentException
   *           If the sub-command could not be created successfully.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      CreateSubCommandHandler<C, S> create(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      OptionalRelationDefinition<C, S> r) throws ArgumentException {
    return new CreateSubCommandHandler<C, S>(parser, p, r, null, p.child(r));
  }


  /**
   * Interactively lets a user create a new managed object beneath a
   * parent.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param app
   *          The console application.
   * @param context
   *          The management context.
   * @param parent
   *          The parent managed object.
   * @param rd
   *          The relation beneath which the child managed object
   *          should be created.
   * @return Returns a MenuResult.success() containing the name of the
   *         created managed object if it was created successfully, or
   *         MenuResult.quit(), or MenuResult.cancel(), if the managed
   *         object was edited interactively and the user chose to
   *         quit or cancel.
   * @throws ClientException
   *           If an unrecoverable client exception occurred whilst
   *           interacting with the server.
   * @throws CLIException
   *           If an error occurred whilst interacting with the
   *           console.
   */
  public static <C extends ConfigurationClient, S extends Configuration>
      MenuResult<String> createManagedObject(
      ConsoleApplication app, ManagementContext context,
      ManagedObject<?> parent, InstantiableRelationDefinition<C, S> rd)
      throws ClientException, CLIException {
    return createManagedObject(app, context, parent, rd, null);
  }

  /**
   * Interactively lets a user create a new managed object beneath a
   * parent.
   *
   * @param <C>
   *          The type of managed object which can be created.
   * @param <S>
   *          The type of server managed object which can be created.
   * @param app
   *          The console application.
   * @param context
   *          The management context.
   * @param parent
   *          The parent managed object.
   * @param rd
   *          The relation beneath which the child managed object
   *          should be created.
   * @param handler
   *          The subcommand handler whose command builder must be updated.
   * @return Returns a MenuResult.success() containing the name of the
   *         created managed object if it was created successfully, or
   *         MenuResult.quit(), or MenuResult.cancel(), if the managed
   *         object was edited interactively and the user chose to
   *         quit or cancel.
   * @throws ClientException
   *           If an unrecoverable client exception occurred whilst
   *           interacting with the server.
   * @throws CLIException
   *           If an error occurred whilst interacting with the
   *           console.
   */
  private static <C extends ConfigurationClient, S extends Configuration>
      MenuResult<String> createManagedObject(
      ConsoleApplication app, ManagementContext context,
      ManagedObject<?> parent, InstantiableRelationDefinition<C, S> rd,
      SubCommandHandler handler)
      throws ClientException, CLIException {
    AbstractManagedObjectDefinition<C, S> d = rd.getChildDefinition();

    // First determine what type of component the user wants to create.
    MenuResult<ManagedObjectDefinition<? extends C, ? extends S>> result;
    result = getTypeInteractively(app, d, Collections.<String>emptySet());

    ManagedObjectDefinition<? extends C, ? extends S> mod;
    if (result.isSuccess()) {
      mod = result.getValue();
    } else if (result.isCancel()) {
      return MenuResult.cancel();
    } else {
      return MenuResult.quit();
    }

    // Now create the component.
    // FIXME: handle default value exceptions?
    List<DefaultBehaviorException> exceptions =
      new LinkedList<DefaultBehaviorException>();
    app.println();
    app.println();
    ManagedObject<? extends C> mo =
      createChildInteractively(app, parent, rd, mod, exceptions);

    // Let the user interactively configure the managed object and commit it.
    MenuResult<Void> result2 = commitManagedObject(app, context, mo, handler);
    if (result2.isCancel()) {
      return MenuResult.cancel();
    } else if (result2.isQuit()) {
      return MenuResult.quit();
    } else {
      return MenuResult.success(mo.getManagedObjectPath().getName());
    }
  }



  // Check that any referenced components are enabled if
  // required.
  private static MenuResult<Void> checkReferences(ConsoleApplication app,
      ManagementContext context, ManagedObject<?> mo,
      SubCommandHandler handler) throws ClientException,
      CLIException {
    ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
    Message ufn = d.getUserFriendlyName();

    try {
      for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
        if (pd instanceof AggregationPropertyDefinition<?,?>) {
          AggregationPropertyDefinition<?, ?> apd =
            (AggregationPropertyDefinition<?, ?>)pd;

          // Skip this aggregation if the referenced managed objects
          // do not need to be enabled.
          if (!apd.getTargetNeedsEnablingCondition().evaluate(context, mo)) {
            continue;
          }

          // The referenced component(s) must be enabled.
          for (String name : mo.getPropertyValues(apd)) {
            ManagedObjectPath<?, ?> path = apd.getChildPath(name);
            Message rufn = path.getManagedObjectDefinition()
                .getUserFriendlyName();
            ManagedObject<?> ref;
            try {
              ref = context.getManagedObject(path);
            } catch (DefinitionDecodingException e) {
              Message msg = ERR_DSCFG_ERROR_GET_CHILD_DDE.get(rufn, rufn, rufn);
              throw new ClientException(LDAPResultCode.OTHER, msg);
            } catch (ManagedObjectDecodingException e) {
              // FIXME: should not abort here. Instead, display the
              // errors (if verbose) and apply the changes to the
              // partial managed object.
              Message msg = ERR_DSCFG_ERROR_GET_CHILD_MODE.get(rufn);
              throw new ClientException(LDAPResultCode.OTHER, msg, e);
            } catch (ManagedObjectNotFoundException e) {
              Message msg = ERR_DSCFG_ERROR_GET_CHILD_MONFE.get(rufn);
              throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
            }

            Condition condition = apd.getTargetIsEnabledCondition();
            while (!condition.evaluate(context, ref)) {
              boolean isBadReference = true;

              if (condition instanceof ContainsCondition) {
                // Attempt to automatically enable the managed object.
                ContainsCondition cvc = (ContainsCondition) condition;
                app.println();
                if (app.confirmAction(
                    INFO_EDITOR_PROMPT_ENABLED_REFERENCED_COMPONENT.get(rufn,
                        name, ufn), true)) {
                  cvc.setPropertyValue(ref);
                  try {
                    ref.commit();
                    isBadReference = false;
                  } catch (MissingMandatoryPropertiesException e) {
                    // Give the user the chance to fix the problems.
                    app.println();
                    displayMissingMandatoryPropertyException(app, e);
                    app.println();
                    if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn),
                        true)) {
                      MenuResult<Void> result = SetPropSubCommandHandler
                          .modifyManagedObject(app, context, ref, handler);
                      if (result.isQuit()) {
                        return result;
                      } else if (result.isSuccess()) {
                        // The referenced component was modified
                        // successfully, but may still be disabled.
                        isBadReference = false;
                      }
                    }
                  } catch (ConcurrentModificationException e) {
                    Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
                    throw new ClientException(
                        LDAPResultCode.CONSTRAINT_VIOLATION, msg);
                  } catch (OperationRejectedException e) {
                    // Give the user the chance to fix the problems.
                    app.println();
                    displayOperationRejectedException(app, e);
                    app.println();
                    if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT.get(rufn),
                        true)) {
                      MenuResult<Void> result = SetPropSubCommandHandler
                          .modifyManagedObject(app, context, ref, handler);
                      if (result.isQuit()) {
                        return result;
                      } else if (result.isSuccess()) {
                        // The referenced component was modified
                        // successfully, but may still be disabled.
                        isBadReference = false;
                      }
                    }
                  } catch (ManagedObjectAlreadyExistsException e) {
                    // Should never happen.
                    throw new IllegalStateException(e);
                  }
                }
              } else {
                app.println();
                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT_TO_ENABLE.get(
                    rufn, name, ufn), true)) {
                  MenuResult<Void> result = SetPropSubCommandHandler
                      .modifyManagedObject(app, context, ref, handler);
                  if (result.isQuit()) {
                    return result;
                  } else if (result.isSuccess()) {
                    // The referenced component was modified
                    // successfully, but may still be disabled.
                    isBadReference = false;
                  }
                }
              }

              // If the referenced component is still disabled because
              // the user refused to modify it, then give the used the
              // option of editing the referencing component.
              if (isBadReference) {
                app.println();
                app.println(ERR_SET_REFERENCED_COMPONENT_DISABLED
                    .get(ufn, rufn));
                app.println();
                if (app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn),
                    true)) {
                  return MenuResult.again();
                } else {
                  return MenuResult.cancel();
                }
              }
            }
          }
        }
      }
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e.getMessage());
      throw new ClientException(LDAPResultCode.OTHER, msg);
    }

    return MenuResult.success();
  }



  // Commit a new managed object's configuration.
  private static MenuResult<Void> commitManagedObject(ConsoleApplication app,
      ManagementContext context, ManagedObject<?> mo, SubCommandHandler handler)
      throws ClientException,
      CLIException {
    ManagedObjectDefinition<?, ?> d = mo.getManagedObjectDefinition();
    Message ufn = d.getUserFriendlyName();

    PropertyValueEditor editor = new PropertyValueEditor(app, context);
    while (true) {
      // Interactively set properties if applicable.
      if (app.isInteractive()) {
        SortedSet<PropertyDefinition<?>> properties =
          new TreeSet<PropertyDefinition<?>>();
        for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
          if (pd.hasOption(PropertyOption.HIDDEN)) {
            continue;
          }
          if (!app.isAdvancedMode() && pd.hasOption(PropertyOption.ADVANCED)) {
            continue;
          }
          properties.add(pd);
        }

        MenuResult<Void> result = editor.edit(mo, properties, true);

        // Interactively enable/edit referenced components.
        if (result.isSuccess()) {
          result = checkReferences(app, context, mo, handler);
          if (result.isAgain()) {
            // Edit again.
            continue;
          }
        }

        if (result.isQuit()) {
          if (!app.isMenuDrivenMode()) {
            // User chose to cancel any changes.
            Message msg = INFO_DSCFG_CONFIRM_CREATE_FAIL.get(ufn);
            app.printVerboseMessage(msg);
          }
          return MenuResult.quit();
        } else if (result.isCancel()) {
          return MenuResult.cancel();
        }
      }

      try {
        // Create the managed object.
        mo.commit();

        // Output success message.
        if (app.isInteractive() || app.isVerbose())
        {
          app.println();
        }
        Message msg = INFO_DSCFG_CONFIRM_CREATE_SUCCESS.get(ufn);
        app.printVerboseMessage(msg);

        if (handler != null) {
          for (PropertyEditorModification<?> mod : editor.getModifications()) {
            try {
              Argument arg = createArgument(mod);
              handler.getCommandBuilder().addArgument(arg);
            } catch (ArgumentException ae) {
              // This is a bug
              throw new RuntimeException(
                "Unexpected error generating the command builder: " + ae, ae);
            }
          }
          handler.setCommandBuilderUseful(true);
        }
        return MenuResult.success();
      } catch (MissingMandatoryPropertiesException e) {
        if (app.isInteractive()) {
          // If interactive, give the user the chance to fix the
          // problems.
          app.println();
          displayMissingMandatoryPropertyException(app, e);
          app.println();
          if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
            return MenuResult.cancel();
          }
        } else {
          throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, e
              .getMessageObject(), e);
        }
      } catch (AuthorizationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msg);
      } catch (ConcurrentModificationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
      } catch (OperationRejectedException e) {
        if (app.isInteractive()) {
          // If interactive, give the user the chance to fix the
          // problems.
          app.println();
          displayOperationRejectedException(app, e);
          app.println();
          if (!app.confirmAction(INFO_DSCFG_PROMPT_EDIT_AGAIN.get(ufn), true)) {
            return MenuResult.cancel();
          }
        } else {
          throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, e
              .getMessageObject(), e);
        }
      } catch (CommunicationException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e.getMessage());
        if (app.isInteractive()) {
          app.println();
          app.printVerboseMessage(msg);
          return MenuResult.cancel();
        } else {
          throw new ClientException(LDAPResultCode.OTHER, msg);
        }
      } catch (ManagedObjectAlreadyExistsException e) {
        Message msg = ERR_DSCFG_ERROR_CREATE_MOAEE.get(ufn);
        if (app.isInteractive()) {
          app.println();
          app.printVerboseMessage(msg);
          return MenuResult.cancel();
        } else {
          throw new ClientException(LDAPResultCode.ENTRY_ALREADY_EXISTS, msg);
        }
      }
    }
  }



  // Interactively create the child by prompting for the name.
  private static <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> createChildInteractively(
      ConsoleApplication app, final ManagedObject<?> parent,
      final InstantiableRelationDefinition<C, S> irelation,
      final ManagedObjectDefinition<? extends C, ? extends S> d,
      final List<DefaultBehaviorException> exceptions) throws CLIException {
    ValidationCallback<ManagedObject<? extends C>> validator =
      new ValidationCallback<ManagedObject<? extends C>>() {

      public ManagedObject<? extends C> validate(ConsoleApplication app,
          String input) throws CLIException {
        ManagedObject<? extends C> child;

        // First attempt to create the child, this will guarantee that
        // the name is acceptable.
        try {
          child = parent.createChild(irelation, d, input, exceptions);
        } catch (IllegalManagedObjectNameException e) {
          CLIException ae = ArgumentExceptionFactory
              .adaptIllegalManagedObjectNameException(e, d);
          app.println();
          app.println(ae.getMessageObject());
          app.println();
          return null;
        }

        // Make sure that there are not any other children with the
        // same name.
        try {
          // Attempt to retrieve a child using this name.
          parent.getChild(irelation, input);
        } catch (AuthorizationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(irelation
              .getUserFriendlyName());
          throw new CLIException(msg);
        } catch (ConcurrentModificationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(irelation
              .getUserFriendlyName());
          throw new CLIException(msg);
        } catch (CommunicationException e) {
          Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(irelation
              .getUserFriendlyName(), e.getMessage());
          throw new CLIException(msg);
        } catch (DefinitionDecodingException e) {
          // Do nothing.
        } catch (ManagedObjectDecodingException e) {
          // Do nothing.
        } catch (ManagedObjectNotFoundException e) {
          // The child does not already exist so this name is ok.
          return child;
        }

        // A child with the specified name must already exist.
        Message msg = ERR_DSCFG_ERROR_CREATE_NAME_ALREADY_EXISTS.get(irelation
            .getUserFriendlyName(), input);
        app.println();
        app.println(msg);
        app.println();
        return null;
      }

    };

    // Display additional help if the name is a naming property.
    Message ufn = d.getUserFriendlyName();
    PropertyDefinition<?> pd = irelation.getNamingPropertyDefinition();
    if (pd != null) {
      app.println(INFO_DSCFG_CREATE_NAME_PROMPT_NAMING.get(ufn, pd.getName()));

      app.println();
      app.printErrln(pd.getSynopsis(), 4);

      if (pd.getDescription() != null) {
        app.println();
        app.printErrln(pd.getDescription(), 4);
      }

      PropertyDefinitionUsageBuilder b =
        new PropertyDefinitionUsageBuilder(true);
      TableBuilder builder = new TableBuilder();
      builder.startRow();
      builder.appendCell(INFO_EDITOR_HEADING_SYNTAX.get());
      builder.appendCell(b.getUsage(pd));

      TextTablePrinter printer = new TextTablePrinter(app.getErrorStream());
      printer.setDisplayHeadings(false);
      printer.setIndentWidth(4);
      printer.setColumnWidth(1, 0);

      app.println();
      builder.print(printer);
      app.println();

      return app.readValidatedInput(INFO_DSCFG_CREATE_NAME_PROMPT_NAMING_CONT
          .get(ufn), validator);
    } else {
      return app.readValidatedInput(INFO_DSCFG_CREATE_NAME_PROMPT.get(ufn),
          validator);
    }
  }



  // Interactively ask the user which type of component they want to create.
  private static <C extends ConfigurationClient, S extends Configuration>
      MenuResult<ManagedObjectDefinition<? extends C, ? extends S>>
      getTypeInteractively(ConsoleApplication app,
          AbstractManagedObjectDefinition<C, S> d,
          Set<String> prohibitedTypes) throws CLIException {
    // First get the list of available of sub-types.
    List<ManagedObjectDefinition<? extends C, ? extends S>> filteredTypes =
      new LinkedList<ManagedObjectDefinition<? extends C,? extends S>>(
          getSubTypes(d).values());
    boolean isOnlyOneType = filteredTypes.size() == 1;

    Iterator<ManagedObjectDefinition<? extends C,? extends S>> i;
    for (i = filteredTypes.iterator() ; i.hasNext();) {
      ManagedObjectDefinition<? extends C,? extends S> cd = i.next();

      if (prohibitedTypes.contains(cd.getName())) {
        // Remove filtered types.
        i.remove();
      } else if (!app.isAdvancedMode()) {
        // Only display advanced types and custom types in advanced mode.
        if (cd.hasOption(ManagedObjectOption.ADVANCED)) {
          i.remove();
        } else if (CLIProfile.getInstance().isForCustomization(cd)) {
          i.remove();
        }
      }
    }

    // If there is only one choice then return immediately.
    if (filteredTypes.size() == 0) {
      Message msg =
        ERR_DSCFG_ERROR_NO_AVAILABLE_TYPES.get(d.getUserFriendlyName());
      app.println(msg);
      return MenuResult.<ManagedObjectDefinition<? extends C,
          ? extends S>>cancel();
    } else if (filteredTypes.size() == 1) {
      ManagedObjectDefinition<? extends C, ? extends S> type =
        filteredTypes.iterator().next();
      if (!isOnlyOneType) {
        // Only one option available so confirm that the user wishes to
        // use it.
        Message msg = INFO_DSCFG_TYPE_PROMPT_SINGLE.get(
            d.getUserFriendlyName(), type.getUserFriendlyName());
        if (!app.confirmAction(msg, true)) {
          return MenuResult.cancel();
        }
      }
      return MenuResult.<ManagedObjectDefinition<? extends C, ? extends S>>
        success(type);
    } else {
      MenuBuilder<ManagedObjectDefinition<? extends C, ? extends S>> builder =
        new MenuBuilder<ManagedObjectDefinition<? extends C, ? extends S>>(app);
      Message msg = INFO_DSCFG_CREATE_TYPE_PROMPT.get(d.getUserFriendlyName());
      builder.setMultipleColumnThreshold(MULTI_COLUMN_THRESHOLD);
      builder.setPrompt(msg);

      for (ManagedObjectDefinition<? extends C, ? extends S> mod :
        filteredTypes) {

        Message option = mod.getUserFriendlyName();
        if (CLIProfile.getInstance().isForCustomization(mod)) {
          option = INFO_DSCFG_CUSTOM_TYPE_OPTION.get(option);
        } else if (mod == d) {
          option = INFO_DSCFG_GENERIC_TYPE_OPTION.get(option);
        }
        builder.addNumberedOption(option,
            MenuResult.<ManagedObjectDefinition<? extends C,
                ? extends S>>success(mod));
      }
      builder.addHelpOption(new TypeHelpCallback<C,S>(d));
      if (app.isMenuDrivenMode()) {
        builder.addCancelOption(true);
      }
      builder.addQuitOption();
      return builder.toMenu().run();
    }
  }



  // The sub-commands naming arguments.
  private final List<StringArgument> namingArgs;

  // The optional naming property definition.
  private final PropertyDefinition<?> namingPropertyDefinition;

  // The path of the parent managed object.
  private final ManagedObjectPath<?, ?> path;

  // The argument which should be used to specify zero or more
  // property values.
  private final StringArgument propertySetArgument;

  // The relation which should be used for creating children.
  private final RelationDefinition<C, S> relation;

  // The sub-command associated with this handler.
  private final SubCommand subCommand;

  // The argument which should be used to specify the type of managed
  // object to be created.
  private final StringArgument typeArgument;

  // The set of instantiable managed object definitions and their
  // associated type option value.
  private final SortedMap<String,
      ManagedObjectDefinition<? extends C, ? extends S>> types;

  // The syntax of the type argument.
  private final String typeUsage;





  // Common constructor.
  private CreateSubCommandHandler(
      SubCommandArgumentParser parser, ManagedObjectPath<?, ?> p,
      RelationDefinition<C, S> r, PropertyDefinition<?> pd,
      ManagedObjectPath<?, ?> c) throws ArgumentException {
    this.path = p;
    this.relation = r;
    this.namingPropertyDefinition = pd;

    // Create the sub-command.
    String name = "create-" + r.getName();
    Message description = INFO_DSCFG_DESCRIPTION_SUBCMD_CREATE.get(r
        .getChildDefinition().getUserFriendlyPluralName());
    this.subCommand = new SubCommand(parser, name, false, 0, 0, null,
        description);

    // Create the -t argument which is used to specify the type of
    // managed object to be created.
    this.types = getSubTypes(r.getChildDefinition());

    // Create the naming arguments.
    this.namingArgs = createNamingArgs(subCommand, c, true);

    // Build the -t option usage.
    this.typeUsage = getSubTypesUsage(r.getChildDefinition());

    // Create the --property argument which is used to specify
    // property values.
    this.propertySetArgument = new StringArgument(OPTION_DSCFG_LONG_SET,
        OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
        INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
        INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
    this.subCommand.addArgument(this.propertySetArgument);

    if (!types.containsKey(DSConfig.GENERIC_TYPE)) {
      // The option is mandatory when non-interactive.
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, false, false, true,
          INFO_TYPE_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_TYPE.get(r.getChildDefinition()
              .getUserFriendlyName(), typeUsage));
    } else {
      // The option has a sensible default "generic".
      this.typeArgument = new StringArgument("type", OPTION_DSCFG_SHORT_TYPE,
          OPTION_DSCFG_LONG_TYPE, false, false, true,
          INFO_TYPE_PLACEHOLDER.get(),
          DSConfig.GENERIC_TYPE, null, INFO_DSCFG_DESCRIPTION_TYPE_DEFAULT.get(
              r.getChildDefinition().getUserFriendlyName(),
              DSConfig.GENERIC_TYPE, typeUsage));

      // Hide the option if it defaults to generic and generic is the
      // only possible value.
      if (types.size() == 1) {
        this.typeArgument.setHidden(true);
      }
    }
    this.subCommand.addArgument(this.typeArgument);

    // Register the tags associated with the child managed objects.
    addTags(relation.getChildDefinition().getAllTags());
  }



  /**
   * Gets the relation definition associated with the type of
   * component that this sub-command handles.
   *
   * @return Returns the relation definition associated with the type
   *         of component that this sub-command handles.
   */
  public RelationDefinition<?, ?> getRelationDefinition() {
    return relation;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public SubCommand getSubCommand() {
    return subCommand;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public MenuResult<Integer> run(ConsoleApplication app,
      ManagementContextFactory factory) throws ArgumentException,
      ClientException, CLIException {
    Message ufn = relation.getUserFriendlyName();

    // Get the naming argument values.
    List<String> names = getNamingArgValues(app, namingArgs);
    // Reset the command builder
    getCommandBuilder().clearArguments();

    setCommandBuilderUseful(false);

    // Update the command builder.
    updateCommandBuilderWithSubCommand();

    // Add the child managed object.
    ManagementContext context = factory.getManagementContext(app);
    MenuResult<ManagedObject<?>> result;
    try {
      result = getManagedObject(app, context, path, names);
    } catch (AuthorizationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
      throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS, msg);
    } catch (DefinitionDecodingException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_DDE.get(pufn, pufn, pufn);
      throw new ClientException(LDAPResultCode.OTHER, msg);
    } catch (ManagedObjectDecodingException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MODE.get(pufn);
      throw new ClientException(LDAPResultCode.OTHER, msg, e);
    } catch (CommunicationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e
          .getMessage());
      throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
    } catch (ConcurrentModificationException e) {
      Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
      throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
    } catch (ManagedObjectNotFoundException e) {
      Message pufn = path.getManagedObjectDefinition().getUserFriendlyName();
      Message msg = ERR_DSCFG_ERROR_GET_PARENT_MONFE.get(pufn);
      if (app.isInteractive()) {
        app.println();
        app.printVerboseMessage(msg);
        return MenuResult.cancel();
      } else {
        throw new ClientException(LDAPResultCode.NO_SUCH_OBJECT, msg);
      }
    }

    if (result.isQuit()) {
      if (!app.isMenuDrivenMode()) {
        // User chose to cancel creation.
        Message msg = INFO_DSCFG_CONFIRM_CREATE_FAIL.get(ufn);
        app.printVerboseMessage(msg);
      }
      return MenuResult.quit();
    } else if (result.isCancel()) {
      // Must be menu driven, so no need for error message.
      return MenuResult.cancel();
    }

    ManagedObject<?> parent = result.getValue();

    // Determine the type of managed object to be created. If we are creating
    // a managed object beneath a set relation then prevent creation of
    // duplicates.
    Set<String> prohibitedTypes;
    if (relation instanceof SetRelationDefinition) {
      SetRelationDefinition<C, S> sr = (SetRelationDefinition<C, S>) relation;
      prohibitedTypes = new HashSet<String>();
      try
      {
        for (String child : parent.listChildren(sr)) {
          prohibitedTypes.add(child);
        }
      }
      catch (AuthorizationException e)
      {
        Message msg = ERR_DSCFG_ERROR_CREATE_AUTHZ.get(ufn);
        throw new ClientException(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
            msg);
      }
      catch (ConcurrentModificationException e)
      {
        Message msg = ERR_DSCFG_ERROR_CREATE_CME.get(ufn);
        throw new ClientException(LDAPResultCode.CONSTRAINT_VIOLATION, msg);
      }
      catch (CommunicationException e)
      {
        Message msg = ERR_DSCFG_ERROR_CREATE_CE.get(ufn, e
            .getMessage());
        throw new ClientException(LDAPResultCode.CLIENT_SIDE_SERVER_DOWN, msg);
      }
    } else {
      // No prohibited types.
      prohibitedTypes = Collections.emptySet();
    }

    ManagedObjectDefinition<? extends C, ? extends S> d;
    if (!typeArgument.isPresent()) {
      if (app.isInteractive()) {
        // Let the user choose.
        MenuResult<ManagedObjectDefinition<? extends C, ? extends S>> dresult;
        app.println();
        app.println();
        dresult = getTypeInteractively(app, relation.getChildDefinition(),
            prohibitedTypes);

        if (dresult.isSuccess()) {
          d = dresult.getValue();
        } else if (dresult.isCancel()) {
          return MenuResult.cancel();
        } else {
          // Must be quit.
          if (!app.isMenuDrivenMode()) {
            app.printVerboseMessage(INFO_DSCFG_CONFIRM_CREATE_FAIL.get(ufn));
          }
          return MenuResult.quit();
        }
      } else if (typeArgument.getDefaultValue() != null) {
        d = types.get(typeArgument.getDefaultValue());
      } else {
        throw ArgumentExceptionFactory
            .missingMandatoryNonInteractiveArgument(typeArgument);
      }
    } else {
      d = types.get(typeArgument.getValue());
      if (d == null) {
        throw ArgumentExceptionFactory.unknownSubType(relation, typeArgument
            .getValue(), typeUsage);
      }
    }

    // Encode the provided properties.
    List<String> propertyArgs = propertySetArgument.getValues();
    MyPropertyProvider provider = new MyPropertyProvider(d,
        namingPropertyDefinition, propertyArgs);

    ManagedObject<? extends C> child;
    List<DefaultBehaviorException> exceptions =
      new LinkedList<DefaultBehaviorException>();
    boolean isNameProvidedInteractively = false;
    String providedNamingArgName = null;
    if (relation instanceof InstantiableRelationDefinition) {
      InstantiableRelationDefinition<C, S> irelation =
        (InstantiableRelationDefinition<C, S>) relation;
      String name = names.get(names.size() - 1);
      if (name == null) {
        if (app.isInteractive()) {
          app.println();
          app.println();
          child = createChildInteractively(app, parent, irelation,
              d, exceptions);
          isNameProvidedInteractively = true;
          providedNamingArgName =
            CLIProfile.getInstance().getNamingArgument(irelation);
        } else {
          throw ArgumentExceptionFactory
              .missingMandatoryNonInteractiveArgument(namingArgs.get(names
                  .size() - 1));
        }
      } else {
        try {
          child = parent.createChild(irelation, d, name,
              exceptions);
        } catch (IllegalManagedObjectNameException e) {
          throw ArgumentExceptionFactory
              .adaptIllegalManagedObjectNameException(e, d);
        }
      }
    } else if (relation instanceof SetRelationDefinition) {
      SetRelationDefinition<C, S> srelation =
        (SetRelationDefinition<C, S>) relation;
      child = parent.createChild(srelation, d, exceptions);
    } else {
      OptionalRelationDefinition<C, S> orelation =
        (OptionalRelationDefinition<C, S>) relation;
      child = parent.createChild(orelation, d, exceptions);
    }

    // FIXME: display any default behavior exceptions in verbose
    // mode.

    // Set any properties specified on the command line.
    for (PropertyDefinition<?> pd : provider.getProperties()) {
      setProperty(child, provider, pd);
    }

    // Now the command line changes have been made, create the managed
    // object interacting with the user to fix any problems if
    // required.
    MenuResult<Void> result2 = commitManagedObject(app, context, child, this);
    if (result2.isCancel()) {
      return MenuResult.cancel();
    } else if (result2.isQuit()) {
      return MenuResult.quit();
    } else {
      if (typeArgument.hasValue())
      {
        getCommandBuilder().addArgument(typeArgument);
      }
      else
      {
        // Set the type provided by the user
        StringArgument arg = new StringArgument(typeArgument.getName(),
            OPTION_DSCFG_SHORT_TYPE,
            OPTION_DSCFG_LONG_TYPE, false, false, true,
            INFO_TYPE_PLACEHOLDER.get(), typeArgument.getDefaultValue(),
            typeArgument.getPropertyName(),
            typeArgument.getDescription());
        arg.addValue(getTypeName(d));
        getCommandBuilder().addArgument(arg);
      }
      if (propertySetArgument.hasValue())
      {
        /*
          We might have some conflicts in terms of arguments: the user might
           have provided some values that were not good and then these have
           overwritten when asking for them interactively: filter them */
        StringArgument filteredArg = new StringArgument(OPTION_DSCFG_LONG_SET,
            OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
            INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
            INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
        for (String value : propertySetArgument.getValues())
        {
          boolean addValue = true;
          int index = value.indexOf(':');
          if (index != -1)
          {
            String propName = value.substring(0, index);
            for (Argument arg : getCommandBuilder().getArguments())
            {
              for (String value2 : arg.getValues())
              {
                String prop2Name;
                if (arg.getName().equals(OPTION_DSCFG_LONG_SET) ||
                    arg.getName().equals(OPTION_DSCFG_LONG_REMOVE))
                {
                  int index2 = value2.indexOf(':');
                  if (index2 != -1)
                  {
                    prop2Name = value2.substring(0, index2);
                  }
                  else
                  {
                    prop2Name = null;
                  }
                }
                else if (arg.getName().equals(OPTION_DSCFG_LONG_RESET))
                {
                  prop2Name = value2;
                }
                else
                {
                  prop2Name = null;
                }
                if (prop2Name != null)
                {
                  if (prop2Name.equalsIgnoreCase(propName))
                  {
                    addValue = false;
                    break;
                  }
                }
              }
              if (!addValue)
              {
                break;
              }
            }
          }
          else
          {
            addValue = false;
          }
          if (addValue)
          {
            filteredArg.addValue(value);
          }
        }
        if (filteredArg.hasValue())
        {
          getCommandBuilder().addArgument(filteredArg);
        }
      }

      /* Filter the arguments that are used internally */
      List<Argument> argsCopy = new LinkedList<Argument>(
          getCommandBuilder().getArguments());
      for (Argument arg : argsCopy)
      {
        if (arg != null) {
          if (arg.getName().equals(OPTION_DSCFG_LONG_RESET) ||
            arg.getName().equals(OPTION_DSCFG_LONG_REMOVE)) {
            getCommandBuilder().removeArgument(arg);
          }
        }
      }

      if (isNameProvidedInteractively)
      {
        StringArgument arg = new StringArgument(providedNamingArgName, null,
            providedNamingArgName, false, true,
            INFO_NAME_PLACEHOLDER.get(),
            INFO_DSCFG_DESCRIPTION_NAME_CREATE.get(d.getUserFriendlyName()));
        arg.addValue(child.getManagedObjectPath().getName());
        getCommandBuilder().addArgument(arg);
      }
      else
      {
        for (StringArgument arg : namingArgs)
        {
          if (arg.isPresent())
          {
            getCommandBuilder().addArgument(arg);
          }
        }
      }
      return MenuResult.success(0);
    }
  }



  // Set a property's initial values.
  private <T> void setProperty(ManagedObject<?> mo,
      MyPropertyProvider provider, PropertyDefinition<T> pd) {
    Collection<T> values = provider.getPropertyValues(pd);

    // This cannot fail because the property values have already been
    // validated.
    mo.setPropertyValues(pd, values);
  }

  /**
   * Creates an argument (the one that the user should provide in the
   * command-line) that is equivalent to the modification proposed by the user
   * in the provided PropertyEditorModification object.
   * @param mod the object describing the modification made.
   * @return the argument representing the modification.
   * @throws ArgumentException if there is a problem creating the argument.
   */
  private static <T> Argument createArgument(PropertyEditorModification<T> mod)
  throws ArgumentException
  {
    StringArgument arg;

    PropertyDefinition<T> propertyDefinition = mod.getPropertyDefinition();
    String propName = propertyDefinition.getName();

    switch (mod.getType())
    {
    case ADD:
      arg = new StringArgument(OPTION_DSCFG_LONG_SET,
          OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
          INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
      for (T value : mod.getModificationValues())
      {
        arg.addValue(propName+':'+getArgumentValue(propertyDefinition, value));
      }
      break;
    case SET:
      arg = new StringArgument(OPTION_DSCFG_LONG_SET,
          OPTION_DSCFG_SHORT_SET, OPTION_DSCFG_LONG_SET, false, true, true,
          INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_PROP_VAL.get());
      for (T value : mod.getModificationValues())
      {
        arg.addValue(propName+':'+getArgumentValue(propertyDefinition, value));
      }
      break;
    case RESET:
      arg = new StringArgument(OPTION_DSCFG_LONG_RESET,
          null, OPTION_DSCFG_LONG_RESET, false, true, true,
          INFO_PROPERTY_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_RESET_PROP.get());
      arg.addValue(propName);
      break;
    case REMOVE:
      arg = new StringArgument(OPTION_DSCFG_LONG_REMOVE,
          null, OPTION_DSCFG_LONG_REMOVE, false, true,
          true, INFO_VALUE_SET_PLACEHOLDER.get(), null, null,
          INFO_DSCFG_DESCRIPTION_REMOVE_PROP_VAL.get());
      for (T value : mod.getModificationValues())
      {
        arg.addValue(propName+':'+getArgumentValue(propertyDefinition, value));
      }
      arg = null;
      break;
    default:
      // Bug
      throw new IllegalStateException("Unknown modification type: "+
          mod.getType());
    }
    return arg;
  }

  /**
   * Returns the type name for a given ManagedObjectDefinition.
   * @param d the ManagedObjectDefinition.
   * @return the type name for the provided ManagedObjectDefinition.
   */
  private String getTypeName(
      ManagedObjectDefinition<? extends C, ? extends S> d)
  {
    String name = d.getName();
    for (String key : types.keySet())
    {
      ManagedObjectDefinition<? extends C, ? extends S> current =
        types.get(key);
      if (current.equals(d))
      {
        name = key;
        break;
      }
    }
    return name;
  }
}
