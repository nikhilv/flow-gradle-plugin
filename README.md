# flow-gradle-plugin
A gradle plugin that houses the logic to build our ElectricFlow plugins.

To build plugin use ./gradlew jar.
To upload on Bintray use ./gradlew bintrayUpload.
If you make multiple uploads of same version, make sure you delete version through bintray web interface. 

Sample build.gradle:

```groovy
buildscript {
    repositories {
        maven {
            url 'http://dl.bintray.com/ecpluginsdev/maven'
        }

        jcenter()
    }
    dependencies {
        classpath group: 'com.electriccloud.plugins', name: 'flow-gradle-plugin', version: '+'
    }
}

group = 'com.electriccloud'
description = "Plugins : <plugin name>"
version = "1.0.0"

apply plugin: 'flow-gradle-plugin'

gwt {
	modules 'GWT module name, e.g. ecplugins.<plugin>.ConfigurationManagement'
}

```
