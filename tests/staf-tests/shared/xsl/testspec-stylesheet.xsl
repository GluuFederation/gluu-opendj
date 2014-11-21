<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<!--
 ! CDDL HEADER START
 !
 ! The contents of this file are subject to the terms of the
 ! Common Development and Distribution License, Version 1.0 only
 ! (the "License").  You may not use this file except in compliance
 ! with the License.
 !
 ! You can obtain a copy of the license at
 ! trunk/opends/resource/legal-notices/OpenDS.LICENSE
 ! or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 ! See the License for the specific language governing permissions
 ! and limitations under the License.
 !
 ! When distributing Covered Code, include this CDDL HEADER in each
 ! file and include the License file at
 ! trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 ! add the following below this CDDL HEADER, with the fields enclosed
 ! by brackets "[]" replaced with your own identifying information:
 !      Portions Copyright [yyyy] [name of copyright owner]
 !
 ! CDDL HEADER END
 !
 !      Copyright 2007-2008 Sun Microsystems, Inc.
 !      Portions Copyright 2011 ForgeRock AS
 ! -->

<xsl:output method="html"/>

<xsl:template match="/">

  <xsl:element name="html">
  
    <xsl:variable name="opends-url" select="'http://www.forgerock.com/'"/>
    <xsl:variable name="opends-images" select="concat($opends-url,'public/images/')"/>
    <xsl:variable name="opends-logo" select="concat($opends-images,'opendj_logo.png')"/>

    <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print"/>

  <xsl:element name="head">

    <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print"/>
    <link rel="stylesheet" href="https://opends.dev.java.net/public/css/opends.css" type="text/css"/>

    <xsl:element name="title">
      <xsl:value-of select="'Test Specification'"/>
    </xsl:element>

  </xsl:element>

  <xsl:element name="body">

    <xsl:variable name="testgroup" select="/qa/product/testphase/testgroup"/>
                   
    <xsl:for-each select="$testgroup">
        
        <xsl:element name="table">
          <xsl:attribute name="class">
            <xsl:value-of select="'tertmasttable'"/>
          </xsl:attribute>
          <xsl:attribute name="cellspacing">
            <xsl:value-of select="'0'"/>
          </xsl:attribute>
          <xsl:attribute name="width">
            <xsl:value-of select="'100%'"/>
          </xsl:attribute>
          <xsl:element name="tr">
            <xsl:element name="td">
              <xsl:element name="div">
                <xsl:attribute name="class">
                  <xsl:value-of select="'collectionheader'"/>
                </xsl:attribute>
                <xsl:value-of select="concat('Test Specification for ',@name)"/>
              </xsl:element>
            </xsl:element>
            <xsl:element name="td">
              <xsl:attribute name="width">
                <xsl:value-of select="'10%'"/>
              </xsl:attribute>
              <xsl:element name="a">
                <xsl:attribute name="href">
                  <xsl:value-of select="$opends-url"/>
                </xsl:attribute>
                  <xsl:element name="img">
                  <xsl:attribute name="src">
                    <xsl:value-of select="$opends-logo"/>
                  </xsl:attribute>
                  <xsl:attribute name="alt">
                    <xsl:value-of select="'OpenDJ Logo'"/>
                  </xsl:attribute>
                  <xsl:attribute name="align">
                    <xsl:value-of select="'middle'"/>
                  </xsl:attribute>
                  <xsl:attribute name="border">
                    <xsl:value-of select="'0'"/>
                  </xsl:attribute>
                  <xsl:attribute name="height">
                    <xsl:value-of select="'33'"/>
                  </xsl:attribute>
                  <xsl:attribute name="width">
                    <xsl:value-of select="'104'"/>
                  </xsl:attribute>
                </xsl:element>
              </xsl:element>
            </xsl:element>
          </xsl:element>
        </xsl:element>

        <hr noshade="noshade" size="1" />

        <xsl:element name="br"/>

        <!-- Test Group Table -->
        <xsl:element name="table">
        
          <xsl:attribute name="border">1</xsl:attribute>
          <xsl:attribute name="width">80%</xsl:attribute>

          <xsl:element name="tr">
            <xsl:attribute name="bgcolor">yellow</xsl:attribute>
            <xsl:element name="td">
              <xsl:attribute name="width">20%</xsl:attribute>
              <xsl:element name="b">
                <xsl:value-of select="'Test Group'"/>
              </xsl:element>
            </xsl:element>
            <xsl:element name="td">
                <xsl:value-of select="@name"/>
            </xsl:element>
          </xsl:element>

          <xsl:element name="tr">
            <xsl:element name="td">
              <xsl:attribute name="width">20%</xsl:attribute>
              <xsl:element name="b">
                <xsl:value-of select="'Test Group Purpose'"/>
              </xsl:element>
            </xsl:element>
            <xsl:element name="td">
                <xsl:value-of select="grouppurpose"/>
            </xsl:element>
          </xsl:element>

        </xsl:element>

        <xsl:element name="br"/>
        
        <!-- Test Suite List -->
        <xsl:element name="ol">
          <xsl:for-each select="testsuite">
            <xsl:element name="li">
              <xsl:element name="a">
                <xsl:attribute name="href">
                  <xsl:value-of select="concat('#',@name)"/>
                </xsl:attribute>
                <xsl:value-of select="@name"/>
              </xsl:element>
              <xsl:value-of select="concat(' : ',suitepurpose)"/>
            </xsl:element>
          </xsl:for-each>
        </xsl:element>

        <xsl:for-each select="testsuite">
        
          <!-- Test Suite Header -->
          <xsl:element name="h2">
            <xsl:value-of select="@name"/>
          </xsl:element>

          <!-- Test Suite Anchor -->
          <xsl:element name="a">
            <xsl:attribute name="name">
              <xsl:value-of select="@name"/>
            </xsl:attribute>
          </xsl:element>
                             
          <!-- Test Suite Table -->
          <xsl:element name="table">
          
            <xsl:attribute name="border">1</xsl:attribute>
            <xsl:attribute name="width">80%</xsl:attribute>
            
            <xsl:element name="tr">
              <xsl:attribute name="bgcolor">aqua</xsl:attribute>
              <xsl:element name="td">
                <xsl:attribute name="width">20%</xsl:attribute>
                <xsl:element name="b"> 
                  <xsl:value-of select="'Test Suite Name'"/>
                </xsl:element>
              </xsl:element>
              <xsl:element name="td">
                  <xsl:value-of select="@name"/>
              </xsl:element>
            </xsl:element>

            <xsl:element name="tr">
              <xsl:element name="td">
                <xsl:attribute name="width">20%</xsl:attribute>
                <xsl:element name="b">
                  <xsl:value-of select="'Purpose'"/>
                </xsl:element>
              </xsl:element>
              <xsl:element name="td">
                  <xsl:value-of select="suitepurpose"/>
              </xsl:element>
            </xsl:element>

            <xsl:element name="tr">
              <xsl:element name="td">
                <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Suite Group'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="suitegroup"/>
              </xsl:element>
            </xsl:element>
                                
            </xsl:element>          

            <xsl:element name="br"/>

            <!-- Test Case List -->
            <xsl:element name="ol">
              <xsl:for-each select="testcase">
                <xsl:element name="li">
                  <xsl:element name="a">
                    <xsl:attribute name="href">
                      <xsl:value-of select="concat('#',../@name,@name)"/>
                    </xsl:attribute>
                    <xsl:value-of select="@name"/>
                  </xsl:element>
                  <xsl:value-of select="concat(' : ',purpose)"/>
                </xsl:element>
              </xsl:for-each>
            </xsl:element>

            <xsl:element name="br"/>
        
            <!-- Test Cases -->
            <xsl:for-each select="testcase">
        
              <!-- Test Case Table -->       
              <xsl:element name="table">
                <xsl:attribute name="border">1</xsl:attribute>
                <xsl:attribute name="width">80%</xsl:attribute>
           
              <!-- Test Case Anchor -->
              <xsl:element name="a">
                <xsl:attribute name="name">
                  <xsl:value-of select="concat(../@name,@name)"/>
                </xsl:attribute>
              </xsl:element>

              <!-- Test Name -->
              <xsl:element name="a">
                <xsl:attribute name="name">
                  <xsl:value-of select="testid"/>
                </xsl:attribute>
              </xsl:element>
              <xsl:element name="tr">
                <xsl:attribute name="bgcolor">lightblue</xsl:attribute>              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="a">
                    <xsl:attribute name="width">20%</xsl:attribute>
                  </xsl:element>
                  <xsl:element name="b">
                    <xsl:value-of select="'Test Name'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="@name"/>
                </xsl:element>
              </xsl:element>

              <!-- Test Purpose -->          
              <xsl:element name="tr">              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Test Purpose'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="purpose"/>
                </xsl:element>
              </xsl:element>
                                             
              <!-- Test Script -->            
              <xsl:element name="tr">              
                <xsl:element name="td">
                <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Test Script'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:element name="a">
                    <xsl:attribute name="href">
                      <xsl:value-of select="testscript"/>
                    </xsl:attribute>
                    <xsl:value-of select="testscript"/>
                  </xsl:element>
                </xsl:element>
              </xsl:element>
                                               
              <!-- Test Issue -->            
              <xsl:element name="tr">              
                <xsl:element name="td">
                <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Test Issue'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:element name="a">
                    <xsl:attribute name="href">
                      <xsl:value-of select="concat('http://java.net/jira/browse/OPENDS-',testissue)"/>
                    </xsl:attribute>
                    <xsl:value-of select="concat('OPENDS-',testissue)"/>
                  </xsl:element>
                </xsl:element>
              </xsl:element>
                                               
              <!-- Test Preamble -->          
              <xsl:element name="tr">              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Preamble'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="preamble"/>
                </xsl:element>
              </xsl:element>
                                                                                     
              <!-- Test Steps -->          
              <xsl:element name="tr">              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Test Steps'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:for-each select="steps/step">
                    <xsl:value-of select="."/>
                    <xsl:element name="br"/>    
                  </xsl:for-each>
                </xsl:element>
              </xsl:element>
                                            
              <!-- Test Postamble -->          
              <xsl:element name="tr">              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Postamble'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="postamble"/>
                </xsl:element>
              </xsl:element>
                                                                                                   
              <!-- Test Result -->          
              <xsl:element name="tr">              
                <xsl:element name="td">
                  <xsl:attribute name="width">20%</xsl:attribute>
                  <xsl:element name="b"> 
                    <xsl:value-of select="'Test Result'"/>
                  </xsl:element>
                </xsl:element>
                <xsl:element name="td">
                  <xsl:value-of select="result"/>
                </xsl:element>
              </xsl:element>
                                                                                                                    
            </xsl:element>
        
            <xsl:element name="br"/>    
    
          </xsl:for-each>
                  
        </xsl:for-each>

    </xsl:for-each>


    </xsl:element> 
     
  </xsl:element>


</xsl:template>


</xsl:stylesheet>
