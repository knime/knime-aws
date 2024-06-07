## Update the AWS Java SDK in this plugin

  1. Bump the versions in `libs/fetch_jars/pom.xml` and run `mvn package` inside the `libs/fetch_jars` directory
  2. Generate the list of packages to export with the following command inside the `libs` directory, and update `META-INF/MANIFEST.MF`: `find . -name '*.jar' | xargs -n 1 zipinfo -1 | grep software/amazon/awssdk | egrep -v '(META|internal|hirdparty)' | egrep '/$' | sed 's!/$!,!; s!/!.!g' | sort | uniq`
  3. Update the list of libraries in `META-INF/MANIFEST.MF`, `.classpath`, `build.properties` and `libraries_license/licenses.csv`
