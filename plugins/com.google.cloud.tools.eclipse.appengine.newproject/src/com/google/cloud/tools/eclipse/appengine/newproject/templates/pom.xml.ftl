<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" 
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>
  <packaging>war</packaging>
  <version>1.0-SNAPSHOT</version>

  <groupId>HelloAppEngine</groupId>
  <artifactId>HelloAppEngine</artifactId>

  <properties>
    <app.id>${ProjectID}</app.id>
    <app.version>1</app.version>
    <appengine.version>1.9.37</appengine.version>
    <gcloud.plugin.version>2.0.9.90.v20151210</gcloud.plugin.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.showDeprecation>true</maven.compiler.showDeprecation>
  </properties>

  <prerequisites>
    <maven>3.1.0</maven>
  </prerequisites>

  <dependencies>
    <!-- Compile/runtime dependencies -->
    <dependency>
        <groupId>com.google.appengine</groupId>
        <artifactId>appengine-api-1.0-sdk</artifactId>
        <version>${appengine.version}</version>
    </dependency>
    <dependency>
        <groupId>javax.servlet</groupId>
        <artifactId>servlet-api</artifactId>
        <version>2.5</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>jstl</groupId>
        <artifactId>jstl</artifactId>
        <version>1.2</version>
    </dependency>

    <!-- Test Dependencies -->
    <dependency>
        <groupId>com.google.appengine</groupId>
        <artifactId>appengine-testing</artifactId>
        <version>${appengine.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.google.appengine</groupId>
        <artifactId>appengine-api-stubs</artifactId>
        <version>${appengine.version}</version>
        <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <!-- for hot reload of the web application-->
    <outputDirectory>${project.build.directory}/${project.build.finalName}/WEB-INF/classes</outputDirectory>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>versions-maven-plugin</artifactId>
            <version>2.1</version>
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
            <groupId>org.apache.maven.plugins</groupId>
            <version>3.1</version>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <source>1.7</source>
                <target>1.7</target>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-war-plugin</artifactId>
            <version>2.4</version>
            <configuration>
                <archiveClasses>true</archiveClasses>
                <webResources>
                    <!-- in order to interpolate version from pom into appengine-web.xml -->
                    <resource>
                        <directory>${basedir}/src/main/webapp/WEB-INF</directory>
                        <filtering>true</filtering>
                        <targetPath>WEB-INF</targetPath>
                    </resource>
                </webResources>
            </configuration>
        </plugin>

        <plugin>
            <groupId>com.google.appengine</groupId>
            <artifactId>appengine-maven-plugin</artifactId>
            <version>${appengine.version}</version>
            <configuration>
                <enableJarClasses>false</enableJarClasses>
                <version>${app.version}</version>
                <!-- Comment in the below snippet to bind to all IPs instead of just localhost -->
                <!-- address>0.0.0.0</address>
                <port>8080</port -->
                <!-- Comment in the below snippet to enable local debugging with a remote debugger
                     like those included with Eclipse or IntelliJ -->
                <!-- jvmFlags>
                  <jvmFlag>-agentlib:jdwp=transport=dt_socket,address=8000,server=y,suspend=n</jvmFlag>
                </jvmFlags -->
            </configuration>
        </plugin>
        <plugin>
          <groupId>com.google.appengine</groupId>
          <artifactId>gcloud-maven-plugin</artifactId>
          <version>${gcloud.plugin.version}</version>
          <configuration>
            <set_default>true</set_default>
          </configuration>
        </plugin>
    </plugins>
  </build>
</project>
