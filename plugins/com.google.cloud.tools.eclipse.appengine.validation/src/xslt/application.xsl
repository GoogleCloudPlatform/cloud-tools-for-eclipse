<?xml version="1.0" encoding="UTF-8"?>
<!--
	This stylesheet removes an <application/> element from appengine-web.xml
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="application"/>

</xsl:stylesheet>