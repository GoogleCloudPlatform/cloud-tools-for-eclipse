<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>
  <packaging>war</packaging>
  <version>${projectVersion}</version>

  <groupId>${projectGroupId}</groupId>
  <artifactId>${projectArtifactId}</artifactId>

  <properties>
    <appengine.maven.plugin.version>${mavenPluginVersion}</appengine.maven.plugin.version>
    <appengine.api.sdk.version>${appEngineApiSdkVersion}</appengine.api.sdk.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <maven.compiler.source>${compilerVersion}</maven.compiler.source>
    <maven.compiler.target>${compilerVersion}</maven.compiler.target>
    <maven.compiler.showDeprecation>true</maven.compiler.showDeprecation>
  </properties>
  
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-bom</artifactId>
        <version>${googleCloudJavaBomVersion}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Compile/runtime dependencies -->
<#if servletVersion == "2.5">
    <dependency>
      <groupId>javax.servlet</groupId>
      <artifactId>servlet-api</artifactId>
      <version>2.5</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>javax.servlet.jsp</groupId>
      <artifactId>jsp-api</artifactId>
      <version>2.1</version>
      <scope>provided</scope>
    </dependency>
<#else>
    <dependency>
      <groupId>javax.servlet</groupId>
      <artifactId>javax.servlet-api</artifactId>
      <version>3.1.0</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.eclipse.jetty</groupId>
      <artifactId>apache-jsp</artifactId>
      <version>9.3.18.v20170406</version>
      <classifier>nolog</classifier>
      <scope>provided</scope>
    </dependency>
</#if>
    <dependency>
      <groupId>jstl</groupId>
      <artifactId>jstl</artifactId>
      <version>1.2</version>
    </dependency>

    <!-- Test Dependencies -->
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>4.12</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <!-- for hot reload of the web application-->
    <outputDirectory>${r"${project.build.directory}/${project.build.finalName}/WEB-INF/classes"}</outputDirectory>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>versions-maven-plugin</artifactId>
        <version>2.3</version>
        <executions>
          <execution>
            <phase>compile</phase>
            <goals>
              <goal>display-dependency-updates</goal>
              <goal>display-plugin-updates</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

      <plugin>
        <groupId>com.google.cloud.tools</groupId>
        <artifactId>appengine-maven-plugin</artifactId>
        <version>${r"${appengine.maven.plugin.version}"}</version>
      </plugin>
    </plugins>
  </build>
</project>
