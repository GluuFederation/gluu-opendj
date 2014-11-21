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
 *      Portions copyright 2012-2013 ForgeRock AS.
 */

package org.opends.quicksetup.util;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.*;


import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Utility class for use by applications containing methods for managing
 * file system files.  This class handles application notifications for
 * interesting events.
 */
public class FileManager {

  /**
   * Describes the approach taken to deleting a file or directory.
   */
  public enum DeletionPolicy {

    /**
     * Delete the file or directory immediately.
     */
    DELETE_IMMEDIATELY,

    /**
     * Mark the file or directory for deletion after the JVM has exited.
     */
    DELETE_ON_EXIT,

    /**
     * First try to delete the file immediately.  If the deletion was
     * unsuccessful mark the file for deleteion when the JVM has
     * existed.
     */
    DELETE_ON_EXIT_IF_UNSUCCESSFUL

  }

  static private final Logger LOG =
          Logger.getLogger(FileManager.class.getName());

  private Application application = null;

  /**
   * Creates a new file manager.
   */
  public FileManager() {
    // do nothing;
  }

  /**
   * Creates a new file manager.
   * @param app Application managing files to which progress notifications
   * will be sent
   */
  public FileManager(Application app) {
    this.application = app;
  }

  /**
   * Recursively copies any files or directories appearing in
   * <code>source</code> or a subdirectory of <code>source</code>
   * to the corresponding directory under <code>target</code>.  Files
   * in under <code>source</code> are not copied to <code>target</code>
   * if a file by the same name already exists in <code>target</code>.
   *
   * @param source source directory
   * @param target target directory
   * @throws ApplicationException if there is a problem copying files
   */
  public void synchronize(File source, File target)
          throws ApplicationException
  {
    if (source != null && target != null) {
      String[] sourceFileNames = source.list();
      if (sourceFileNames != null) {
        for (String sourceFileName : sourceFileNames) {
          File sourceFile = new File(source, sourceFileName);
          copyRecursively(sourceFile, target, null, false);
        }
      }
    }
  }

  /**
   * Renames the source file to the target file.  If the target file exists
   * it is first deleted.  The rename and delete operation return values
   * are checked for success and if unsuccessful, this method throws an
   * exception.
   *
   * @param fileToRename The file to rename.
   * @param target       The file to which <code>fileToRename</code> will be
   *                     moved.
   * @throws ApplicationException If a problem occurs while attempting to rename
   *                     the file.  On the Windows platform, this typically
   *                     indicates that the file is in use by this or another
   *                     application.
   */
  public void rename(File fileToRename, File target)
          throws ApplicationException {
    if (fileToRename != null && target != null) {
      synchronized (target) {
        if (target.exists()) {
          if (!target.delete()) {
            throw new ApplicationException(
                    ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                    INFO_ERROR_DELETING_FILE.get(Utils.getPath(target)), null);
          }
        }
      }
      if (!fileToRename.renameTo(target)) {
        throw new ApplicationException(
                ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                INFO_ERROR_RENAMING_FILE.get(Utils.getPath(fileToRename),
                        Utils.getPath(target)), null);
      }
    }
  }


  /**
   * Move a file.
   * @param object File to move
   * @param newParent File representing new parent directory
   * @throws ApplicationException if something goes wrong
   */
  public void move(File object, File newParent)
          throws ApplicationException
  {
    move(object, newParent, null);
  }

  /**
   * Move a file.
   * @param object File to move
   * @param newParent File representing new parent directory
   * @param filter that will be asked whether or not the operation should be
   *        performed
   * @throws ApplicationException if something goes wrong
   */
  public void move(File object, File newParent, FileFilter filter)
          throws ApplicationException
  {
    // TODO: application notification
    if (filter == null || filter.accept(object)) {
      new MoveOperation(object, newParent).apply();
    }
  }

  /**
   * Deletes a single file or directory.
   * @param object File to delete
   * @throws ApplicationException if something goes wrong
   */
  public void delete(File object)
          throws ApplicationException
  {
    delete(object, null);
  }

  /**
   * Deletes a single file or directory.
   * @param object File to delete
   * @param filter that will be asked whether or not the operation should be
   *        performed
   * @throws ApplicationException if something goes wrong
   */
  public void delete(File object, FileFilter filter)
          throws ApplicationException
  {
    if (filter == null || filter.accept(object)) {
      new DeleteOperation(object, DeletionPolicy.DELETE_IMMEDIATELY).apply();
    }
  }

  /**
   * Deletes the children of a directory.
   *
   * @param parentDir the directory whose children is deleted
   * @throws ApplicationException if there is a problem deleting children
   */
  public void deleteChildren(File parentDir) throws ApplicationException {
    if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
      File[] children = parentDir.listFiles();
      if (children != null) {
        for (File child : children) {
          deleteRecursively(child);
        }
      }
    }
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file the path to be deleted.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  public void deleteRecursively(File file) throws ApplicationException {
    deleteRecursively(file, null,
            FileManager.DeletionPolicy.DELETE_IMMEDIATELY);
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file   the path to be deleted.
   * @param filter the filter of the files to know if the file can be deleted
   *               directly or not.
   * @param deletePolicy describes how deletions are to be made
   *        JVM exits rather than deleting the files immediately.
   * @throws ApplicationException if something goes wrong.
   */
  public void deleteRecursively(File file, FileFilter filter,
                                DeletionPolicy deletePolicy)
          throws ApplicationException {
    operateRecursively(new DeleteOperation(file, deletePolicy), filter);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @return File representing the destination
   * @throws ApplicationException if something goes wrong.
   */
  public File copy(File objectFile, File destDir)
          throws ApplicationException
  {
    CopyOperation co = new CopyOperation(objectFile, destDir, false);
    co.apply();
    return co.getDestination();
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param overwrite    overwrite destination files.
   * @return File representing the destination
   * @throws ApplicationException if something goes wrong.
   */
  public File copy(File objectFile, File destDir, boolean overwrite)
          throws ApplicationException
  {
    CopyOperation co = new CopyOperation(objectFile, destDir, overwrite);
    co.apply();
    return co.getDestination();
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir)
          throws ApplicationException
  {
    copyRecursively(objectFile, destDir, null);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param filter the filter of the files to know if the file can be copied
   *               directly or not.
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir, FileFilter filter)
          throws ApplicationException {
    copyRecursively(objectFile, destDir, filter, false);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param filter the filter of the files to know if the file can be copied
   *               directly or not.
   * @param overwrite    overwrite destination files.
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir,
                              FileFilter filter, boolean overwrite)
          throws ApplicationException {
    operateRecursively(new CopyOperation(objectFile, destDir, overwrite),
            filter);
  }

 /**
  * Determines whether or not two files differ in content.
  *
  * @param f1 file to compare
  * @param f2 file to compare
  * @return boolean where true indicates that two files differ
  * @throws IOException if there is a problem reading the files' conents
  */
 public boolean filesDiffer(File f1, File f2) throws IOException {
   boolean differ = false;
   FileReader fr1 = new FileReader(f1);
   FileReader fr2 = new FileReader(f2);
   try {
     boolean done = false;
     while (!differ && !done) {
       int c1 = fr1.read();
       int c2 = fr2.read();
       differ = c1 != c2;
       done = c1 == -1 || c2 == -1;
     }
   } finally {
     fr1.close();
     fr2.close();
   }
   return differ;
 }

  private void operateRecursively(FileOperation op, FileFilter filter)
          throws ApplicationException {
    File file = op.getObjectFile();
    if (file.exists()) {
      if (file.isFile()) {
        if (filter != null) {
          if (filter.accept(file)) {
            op.apply();
          }
        } else {
          op.apply();
        }
      } else {
        File[] children = file.listFiles();
        if (children != null) {
          for (File aChildren : children) {
            FileOperation newOp = op.copyForChild(aChildren);
            operateRecursively(newOp, filter);
          }
        }
        if (filter != null) {
          if (filter.accept(file)) {
            op.apply();
          }
        } else {
          op.apply();
        }
      }
    } else {
      // Just tell that the file/directory does not exist.
      if (application != null) {
        application.notifyListeners(application.getFormattedWarning(
                INFO_FILE_DOES_NOT_EXIST.get(String.valueOf(file))));
      }
      LOG.log(Level.INFO, "file '" + file.toString() + "' does not exist");
    }
  }

  /**
   * A file operation.
   */
  private abstract class FileOperation {

    private File objectFile = null;

    /**
     * Creates a new file operation.
     * @param objectFile to be operated on
     */
    public FileOperation(File objectFile) {
      this.objectFile = objectFile;
    }

    /**
     * Gets the file to be operated on.
     * @return File to be operated on
     */
    protected File getObjectFile() {
      return objectFile;
    }

    /**
     * Make a copy of this class for the child file.
     * @param child to act as the new file object
     * @return FileOperation as the same type as this class
     */
    abstract public FileOperation copyForChild(File child);

    /**
     * Execute this operation.
     * @throws ApplicationException if there is a problem.
     */
    abstract public void apply() throws ApplicationException;

  }

  /**
   * A copy operation.
   */
  private class CopyOperation extends FileOperation {

    private File destination;

    private boolean overwrite;

    /**
     * Create a new copy operation.
     * @param objectFile to copy
     * @param destDir to copy to
     * @param overwrite if true copy should overwrite any existing file
     */
    public CopyOperation(File objectFile, File destDir, boolean overwrite) {
      super(objectFile);
      this.destination = new File(destDir, objectFile.getName());
      this.overwrite = overwrite;
    }

    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child) {
      return new CopyOperation(child, destination, overwrite);
    }

    /**
     * Returns the destination file that is the result of copying
     * <code>objectFile</code> to <code>destDir</code>.
     * @return The destination file.
     */
    public File getDestination() {
      return this.destination;
    }

    /**
     * {@inheritDoc}
     */
    public void apply() throws ApplicationException {
      File objectFile = getObjectFile();
      if (objectFile.isDirectory()) {
        if (!destination.exists()) {
          destination.mkdirs();
        }
      } else {

        // If overwriting and the destination exists then kill it
        if (destination.exists() && overwrite) {
          deleteRecursively(destination);
        }

        if (!destination.exists()) {
          if (Utils.insureParentsExist(destination)) {
            if ((application != null) && application.isVerbose()) {
              application.notifyListeners(application.getFormattedWithPoints(
                      INFO_PROGRESS_COPYING_FILE.get(
                              objectFile.getAbsolutePath(),
                              destination.getAbsolutePath())));
            }
            LOG.log(Level.INFO, "copying file '" +
                    objectFile.getAbsolutePath() + "' to '" +
                    destination.getAbsolutePath() + "'");
            FileInputStream fis = null;
            FileOutputStream fos = null;
            try {
              fis = new FileInputStream(objectFile);
              fos = new FileOutputStream(destination);
              byte[] buf = new byte[1024];
              int i;
              while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
              }
              if (destination.exists()) {
                // TODO:  set the file's permissions.  This is made easier in
                // Java 1.6 but until then use the TestUtilities methods
                if (Utils.isUnix()) {
                  String permissions =
                          Utils.getFileSystemPermissions(objectFile);
                  Utils.setPermissionsUnix(
                          Utils.getPath(destination),
                          permissions);
                }
              }

              if ((application != null) && application.isVerbose()) {
                application.notifyListeners(
                        application.getFormattedDoneWithLineBreak());
              }

            } catch (Exception e) {
              Message errMsg = INFO_ERROR_COPYING_FILE.get(
                      objectFile.getAbsolutePath(),
                      destination.getAbsolutePath());
              throw new ApplicationException(
                      ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                      errMsg, null);
            } finally {
              if (fis != null) {
                try {
                  fis.close();
                } catch (IOException e) {
                  // ignore;
                }
              }
              if (fos != null) {
                try {
                  fos.close();
                } catch (IOException e) {
                  // ignore;
                }
              }
            }
          } else {
            Message errMsg = INFO_ERROR_COPYING_FILE.get(
                    objectFile.getAbsolutePath(),
                    destination.getAbsolutePath());
            throw new ApplicationException(
                    ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                    errMsg, null);
          }
        } else {
          LOG.log(Level.INFO, "Ignoring file '" +
                  objectFile.getAbsolutePath() + "' since '" +
                  destination.getAbsolutePath() + "' already exists");
          if ((application != null) && application.isVerbose()) {
            application.notifyListeners(
                    INFO_INFO_IGNORING_FILE.get(
                                    objectFile.getAbsolutePath(),
                                    destination.getAbsolutePath()));
            application.notifyListeners(application.getLineBreak());
          }
        }
      }
    }

  }

  /**
   * A delete operation.
   */
  private class DeleteOperation extends FileOperation {

    private DeletionPolicy deletionPolicy;

    /**
     * Creates a delete operation.
     * @param objectFile to delete
     * @param deletionPolicy describing how files will be deleted
     * is to take place after this program exists.  This is useful
     * for cleaning up files that are currently in use.
     */
    public DeleteOperation(File objectFile, DeletionPolicy deletionPolicy) {
      super(objectFile);
      this.deletionPolicy = deletionPolicy;
    }

    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child) {
      return new DeleteOperation(child, deletionPolicy);
    }

    /**
     * {@inheritDoc}
     */
    public void apply() throws ApplicationException {
      File file = getObjectFile();
      boolean isFile = file.isFile();

      if ((application != null) && application.isVerbose()) {
        if (isFile) {
          application.notifyListeners(application.getFormattedWithPoints(
                  INFO_PROGRESS_DELETING_FILE.get(file.getAbsolutePath())));
        } else {
          application.notifyListeners(application.getFormattedWithPoints(
                  INFO_PROGRESS_DELETING_DIRECTORY.get(
                          file.getAbsolutePath())));
        }
      }
      LOG.log(Level.INFO, "deleting " +
              (isFile ? " file " : " directory ") +
              file.getAbsolutePath());

      boolean delete = false;
      /*
       * Sometimes the server keeps some locks on the files.
       * TODO: remove this code once stop-ds returns properly when server
       * is stopped.
       */
      int nTries = 5;
      for (int i = 0; i < nTries && !delete; i++) {
        if (DeletionPolicy.DELETE_ON_EXIT.equals(deletionPolicy)) {
          file.deleteOnExit();
          delete = true;
        } else {
          delete = file.delete();
          if (!delete && DeletionPolicy.DELETE_ON_EXIT_IF_UNSUCCESSFUL.
                  equals(deletionPolicy)) {
            file.deleteOnExit();
            delete = true;
          }
        }
        if (!delete) {
          try {
            Thread.sleep(1000);
          }
          catch (Exception ex) {
            // do nothing;
          }
        }
      }

      if (!delete) {
        Message errMsg;
        if (isFile) {
          errMsg = INFO_ERROR_DELETING_FILE.get(file.getAbsolutePath());
        } else {
          errMsg = INFO_ERROR_DELETING_DIRECTORY.get(file.getAbsolutePath());
        }
        throw new ApplicationException(
                ReturnCode.FILE_SYSTEM_ACCESS_ERROR,
                errMsg, null);
      }

      if ((application != null) && application.isVerbose()) {
        application.notifyListeners(
                application.getFormattedDoneWithLineBreak());
      }
    }
  }

  /**
   * A delete operation.
   */
  private class MoveOperation extends FileOperation {

    File destination = null;

    /**
     * Creates a delete operation.
     * @param objectFile to delete
     * @param newParent Filr where <code>objectFile</code> will be copied.
     */
    public MoveOperation(File objectFile, File newParent) {
      super(objectFile);
      this.destination = new File(newParent, objectFile.getName());
    }

    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child) {
      return new MoveOperation(child, destination);
    }

    /**
     * {@inheritDoc}
     */
    public void apply() throws ApplicationException {
      File objectFile = getObjectFile();
      if (destination.exists()) {
        deleteRecursively(destination);
      }
      if (!objectFile.renameTo(destination)) {
        throw ApplicationException.createFileSystemException(
                INFO_ERROR_FAILED_MOVING_FILE.get(Utils.getPath(objectFile),
                        Utils.getPath(destination)),
                null);
      }
    }
  }

}
