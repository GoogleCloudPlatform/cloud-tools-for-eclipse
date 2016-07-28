[![unstable](http://badges.github.io/stability-badges/dist/unstable.svg)](http://github.com/badges/stability-badges)


This project provides an Eclipse plugin for building, debugging, and deploying Google Cloud Platform applications.

_TL;DR_: `mvn -Dtycho.toolchains=SYSTEM package` should
generate a build into `gcp-repo/target/repository`.

# Development

This project is built using _Maven Tycho_, a set of extensions to
Maven for building Eclipse bundles and features. 

## Requirements

1. Eclipse 4.5 (Mars) or later.  It's easiest to use the _Eclipse IDE for Java EE Developers_.

  1. The [m2eclipse plugin](http://www.eclipse.org/m2e/) (also called m2e) installed
     to import the projects into Eclipse.

1. Maven 3.3.9 or later.

1. JDK 7

1. git

1. Clone the project to a local directory using `git clone
   https://github.com/GoogleCloudPlatform/gcloud-eclipse-tools.git`.


##Configuring Maven/Tycho Builds

The plugin is built using Maven/Tycho and targeted to Java 7.

### Changing the Eclipse Platform compilation and testing target

By default, the build is targeted against Eclipse Mars / 4.5. 
You can explicitly set the `eclipse.target` property to `mars` (4.5)
or `neon` (4.6).
```
$ mvn -Declipse.target=neon package
```

### Configuring Maven/Tycho Toolchains

We use Tycho's support for Maven Toolchains to ensure that Java 8
features do not creep into the code.  This support is enabled by
compiling with the [`useJDK=BREE`](https://eclipse.org/tycho/sitedocs/tycho-compiler-plugin/compile-mojo.html)
setting that ensures bundles are compiled with a JDK that matches
the bundle's `Bundle-RequiredExecutionEnvironment`.  This setting
requires configuring [Maven's toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html)
to point to appropriate JRE installations.  Tycho further requires
that a toolchain defines an `id` for the specified _Execution
Environment_ identifier.  For example, a `~/.m2/toolchains.xml` to
configure Maven for a Java 7 toolchain on a Mac might be:

```
<?xml version="1.0"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <id>JavaSE-1.7</id> <!-- the Execution Environment -->
      <version>1.7</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>/Library/Java/JavaVirtualMachines/jdk1.7.0_75.jdk/Contents/Home/jre</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

Note that _jdkHome_ above specifies the `jre/` directory: Tycho sets
the default boot classpath to _jdkHome_`/lib/*`, _jdkHome_`/lib/ext/*`,
and _jdkHome_`/lib/endorsed/*`.  For many JDKs, including Oracle's JDK
and the OpenJDK, those directories are actually found in the `jre/`
directory.  Compilation errors such as `java.lang.String` not found
and `java.lang.Exception` not found
indicate a misconfigured _jdkHome_.

You can disable the use of toolchains by setting the `tycho.toolchains`
property to `SYSTEM`.


## Import into Eclipse

We pull in some dependencies directly from Maven-style repositories,
such as Maven Central and the Sonatype staging repository, which isn't
directly supported within Eclipse.  We have a few hoops to jump
through to set up a working development environment.

### Assemble the IDE Target Platform

The Eclipse IDE and Tycho both use a _Target Platform_ to manage
the dependencies for the source bundles and features under development.
Although Tycho can pull dependencies directly from Maven-style
repositories, Eclipse cannot.  So we use Tycho to cobble together
a target platform suitable for the Eclipse IDE.
```
$ mvn -Pide-target-platform package
```
This command builds the project, but also creates a local copy of the
target platform, including any Maven dependencies, into
[`eclipse/ide-target-platform/target/repository`](eclipse/ide-target-platform/target/repository).

The target platform is affected by the `eclipse.target` property,
described below.

### Steps to import into the Eclipse IDE


1. Setup JDK 7 in Eclipse

  1. Select `Window/Preferences` (on Mac `Eclipse/Preferences`).

  1. Under `Java/Installed JREs` click `Add`.

  1. Select Standard VM and click `Next`.

  1. Select the folder that contains the JDK 7 installation by clicking
     `Directory`.

  1. Click `Finish`.

  1. Select `Java/Installed JREs/Execution Environments` page.

  1. Click on `JavaSE-1.7` in the list on the left under `Execution
     Environments:`.

  1. The JDK just added should show up in the list on the right along with other
     installed JDKs/JREs. Set the checkbox next the the JDK 7 added in the
     previous steps to mark it as compatible with the `JavaSE-1.7` execution
     environment.

  1. Click `Apply`.

2. Set up the Target Platform

  1. Keeping the _Preferences_ open, go to _Plug-in
     Development > Target Platform_.
  2. Check the _GCP Development Target (ide-target-repository)_.
     _GCP_ targets listed there.
  3. Click _OK_.
  7. Eclipse will load the target.

3. Import the projects

  1. Select `File/Import...` menu in Eclipse.

  1. Select `Existing Maven Projects` from the list.

  1. Click `Browse...` and select the directory that
     contains the project.

  1. Under `Projects:` the `pom.xml` files representing modules should be
     displayed. Make sure that all of them are selected, and click `Finish`.

  1. Maven may prompt you to install several additional plugin connector plugins from
  [Tycho](https://eclipse.org/tycho/) if they are not already installed. Click
  `Finish` to install them. If Eclipse prompts you to install any other
  plugins, do so.

  1. Restart Eclipse when prompted.

4. Check the imported projects:
  1. There should be no errors in the `Markers` or `Problems` views in Eclipse.
    However you may see several low-priority warnings.

  1. Right-click the `gcloud-eclipse-tools.launch` file under the `trunk` module in the
  `Package Explorer`.

  1. Select `Run Configurations...`

  1. Go to the second tab for `Arguments`

  1. Click the `Variables...` button

  1. Click the `Edit variables...` button

  1. Click `New...`

  1. Set the name to `oauth_id`, and the value to the value you want to use, description optional

  1. Click `OK`, the variable will appear in the list

  1. Repeat steps 6-8 but use `oauth_secret` as the name and use the corresponding value

  1. Click `OK` to close the edit variables dialog

  1. Click `Cancel` to close the variable selection dialog

  1. Click `Apply` to apply the changes to the run config
  
  1. Click to `Run` button

  1. A new instance of Eclipse should be launched with the plugin installed.


# Updating Target Platforms

### Updating the `.target` files

We use _Target Platform_ files (`.target`) to collect the dependencies used
for the build.  These targets specify exact versions of the bundles and
features being built against.  We currently maintain two target platforms,
targeting the latest version of the current and previous release trains.
This is currently:

  - Eclipse Mars (4.5 SR2): [`eclipse/mars/gcp-eclipse-mars.target`](eclipse/mars/gcp-eclipse-mars.target) 
  - Eclipse Neon (4.6): [`eclipse/neon/gcp-eclipse-neon.target`](eclipse/neon/gcp-eclipse-neon.target)

These `.target` files are generated and *should not be manually updated*.
Updating `.target` files directly becomes a chore once it has more than a 
couple of dependencies.  We instead generate these `.target`s from 
_Target Platform Definition_ `.tpd` files.
The `.tpd` files use a simple DSL to specify the bundles and features,
and the location of the repositories containing them.   
The `.tpd` files are processed using the [TPD Editor](https://github.com/mbarbero/fr.obeo.releng.targetplatform)
which resolves the specified dependencies and creates a `.target`.
The process is:

  1. Install the TPD Editor, if necessary
     - Use _Help > Install New Software_ and specify `http://mbarbero.github.io/fr.obeo.releng.targetplatform/p2/latest/`
       as the location.
     - Restart Eclipse when prompted
  2. Open the `.tpd` file in Eclipse.
  3. Make any necessary changes and save.
     - Note that the TPDs specify artifacts using their _p2 identifiers_.
       Bundles are specified using their OSGi Bundle Symbolic Name (e.g.,
       `org.eclipse.core.runtime`).
       Features are specified using their Feature ID suffixed with `.feature.group`
       (e.g., `org.eclipse.rcp.feature.group`).  
  4. Right-click in the editor and choose _Create Target Definition File_
     to update the corresponding .target file.

Both the `.tpd` and `.target` files should be committed.

### Updating the Eclipse IDE Target Platforms

The IDE Target Platform, defined in `eclipse/ide-target-platform`,
may need to be updated when dependencies are added or removed.  The
contents are defined in the `category.xml` file, which specifies
the list of features and bundles that should be included.  This
file can be edited using the Category editor in Eclipse.  Ideally
the version should be specified as `"0.0.0"` to indicate that the
current version found should be used.  Unlike the `.tpd` file,
the identifiers are not p2 identifiers, and so features do not
require the `.feature.group` suffix.


# Release

## Compile-time Constant Injection

We plug a few constants into code at compile-time.  We do this to avoid
hard-coding these values and hamper their public visibilty.

| Constant                     | Bundle        | System Property       |
| ---------------------------- | ------------- | --------------------- |
| OAuth client ID              | login         | `oauth.client.id`     |
| OAuth client secret          | login         | `oauth.client.secret` |
| Google Analytics Tracking ID | usagetracking | `ga.tracking.id`      |

The Java classes that hold these constant values are auto-generated
from template Java source files, using [templating-maven-plugin]
(http://www.mojohaus.org/templating-maven-plugin/).  The values are
populated from system properties in the auto-gen process. That is, for
example, `mvn -Doauth.client.id=id1234 templating:filter-sources` or
`mvn -Doauth.client.id=id1234 package` will result in injecting `id1234`
as an OAuth client ID.

### Injecting Constants for Debugging

You can also easily inject values for an Eclipse debug session, since Eclipse
auto-generates the Java classes (if configured to do auto-build).  Providing
relevant system properties when launching a dev instance of Eclipse and deleting
a file to re-generate will do the job.
