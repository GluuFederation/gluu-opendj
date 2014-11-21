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
  !
  !      Copyright 2008 Sun Microsystems, Inc.
  ! -->
<xsl:stylesheet version="1.0" xmlns:adm="http://www.opends.org/admin"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <!-- 
    Templates for processing attribute type properties.
  -->
  <xsl:template match="adm:attribute-type" mode="java-value-imports">
    <import>org.opends.server.types.AttributeType</import>
  </xsl:template>
  <xsl:template match="adm:attribute-type" mode="java-value-type">
    <xsl:value-of select="'AttributeType'" />
  </xsl:template>
  <xsl:template match="adm:attribute-type"
    mode="java-definition-type">
    <xsl:value-of select="'AttributeTypePropertyDefinition'" />
  </xsl:template>
</xsl:stylesheet>
