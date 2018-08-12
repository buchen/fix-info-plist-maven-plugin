# About

Small Maven plugin to "fix" the Info.plist file generated by p2 during a [Tycho](https://eclipse.org/tycho) product build.

Apparently there is no easy way to provide a custom Info.plist file for Eclipse RCP product - see the [Eclipse Bugzilla 339526](https://bugs.eclipse.org/bugs/show_bug.cgi?id=339526) with the feature request. One could patch the original launcher bundles. Instead, this Maven plugin manipulates the Info.plist between the ```materialize-products``` and ```archive-products``` goals of the P2 Directory Tycho plugin.

This plugin updates
* the Info.plist file in the products directory (target/products/.../macosx/cocoa/x86_84/.../Contents/Info.plist)
*  and, if exists, the archived 'binaries' in the repository (target/repository/binary/...executable.cocoa.macosx.x86_64_...)

This plugin was inspired by https://github.com/komaz/eclipse-ini-patcher

## Configuration

**First**, add the repository (if neeeded):

```xml
<pluginRepositories>
  <pluginRepository>
    <id>buchen-maven-repo</id>
    <url>http://buchen.github.io/maven-repo</url>
    <layout>default</layout>
  </pluginRepository>
</pluginRepositories>
```

**Second**, make sure that the two goals ```tycho-p2-director-plugin``` are attached to two different phases (here: package and verify):

```xml
<plugin>
  <groupId>org.eclipse.tycho</groupId>
  <artifactId>tycho-p2-director-plugin</artifactId>
  <version>${tycho-version}</version>
  <executions>
    <execution>
      <id>materialize-products</id>
      <goals>
        <goal>materialize-products</goal>
      </goals>
    </execution>
    <execution>
      <id>archive-products</id>
      <phase>verify</phase>
      <goals>
        <goal>archive-products</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <products>
      <product>
        <id>your.rcp.product</id>
        <rootFolders>
          <macosx>YourRCPApplication.app</macosx>
        </rootFolders>
        ...
      </product>
    </products>
  </configuration>
</plugin>
```

**Finally** configure the Info.plist properties to be added/updated/removed:

```xml
<plugin>
  <groupId>name.abuchen</groupId>
  <artifactId>fix-info-plist-maven-plugin</artifactId>
  <version>1.3</version>
  <executions>
    <execution>
      <id>fix-info-plist</id>
      <phase>package</phase>
      <goals>
        <goal>fix-info-plist</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <productId>your.rcp.product</productId>
    <appName>YourRCPApplication.app</appName>
    <properties>
      <property>
        <name>CFBundleGetInfoString</name>
        <value>Replace default Eclipse copyright with your own.</value>
      </property>
      <property>
        <name>Eclipse</name>
        <value /> <!-- key 'Eclipse' will be removed because there is no value -->
      </property>
      <property>
        <name>CFBundleTypeExtensions</name>
        <value>[comma,separated,array,values]</value>
      </property>
    </properties>
  </configuration>
</plugin>
```

For a real life configuration, have a look at the [Portfolio Performance RCP](https://github.com/buchen/portfolio/blob/master/portfolio-product/pom.xml).

## License

Eclipse Public License
http://www.eclipse.org/legal/epl-v10.html
