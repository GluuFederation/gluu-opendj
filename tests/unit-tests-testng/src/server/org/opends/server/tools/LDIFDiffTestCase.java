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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.tools;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.testng.Assert.*;



/**
 * A set of test cases for the LDIFDiff tool.
 */
public class LDIFDiffTestCase
       extends ToolsTestCase
{
  // The path to the file that will be used if there are no differences between
  // the source and target LDIF data sets.
  private String noDiffsFile =
       System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT) + File.separator +
       "tests" + File.separator + "unit-tests-testng" + File.separator +
       "resource" + File.separator + "ldif-diff" + File.separator +
       "diff-nochanges.ldif";



  /**
   * Make sure that the server is running, since we need it for schema
   * handling.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }


  /**
   * Calculates the checksum of a file
   */
  private long calcChecksum(String filename) throws Exception
  {
    return calcChecksum(new File(filename));
  }

  private long calcChecksum(File file) throws Exception
  {
    long checksum = 0L;
    BufferedReader reader = new BufferedReader(new FileReader(file));
    String line = null;
    while ((line =reader.readLine()) != null)
    {
      checksum += line.hashCode();
    }
    reader.close();
    return checksum;
  }


  /**
   * Tests the LDIFDiff tool with an argument that will simply cause it to
   * display usage information.
   */
  @Test()
  public void testUsage()
  {
    String[] args = { "--help" };
    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);

    args = new String[] { "-H" };
    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);

    args = new String[] { "-?" };
    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
  }



  /**
   * Tests the LDIFDiff tool with an invalid set of arguments.
   */
  @Test()
  public void testInvalidArguments()
  {
    String[] args =
    {
      "--invalid"
    };

    assertFalse(LDIFDiff.mainDiff(args, true, System.out, System.err) == 0);
  }



  /**
   * Retrieves the names of the files that should be used when testing the
   * ldif-diff tool.  Each element of the outer array should be an array
   * containing the following elements:
   * <OL>
   *   <LI>The path to the source LDIF file</LI>
   *   <LI>The path to the target LDIF file</LI>
   *   <LI>The path to the diff file, or {@code null} if the diff is supposed
   *       to fail</LI>
   * </OL>
   */
  @DataProvider(name = "testdata")
  public Object[][] getTestData()
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String ldifRoot  = buildRoot + File.separator + "tests" + File.separator +
                       "unit-tests-testng" + File.separator + "resource" +
                       File.separator + "ldif-diff" + File.separator;

    return new Object[][]
    {
      // Both files are empty.
      new Object[] { ldifRoot + "source-empty.ldif",
                     ldifRoot + "target-empty.ldif",
                     noDiffsFile, noDiffsFile,
                     COMPARE_TRUE },

      // Both files are the single-entry source.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     noDiffsFile, noDiffsFile,
                     COMPARE_TRUE },

      // Both files are the single-entry target.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     noDiffsFile, noDiffsFile,
                     COMPARE_TRUE },

      // Both files are the multiple-entry source.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     noDiffsFile, noDiffsFile,
                     COMPARE_TRUE },

      // Both files are the multiple-entry target.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     noDiffsFile, noDiffsFile,
                     COMPARE_TRUE },

      // The source is empty but the target has a single entry.
      new Object[] { ldifRoot + "source-empty.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-emptytosingle.ldif",
                     ldifRoot + "diff-emptytosingle.ldif",
                     COMPARE_FALSE },

      // The source has a single entry but the target is empty.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-empty.ldif",
                     ldifRoot + "diff-singletoempty.ldif",
                     ldifRoot + "diff-singletoempty.ldif",
                     COMPARE_FALSE },

      // Make a change to only a single entry in the source->target direction.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-singleentry.ldif",
                     ldifRoot + "diff-singleentry.ldif",
                     COMPARE_FALSE },

      // Make a change to only a single entry in the target->source direction.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "diff-singleentry-reverse.ldif",
                     ldifRoot + "diff-singleentry-reverse.ldif",
                     COMPARE_FALSE },

      // Make changes to multiple entries in the source->target direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries-singlevalue.ldif",
                     COMPARE_FALSE },

      // Make changes to multiple entries in the target->source direction.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "diff-multipleentries-reverse.ldif",
                     ldifRoot +
                          "diff-multipleentries-reverse-singlevalue.ldif",
                     COMPARE_FALSE },

      // Go from one entry to multiple in the source->target direction.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "diff-singletomultiple.ldif",
                     ldifRoot + "diff-singletomultiple-singlevalue.ldif",
                     COMPARE_FALSE },

      // Go from one entry to multiple in the target->source direction.
      new Object[] { ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "diff-singletomultiple-reverse.ldif",
                     ldifRoot + "diff-singletomultiple-reverse.ldif",
                     COMPARE_FALSE },

      // Go from multiple entries to one in the source->target direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     ldifRoot + "diff-multipletosingle.ldif",
                     ldifRoot + "diff-multipletosingle.ldif",
                     COMPARE_FALSE },

      // Go from multiple entries to one in the target->source direction.
      new Object[] { ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "diff-multipletosingle-reverse.ldif",
                     ldifRoot +
                          "diff-multipletosingle-reverse-singlevalue.ldif",
                     COMPARE_FALSE },

      // The source file doesn't exist.
      new Object[] { ldifRoot + "source-notfound.ldif",
                     ldifRoot + "target-singleentry.ldif",
                     null, null,
                     COMPARE_FALSE },

      // The target file doesn't exist.
      new Object[] { ldifRoot + "source-singleentry.ldif",
                     ldifRoot + "target-notfound.ldif",
                     null, null,
                     COMPARE_FALSE }
    };
  }


  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * normal mode of operation works as expected.  This is a bit tricky because
   * the attributes and values will be written in an indeterminate order, so we
   * can't just use string equality.  We'll have to use a crude checksum
   * mechanism to test whether they are equal.  Combined with other methods in
   * this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   * @param resultCode            The result code that should be returned with
   *                              --useCompareResultCode flag
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testVerifyNormal(String sourceFile, String targetFile,
      String normalDiffFile, String singleValueDiffFile, int resultCode)
      throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", outputFile.getAbsolutePath(),
      "-O"
    };
    String[] argsUseCompare =
    {
      "-s", sourceFile,
      "-t", targetFile,
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    if (normalDiffFile == null)
    {
      // We expect this to fail, so just make sure that it does.
      assertFalse(LDIFDiff.mainDiff(args, true, System.out, System.err) == 0);
      outputFile.delete();
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(outputFile), calcChecksum(normalDiffFile));

    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), resultCode);
    outputFile.delete();
  }




  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * single value changes mode of operation works as expected.  This is a bit
   * tricky because the attributes and values will be written in an
   * indeterminate order, so we can't just use string equality.  We'll have to
   * use a crude checksum mechanism to test whether they are equal.  Combined
   * with other methods in this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   * @param resultCode            The result code that should be returned with
   *                              --useCompareResultCode flag
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testVerifySingleValue(String sourceFile, String targetFile,
      String normalDiffFile, String singleValueDiffFile, int resultCode)
      throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", outputFile.getAbsolutePath(),
      "-O",
      "-S"
    };
    String[] argsUseCompare =
    {
      "-s", sourceFile,
      "-t", targetFile,
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    if (singleValueDiffFile == null)
    {
      // We expect this to fail, so just make sure that it does.
      assertFalse(LDIFDiff.mainDiff(args, true, System.out, System.err) == 0);
      outputFile.delete();
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(outputFile), calcChecksum(singleValueDiffFile));

    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), resultCode);
    outputFile.delete();
  }



  /**
   * Tests the LDIFDiff tool by first identifying the differences between the
   * source and the target and then using the LDIFModify tool to apply the
   * identified changes to the source LDIF and verify that it matches the
   * target.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   * @param resultCode            The result code that should be returned with
   *                              --useCompareResultCode flag
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testReconstructNormal(String sourceFile, String targetFile,
      String normalDiffFile, String singleValueDiffFile, int resultCode)
      throws Exception
  {
    // If the command is expected to fail, or if there aren't any differences,
    // then bail out now.
    if ((normalDiffFile == null) || normalDiffFile.equals(noDiffsFile))
    {
      return;
    }


    // Generate the diff file.
    File diffOutputFile = File.createTempFile("difftest", "ldif");
    diffOutputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", diffOutputFile.getAbsolutePath()
    };
    String[] argsUseCompare =
    {
      "-s", sourceFile,
      "-t", targetFile,
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), resultCode);


    // Use LDIFModify to generate a new target file.
    File newTargetFile = File.createTempFile("difftest", "newtarget.ldif");
    newTargetFile.deleteOnExit();

    DirectoryServer.getInstance();
    args = new String[]
    {
      "-c", DirectoryServer.getConfigFile(),
      "-s", sourceFile,
      "-m", diffOutputFile.getAbsolutePath(),
      "-t", newTargetFile.getAbsolutePath()
    };

    assertEquals(LDIFModify.ldifModifyMain(args, true, System.out, System.err),
                 0);


    // Use LDIFDiff again to verify that there are effectively no differences
    // between the original target and the new target.
    File newDiffFile = File.createTempFile("difftest", "newdiff.ldif");
    newDiffFile.deleteOnExit();

    args = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      "-o", newDiffFile.getAbsolutePath()
    };
    argsUseCompare = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(newDiffFile), calcChecksum(noDiffsFile));
    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), COMPARE_TRUE);

    diffOutputFile.delete();
    newTargetFile.delete();
    newDiffFile.delete();
  }



  /**
   * Tests the LDIFDiff tool by first identifying the differences between the
   * source and the target (using the single-value format) and then using the
   * LDIFModify tool to apply the identified changes to the source LDIF and
   * verify that it matches the target.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @param  singleValueDiffFile  The path to the file containing the expected
   *                              diff in "single-value" form, where each
   *                              attribute-level change results in a separate
   *                              entry per attribute value.
   * @param resultCode            The result code that should be returned with
   *                              --useCompareResultCode flag
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testReconstructSingleValue(String sourceFile, String targetFile,
      String normalDiffFile, String singleValueDiffFile, int resultCode)
      throws Exception
  {
    // If the command is expected to fail, or if there aren't any differences,
    // then bail out now.
    if ((normalDiffFile == null) || singleValueDiffFile.equals(noDiffsFile))
    {
      return;
    }


    // Generate the diff file.
    File diffOutputFile = File.createTempFile("difftest", "ldif");
    diffOutputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-o", diffOutputFile.getAbsolutePath(),
      "-S"
    };
    String[] argsUseCompare =
    {
      "-s", sourceFile,
      "-t", targetFile,
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), resultCode);


    // Use LDIFModify to generate a new target file.
    File newTargetFile = File.createTempFile("difftest", "newtarget.ldif");
    newTargetFile.deleteOnExit();

    DirectoryServer.getInstance();
    args = new String[]
    {
      "-c", DirectoryServer.getConfigFile(),
      "-s", sourceFile,
      "-m", diffOutputFile.getAbsolutePath(),
      "-t", newTargetFile.getAbsolutePath()
    };

    assertEquals(LDIFModify.ldifModifyMain(args, true, System.out, System.err),
                 0);


    // Use LDIFDiff again to verify that there are effectively no differences
    // between the original target and the new target.
    File newDiffFile = File.createTempFile("difftest", "newdiff.ldif");
    newDiffFile.deleteOnExit();

    args = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      "-o", newDiffFile.getAbsolutePath()
    };
    argsUseCompare = new String[]
    {
      "-s", targetFile,
      "-t", newTargetFile.getAbsolutePath(),
      // No need to write to the outputFile
      "--useCompareResultCode"
    };

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(newDiffFile), calcChecksum(noDiffsFile));
    assertEquals(LDIFDiff
        .mainDiff(argsUseCompare, true, System.out, System.err), COMPARE_TRUE);

    diffOutputFile.delete();
    newTargetFile.delete();
    newDiffFile.delete();
  }


  /**
   * Retrieves the names of the files that should be used when testing the
   * ldif-diff tool.  Each element of the outer array should be an array
   * containing the following elements:
   * <OL>
   *   <LI>The path to the source LDIF file</LI>
   *   <LI>The path to the target LDIF file</LI>
   *   <LI>The path to the file with attributes to be ignored</LI>
   *   <LI>The path to the diff file</LI>
   * </OL>
   */
  @DataProvider(name = "ignoreattributesdata")
  public Object[][] getIATestData()
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String ldifRoot  = buildRoot + File.separator + "tests" + File.separator +
                       "unit-tests-testng" + File.separator + "resource" +
                       File.separator + "ldif-diff" + File.separator;

    return new Object[][]
    {
      // Make changes to multiple entries in the target->source direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "ignore-attributes",
                     ldifRoot + "diff-multipleentries-ignore-attributes.ldif" },

      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "does-not-exist",
                     ldifRoot + "diff-multipleentries-ignore-attributes.ldif" }
    };

  }



  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * normal mode of operation works as expected.  This is a bit tricky because
   * the attributes and values will be written in an indeterminate order, so we
   * can't just use string equality.  We'll have to use a crude checksum
   * mechanism to test whether they are equal.  Combined with other methods in
   * this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "ignoreattributesdata")
  public void testVerifyIgnoreAttributes(String sourceFile, String targetFile,
                               String ignoreAttributesFile,
                               String normalDiffFile)
         throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-a", ignoreAttributesFile,
      "-o", outputFile.getAbsolutePath(),
      "-O"
    };

    if (ignoreAttributesFile.endsWith("does-not-exist"))
    {
      // We expect this to fail, so just make sure that it does.
      assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 1);
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(outputFile), calcChecksum(normalDiffFile));

    outputFile.delete();
  }


  /**
   * Retrieves the names of the files that should be used when testing the
   * ldif-diff tool.  Each element of the outer array should be an array
   * containing the following elements:
   * <OL>
   *   <LI>The path to the source LDIF file</LI>
   *   <LI>The path to the target LDIF file</LI>
   *   <LI>The path to the file with entries to be ignored</LI>
   *   <LI>The path to the diff file</LI>
   * </OL>
   */
  @DataProvider(name = "ignoreentriesdata")
  public Object[][] getIETestData()
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String ldifRoot  = buildRoot + File.separator + "tests" + File.separator +
                       "unit-tests-testng" + File.separator + "resource" +
                       File.separator + "ldif-diff" + File.separator;

    return new Object[][]
    {
      // Make changes to multiple entries in the target->source direction.
      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "ignore-entries",
                     ldifRoot + "diff-multipleentries-ignore-entries.ldif" },

      new Object[] { ldifRoot + "source-multipleentries.ldif",
                     ldifRoot + "target-multipleentries.ldif",
                     ldifRoot + "does-not-exist",
                     ldifRoot + "diff-multipleentries-ignore-entries.ldif" }
    };

  }



  /**
   * Tests the LDIFDiff tool with the provided information to ensure that the
   * normal mode of operation works as expected.  This is a bit tricky because
   * the attributes and values will be written in an indeterminate order, so we
   * can't just use string equality.  We'll have to use a crude checksum
   * mechanism to test whether they are equal.  Combined with other methods in
   * this class, this should be good enough.
   *
   * @param  sourceFile           The path to the file containing the source
   *                              data set.
   * @param  targetFile           The path to the file containing the target
   *                              data set.
   * @param  normalDiffFile       The path to the file containing the expected
   *                              diff in "normal" form (at most one record per
   *                              entry), or {@code null} if the diff is
   *                              supposed to fail.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "ignoreentriesdata")
  public void testVerifyIgnoreEntries(String sourceFile, String targetFile,
                               String ignoreEntriesFile,
                               String normalDiffFile)
         throws Exception
  {
    File outputFile = File.createTempFile("difftest", "ldif");
    outputFile.deleteOnExit();

    String[] args =
    {
      "-s", sourceFile,
      "-t", targetFile,
      "-e", ignoreEntriesFile,
      "-o", outputFile.getAbsolutePath(),
      "-O"
    };

    if (ignoreEntriesFile.endsWith("does-not-exist"))
    {
      // We expect this to fail, so just make sure that it does.
      assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 1);
      return;
    }

    assertEquals(LDIFDiff.mainDiff(args, true, System.out, System.err), 0);
    assertEquals(calcChecksum(outputFile), calcChecksum(normalDiffFile));

    outputFile.delete();
  }
}

