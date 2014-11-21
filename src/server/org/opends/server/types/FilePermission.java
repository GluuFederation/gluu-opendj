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
 *      Portions Copyright 2013 ForgeRock, AS.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.opends.server.core.DirectoryServer;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.UtilityMessages.*;



/**
 * This class provides a mechanism for setting file permissions in a
 * more abstract manner than is provided by the underlying operating
 * system and/or filesystem.  It uses a traditional UNIX-style rwx/ugo
 * representation for the permissions and converts them as necessary
 * to the scheme used by the underlying platform.  It does not provide
 * any mechanism for getting file permissions, nor does it provide any
 * way of dealing with file ownership or ACLs.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public class FilePermission
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by its owner.
   */
  public static final int OWNER_READABLE = 0x0100;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by its owner.
   */
  public static final int OWNER_WRITABLE = 0x0080;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by its owner.
   */
  public static final int OWNER_EXECUTABLE = 0x0040;



  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by members of its group.
   */
  public static final int GROUP_READABLE = 0x0020;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by members of its group.
   */
  public static final int GROUP_WRITABLE = 0x0010;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by members of its group.
   */
  public static final int GROUP_EXECUTABLE = 0x0008;



  /**
   * The bitmask that should be used for indicating whether a file is
   * readable by users other than the owner or group members.
   */
  public static final int OTHER_READABLE = 0x0004;



  /**
   * The bitmask that should be used for indicating whether a file is
   * writable by users other than the owner or group members.
   */
  public static final int OTHER_WRITABLE = 0x0002;



  /**
   * The bitmask that should be used for indicating whether a file is
   * executable by users other than the owner or group members.
   */
  public static final int OTHER_EXECUTABLE = 0x0001;



  // Indicates if the Java 7 NIO features can be used, including
  // enhancements to Java 6 {@link java.io.File}.
  private static boolean useNIO;

  // The {@link java.io.File#toPath} method if it is available.
  private static Method toPath;

  // The {@link java.nio.files.Files#setPosixFilePermissions} method if it is
  // available.
  private static Method setPosixFilePermissions;

  // The {@link java.nio.file.Files#getFileAttributeView} method if it is
  // available.
  private static Method getFileAttributeView;

  // The {@link java.nio.file.attribute.PosixFilePermissions#fromString} method
  // if it is available.
  private static Method fromString;

  // The {@link java.nio.file.attribute.PosixFilePermissions#asFileAttribute}
  // method if is available.
  private static Method asFileAttribute;

  // The {@link java.nio.file.attribute.PosixFileAttributeView} class if it is
  // available.
  private static Class<?> posixView;

  // The {@link java.nio.file.attribute.AclFileAttributeView} class if it is
  // available.
  private static Class<?> aclView;

  // The {@link java.nio.file.LinkOption} class if it is available.
  private static Class<?> linkOption;

  // The encoded representation for this file permission.
  private int encodedPermission;



  static
  {
    // Iterate through all the necessary methods and classes in Java 7
    // for dealing with permissions.
    try
    {
      useNIO = false;
      toPath = null;
      setPosixFilePermissions = null;
      getFileAttributeView = null;
      fromString = null;
      asFileAttribute = null;
      posixView = null;
      aclView = null;
      linkOption = null;

      Class<?> c = Class.forName("java.io.File");
      for (Method m : c.getMethods())
      {
        String name = m.getName();
        Class<?>[] argTypes = m.getParameterTypes();
        if (name.equals("toPath") && argTypes.length == 0)
        {
          toPath = m;
        }
      }
      if (toPath == null)
      {
        throw new NoSuchMethodException("java.io.File.toPath");
      }

      c = Class.forName("java.nio.file.attribute.PosixFilePermissions");
      for (Method m : c.getMethods())
      {
        String name = m.getName();
        Class<?>[] argTypes = m.getParameterTypes();
        if (name.equals("fromString") && argTypes.length == 1)
        {
          fromString = m;
        }
        if (name.equals("asFileAttribute") && argTypes.length == 1)
        {
          asFileAttribute = m;
        }
      }
      if (fromString == null)
      {
        throw new NoSuchMethodException(
            "java.nio.file.attribute.PosixFilePermissions.fromString");
      }
      if (asFileAttribute == null) {
        throw new NoSuchMethodException(
            "java.nio.file.attribute.PosixFilePermissions.asFileAttribute");
      }

      c = Class.forName("java.nio.file.Files");
      for (Method m : c.getMethods())
      {
        String name = m.getName();
        Class<?>[] argTypes = m.getParameterTypes();
        if (name.equals("setPosixFilePermissions") && argTypes.length == 2)
        {
          setPosixFilePermissions = m;
        }
        if (name.equals("getFileAttributeView") && argTypes.length == 3)
        {
          getFileAttributeView = m;
        }
      }
      if (setPosixFilePermissions == null)
      {
        throw new NoSuchMethodException(
            "java.nio.file.Files.setPosixFilePermissions");
      }
      if (getFileAttributeView == null)
      {
        throw new NoSuchMethodException(
            "java.nio.file.Files.getFileAttributeView");
      }

      posixView = Class.forName(
          "java.nio.file.attribute.PosixFileAttributeView");
      aclView = Class.forName("java.nio.file.attribute.AclFileAttributeView");
      linkOption = Class.forName("java.nio.file.LinkOption");

      // If we got here, then we have everything we need.
      useNIO = true;
    }
    catch (NoSuchMethodException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.INFO, e);
      }
    }
    catch (ClassNotFoundException e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.INFO, e);
      }
    }
    finally
    {
      // Clean up if we only had partial success.
      if (useNIO == false)
      {
        toPath = null;
        setPosixFilePermissions = null;
        getFileAttributeView = null;
        fromString = null;
        asFileAttribute = null;
        posixView = null;
        aclView = null;
        linkOption = null;
      }
    }

  }



  /**
   * Creates a new file permission object with the provided encoded
   * representation.
   *
   * @param  encodedPermission  The encoded representation for this
   *                            file permission.
   */
  public FilePermission(int encodedPermission)
  {
    this.encodedPermission = encodedPermission;
  }



  /**
   * Creates a new file permission with the specified rights for the
   * file owner.  Users other than the owner will not have any rights.
   *
   * @param  ownerReadable    Indicates whether the owner should have
   *                          the read permission.
   * @param  ownerWritable    Indicates whether the owner should have
   *                          the write permission.
   * @param  ownerExecutable  Indicates whether the owner should have
   *                          the execute permission.
   */
  public FilePermission(boolean ownerReadable, boolean ownerWritable,
                        boolean ownerExecutable)
  {
    encodedPermission = 0x0000;

    if (ownerReadable)
    {
      encodedPermission |= OWNER_READABLE;
    }

    if (ownerWritable)
    {
      encodedPermission |= OWNER_WRITABLE;
    }

    if (ownerExecutable)
    {
      encodedPermission |= OWNER_EXECUTABLE;
    }
  }



  /**
   * Creates a new file permission with the specified rights for the
   * file owner, group members, and other users.
   *
   * @param  ownerReadable    Indicates whether the owner should have
   *                          the read permission.
   * @param  ownerWritable    Indicates whether the owner should have
   *                          the write permission.
   * @param  ownerExecutable  Indicates whether the owner should have
   *                          the execute permission.
   * @param  groupReadable    Indicates whether members of the file's
   *                          group should have the read permission.
   * @param  groupWritable    Indicates whether members of the file's
   *                          group should have the write permission.
   * @param  groupExecutable  Indicates whether members of the file's
   *                          group should have the execute
   *                          permission.
   * @param  otherReadable    Indicates whether other users should
   *                          have the read permission.
   * @param  otherWritable    Indicates whether other users should
   *                          have the write permission.
   * @param  otherExecutable  Indicates whether other users should
   *                          have the execute permission.
   */
  public FilePermission(boolean ownerReadable, boolean ownerWritable,
                        boolean ownerExecutable,
                        boolean groupReadable, boolean groupWritable,
                        boolean groupExecutable,
                        boolean otherReadable, boolean otherWritable,
                        boolean otherExecutable)
  {
    encodedPermission = 0x0000;

    if (ownerReadable)
    {
      encodedPermission |= OWNER_READABLE;
    }

    if (ownerWritable)
    {
      encodedPermission |= OWNER_WRITABLE;
    }

    if (ownerExecutable)
    {
      encodedPermission |= OWNER_EXECUTABLE;
    }

    if (groupReadable)
    {
      encodedPermission |= GROUP_READABLE;
    }

    if (groupWritable)
    {
      encodedPermission |= GROUP_WRITABLE;
    }

    if (groupExecutable)
    {
      encodedPermission |= GROUP_EXECUTABLE;
    }

    if (otherReadable)
    {
      encodedPermission |= OTHER_READABLE;
    }

    if (otherWritable)
    {
      encodedPermission |= OTHER_WRITABLE;
    }

    if (otherExecutable)
    {
      encodedPermission |= OTHER_EXECUTABLE;
    }
  }



  /**
   * Indicates whether this file permission includes the owner read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner read permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerReadable()
  {
    return ((encodedPermission & OWNER_READABLE) == OWNER_READABLE);
  }



  /**
   * Indicates whether this file permission includes the owner write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner write permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerWritable()
  {
    return ((encodedPermission & OWNER_WRITABLE) == OWNER_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the owner execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          owner execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isOwnerExecutable()
  {
    return ((encodedPermission & OWNER_EXECUTABLE) ==
            OWNER_EXECUTABLE);
  }



  /**
   * Indicates whether this file permission includes the group read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group read permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupReadable()
  {
    return ((encodedPermission & GROUP_READABLE) == GROUP_READABLE);
  }



  /**
   * Indicates whether this file permission includes the group write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group write permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupWritable()
  {
    return ((encodedPermission & GROUP_WRITABLE) == GROUP_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the group execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          group execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isGroupExecutable()
  {
    return ((encodedPermission & GROUP_EXECUTABLE) ==
            GROUP_EXECUTABLE);
  }



  /**
   * Indicates whether this file permission includes the other read
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other read permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherReadable()
  {
    return ((encodedPermission & OTHER_READABLE) == OTHER_READABLE);
  }



  /**
   * Indicates whether this file permission includes the other write
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other write permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherWritable()
  {
    return ((encodedPermission & OTHER_WRITABLE) == OTHER_WRITABLE);
  }



  /**
   * Indicates whether this file permission includes the other execute
   * permission.
   *
   * @return  <CODE>true</CODE> if this file permission includes the
   *          other execute permission, or <CODE>false</CODE> if not.
   */
  public boolean isOtherExecutable()
  {
    return ((encodedPermission & OTHER_EXECUTABLE) ==
            OTHER_EXECUTABLE);
  }



  /**
   * Indicates whether the there is a mechanism available for setting
   * permissions in the underlying filesystem on the current platform.
   *
   * @return  <CODE>true</CODE> if there is a mechanism available for
   *          setting file permissions on the underlying system (e.g.,
   *          if the server is running in a Java 6 environment, or if
   *          this is a UNIX-based system and the use of exec is
   *          allowed), or <CODE>false</CODE> if no such mechanism is
   *          available.
   */
  public static boolean canSetPermissions()
  {
    if (useNIO)
    {
      // It's a Java 7 environment.
      return true;
    }

    // It's a Java 6 environment, so we can always use that
    // mechanism.
    return true;
  }



  /**
   * Attempts to set the given permissions on the specified file.  If
   * the underlying platform does not allow the full level of
   * granularity specified in the permissions, then an attempt will be
   * made to set them as closely as possible to the provided
   * permissions, erring on the side of security.
   *
   * @param  f  The file to which the permissions should be applied.
   * @param  p  The permissions to apply to the file.
   *
   * @return  <CODE>true</CODE> if the permissions (or the nearest
   *          equivalent) were successfully applied to the specified
   *          file, or <CODE>false</CODE> if was not possible to set
   *          the permissions on the current platform.
   *
   * @throws  FileNotFoundException  If the specified file does not
   *                                 exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              set the file permissions.
   */
  public static boolean setPermissions(File f, FilePermission p)
         throws FileNotFoundException, DirectoryException
  {
    if (! f.exists())
    {
      Message message =
          ERR_FILEPERM_SET_NO_SUCH_FILE.get(f.getAbsolutePath());
      throw new FileNotFoundException(message.toString());
    }

    // If we're running Java 7 and have NIO available, use that.
    if (useNIO)
    {
      return setUsingJava7(f, p);
    }

    // If we're running Java 6, then we'll use the methods that Java
    // provides.
    return setUsingJava6(f, p);
  }



  /**
   * Attempts to set the specified permissions for the given file or
   * directory using the Java 7 NIO API. This will set the full POSIX
   * permissions on systems supporting POSIX filesystem semantics.
   *
   * @param f The file or directory to which the permissions should be applied.
   * @param p The permissions to apply to the file or directory.
   *
   * @return <code>true</code> if the permissions were successfully updated, or
   *         <code>false</code> if not.
   *
   */
 private static boolean setUsingJava7(File f, FilePermission p)
 {
   try
   {
     // path = f.toPath();
     Object path = toPath.invoke(f);

     // posix = Files.getFileAttributeView(path, posixFileAttributeView.class);
     Object posix = getFileAttributeView.invoke(null, path, posixView,
         Array.newInstance(linkOption, 0));

     // If a POSIX view is available, then set the permissions.
     // NOTE:  Windows 2003, 2008 and 7 (and probably others) don't have POSIX
     //        views.
     if (posix != null)
     {
       // Build a string like "rwxr-x-w-" from p.
       StringBuilder posixMode = new StringBuilder();
       toPOSIXString(p, posixMode, "", "", "");
       // perms = PosixFilePermissions.fromString(posixMode.toString());
       Object perms = fromString.invoke(null, posixMode.toString());

       // Files.setPosixFilePermissions(path, perms);
       setPosixFilePermissions.invoke(null, path, perms);
       return true;
     }

     // acl = Files.getFileAttributeView(path, aclFileAttributeView.class);
     Object acl = getFileAttributeView.invoke(null, path, aclView,
         Array.newInstance(linkOption, 0));

     // If an ACL view is available, then return successfully.
     // This is not ideal, but the intention is the administrator has set up
     // the inherited ACLs "appropriately" so we don't need to do anything.
     //
     // Also ideally we would check ACLs before checking POSIX, in case we have
     // a filesystem like ZFS that can support both.
     if (acl != null)
     {
       return true;
     }
   }
   catch (Exception e)
   {
     if (debugEnabled())
     {
       TRACER.debugCaught(DebugLogLevel.ERROR, e);
     }
   }
   return false;
 }

  /**
   * Attempts to set the specified permissions for the given file
   * using the Java 6 <CODE>FILE</CODE> API.  Only the "owner" and
   * "other" permissions will be preserved, since Java 6 doesn't provide
   * a way to set the group permissions directly.
   *
   * @param  f  The file to which the permissions should be applied.
   * @param  p  The permissions to apply to the file.
   *
   * @return  <CODE>true</CODE> if the permissions were successfully
   *          updated, or <CODE>false</CODE> if not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to update permissions.
   */
  private static boolean setUsingJava6(File f, FilePermission p)
          throws DirectoryException
  {
    // NOTE:  Due to a very nasty behavior of the Java 6 API, if you
    //        want to want to grant a permission for the owner but not
    //        for anyone else, then you *must* remove it for everyone
    //        first, and then add it only for the owner.  Otherwise,
    //        the other permissions will be left unchanged and if they
    //        had it before then they will still have it.

    boolean anySuccessful   = false;
    boolean anyFailed       = false;
    boolean exceptionThrown = false;

    // Take away read permission from everyone if necessary.
    if (p.isOwnerReadable() && (! p.isOtherReadable()))
    {
      try
      {
        if (f.setReadable(false, false))
        {
          anySuccessful = true;
        }
        else
        {
          if(!DirectoryServer.getOperatingSystem().equals(
              OperatingSystem.WINDOWS))
          {
            // On Windows platforms, file readability permissions
            // cannot be set to false. Do not consider this case
            // a failure. http://java.sun.com/developer/
            // technicalArticles/J2SE/Desktop/javase6/enhancements/
            anyFailed = true;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        exceptionThrown = true;
      }
    }

    // Grant the appropriate read permission.
    try
    {
      boolean ownerOnly =
           (p.isOwnerReadable() != p.isOtherReadable());

      if (f.setReadable(p.isOwnerReadable(), ownerOnly))
      {
        anySuccessful = true;
      }
      else
      {
        if(!DirectoryServer.getOperatingSystem().equals(
            OperatingSystem.WINDOWS) || p.isOwnerReadable())
        {
          // On Windows platforms, file readability permissions
          // cannot be set to false. Do not consider this case
          // a failure. http://java.sun.com/developer/
          // technicalArticles/J2SE/Desktop/javase6/enhancements/
          anyFailed = true;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      exceptionThrown = true;
    }


    // NOTE:  On Windows platforms attempting to call setWritable on a
    //        directory always fails, regardless of parameters. Ignore
    //        these failures.
    boolean ignoreSetWritableFailures =
        (DirectoryServer.getOperatingSystem().equals(
            OperatingSystem.WINDOWS) && f.isDirectory());

    // Take away write permission from everyone if necessary.
    if (p.isOwnerWritable() && (! p.isOtherWritable()))
    {
      try
      {
        if (f.setWritable(false, false))
        {
          anySuccessful = true;
        }
        else if (!ignoreSetWritableFailures)
        {
          anyFailed = true;
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        exceptionThrown = true;
      }
    }

    // Grant the appropriate write permission.
    try
    {
      boolean ownerOnly =
           (p.isOwnerWritable() != p.isOtherWritable());

      if (f.setWritable(p.isOwnerWritable(), ownerOnly))
      {
        anySuccessful = true;
      }
      else if (!ignoreSetWritableFailures)
      {
        anyFailed = true;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      exceptionThrown = true;
    }


    // Take away execute permission from everyone if necessary.
    if (p.isOwnerExecutable() && (! p.isOtherExecutable()))
    {
      try
      {
        if (f.setExecutable(false, false))
        {
          anySuccessful = true;
        }
        else
        {
          if(!DirectoryServer.getOperatingSystem().equals(
              OperatingSystem.WINDOWS))
          {
            // On Windows platforms, file execute permissions
            // cannot be set to false. Do not consider this case
            // a failure. http://java.sun.com/developer/
            // technicalArticles/J2SE/Desktop/javase6/enhancements/
            anyFailed = true;
          }
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        exceptionThrown = true;
      }
    }

    // Grant the appropriate execute permission.
    try
    {
      boolean ownerOnly =
           (p.isOwnerExecutable() != p.isOtherExecutable());

      if (f.setExecutable(p.isOwnerExecutable(), ownerOnly))
      {
        anySuccessful = true;
      }
      else
      {
        if(!DirectoryServer.getOperatingSystem().equals(
            OperatingSystem.WINDOWS) || p.isOwnerExecutable())
        {
          // On Windows platforms, file execute permissions
          // cannot be set to false. Do not consider this case
          // a failure. http://java.sun.com/developer/
          // technicalArticles/J2SE/Desktop/javase6/enhancements/
          anyFailed = true;
        }
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      exceptionThrown = true;
    }


    if (exceptionThrown)
    {
      // If an exception was thrown, we can't be sure whether or not
      // any permissions were updated.
      Message message =
          ERR_FILEPERM_SET_JAVA_EXCEPTION.get(f.getAbsolutePath());
      throw new DirectoryException(ResultCode.OTHER, message);
    }
    else if (anyFailed)
    {
      if (anySuccessful)
      {
        // Some of the file permissions may have been altered.
        Message message = ERR_FILEPERM_SET_JAVA_FAILED_ALTERED.get(
            f.getAbsolutePath());
        throw new DirectoryException(ResultCode.OTHER, message);
      }
      else
      {
        // The file permissions should have been left intact.
        Message message = ERR_FILEPERM_SET_JAVA_FAILED_UNALTERED.get(
            f.getAbsolutePath());
        throw new DirectoryException(ResultCode.OTHER, message);
      }
    }
    else
    {
      return anySuccessful;
    }
  }



  /**
   * Retrieves a three-character string that is the UNIX mode for the
   * provided file permission.  Each character of the string will be a
   * numeric digit from zero through seven.
   *
   * @param  p  The permission to retrieve as a UNIX mode string.
   *
   * @return  The UNIX mode string for the provided permission.
   */
  public static String toUNIXMode(FilePermission p)
  {
    StringBuilder buffer = new StringBuilder(3);
    toUNIXMode(buffer, p);
    return buffer.toString();
  }



  /**
   * Appends a three-character string that is the UNIX mode for the
   * provided file permission to the given buffer.  Each character of
   * the string will be a numeric digit from zero through seven.
   *
   * @param  buffer  The buffer to which the mode string should be
   *                 appended.
   * @param  p       The permission to retrieve as a UNIX mode string.
   */
  public static void toUNIXMode(StringBuilder buffer,
                                FilePermission p)
  {
    byte modeByte = 0x00;
    if (p.isOwnerReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isOwnerWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isOwnerExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(String.valueOf(modeByte));

    modeByte = 0x00;
    if (p.isGroupReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isGroupWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isGroupExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(String.valueOf(modeByte));

    modeByte = 0x00;
    if (p.isOtherReadable())
    {
      modeByte |= 0x04;
    }
    if (p.isOtherWritable())
    {
      modeByte |= 0x02;
    }
    if (p.isOtherExecutable())
    {
      modeByte |= 0x01;
    }
    buffer.append(String.valueOf(modeByte));
  }



  /**
   * Decodes the provided string as a UNIX mode and retrieves the
   * corresponding file permission.  The mode string must contain
   * three digits between zero and seven.
   *
   * @param  modeString  The string representation of the UNIX mode to
   *                     decode.
   *
   * @return  The file permission that is equivalent to the given UNIX
   *          mode.
   *
   * @throws  DirectoryException  If the provided string is not a
   *                              valid three-digit UNIX mode.
   */
  public static FilePermission decodeUNIXMode(String modeString)
         throws DirectoryException
  {
    if ((modeString == null) || (modeString.length() != 3))
    {
      Message message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(
          String.valueOf(modeString));
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    int encodedPermission = 0x0000;
    switch (modeString.charAt(0))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= OWNER_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= OWNER_WRITABLE;
        break;
      case '3':
        encodedPermission |= OWNER_WRITABLE | OWNER_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= OWNER_READABLE;
        break;
      case '5':
         encodedPermission |= OWNER_READABLE | OWNER_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= OWNER_READABLE | OWNER_WRITABLE;
        break;
      case '7':
        encodedPermission |= OWNER_READABLE | OWNER_WRITABLE |
                             OWNER_EXECUTABLE;
        break;
      default:
      Message message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(
          String.valueOf(modeString));
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    switch (modeString.charAt(1))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= GROUP_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= GROUP_WRITABLE;
        break;
      case '3':
        encodedPermission |= GROUP_WRITABLE | GROUP_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= GROUP_READABLE;
        break;
      case '5':
         encodedPermission |= GROUP_READABLE | GROUP_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= GROUP_READABLE | GROUP_WRITABLE;
        break;
      case '7':
        encodedPermission |= GROUP_READABLE | GROUP_WRITABLE |
                             GROUP_EXECUTABLE;
        break;
      default:
      Message message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(
          String.valueOf(modeString));
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    switch (modeString.charAt(2))
    {
      case '0':
        break;
      case '1':
        encodedPermission |= OTHER_EXECUTABLE;
        break;
      case '2':
        encodedPermission |= OTHER_WRITABLE;
        break;
      case '3':
        encodedPermission |= OTHER_WRITABLE | OTHER_EXECUTABLE;
        break;
      case '4':
        encodedPermission |= OTHER_READABLE;
        break;
      case '5':
         encodedPermission |= OTHER_READABLE | OTHER_EXECUTABLE;
        break;
      case '6':
        encodedPermission |= OTHER_READABLE | OTHER_WRITABLE;
        break;
      case '7':
        encodedPermission |= OTHER_READABLE | OTHER_WRITABLE |
                             OTHER_EXECUTABLE;
        break;
      default:
      Message message = ERR_FILEPERM_INVALID_UNIX_MODE_STRING.get(
          String.valueOf(modeString));
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    return new FilePermission(encodedPermission);
  }



  /**
   * Build a file permissions string in the "rwx" form expected by NIO,
   * but with optional prefix strings before each three character block.
   * <p>
   * For example: "rwxr-xrw-" and "Owner=rwx, Group=r-x", Other=rw-".
   *
   * @param p      The file permissions to use.
   * @param buffer The buffer being appended to.
   * @param owner  The owner prefix, must not be null.
   * @param group  The group prefix, must not be null.
   * @param other  The other prefix, must not be null.
   */
  private static void toPOSIXString(FilePermission p, StringBuilder buffer,
      String owner, String group, String other)
  {
    buffer.append(owner);
    buffer.append(p.isOwnerReadable() ? "r" : "-");
    buffer.append(p.isOwnerWritable() ? "w" : "-");
    buffer.append(p.isOwnerExecutable() ? "x" : "-");

    buffer.append(group);
    buffer.append(p.isGroupReadable() ? "r" : "-");
    buffer.append(p.isGroupWritable() ? "w" : "-");
    buffer.append(p.isGroupExecutable() ? "x" : "-");

    buffer.append(other);
    buffer.append(p.isOtherReadable() ? "r" : "-");
    buffer.append(p.isOtherWritable() ? "w" : "-");
    buffer.append(p.isOtherExecutable() ? "x" : "-");
  }



  /**
   * Retrieves a string representation of this file permission.
   *
   * @return  A string representation of this file permission.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this file permission to the
   * given buffer.
   *
   * @param  buffer  The buffer to which the data should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    toPOSIXString(this, buffer, "Owner=", ", Group=", ", Other=");
  }
}

