guava-vfs
=========

This library provides [Guava's Files](http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/io/Files.html) utility methods with [Apache Commons VCS](https://commons.apache.org/proper/commons-vfs/) as backend. In most methods, the signature has changed from `File file` to `String file` which is handed to [`FileSystemManager#resolveFile`](https://commons.apache.org/proper/commons-vfs/apidocs/org/apache/commons/vfs2/FileSystemManager.html#resolveFile(java.lang.String)).

Usage
-----
This project is being published to [bintray.com/simon04/maven/guava-vfs](https://bintray.com/simon04/maven/guava-vfs/). To use it, add the following to your `build.gradle`

```gradle
repositories {
  maven {
    url 'https://dl.bintray.com/simon04/maven/'
  }
}

dependencies {
  compile(group: 'net.simon04', name: 'guava-vfs', version: '1.1.0', ext: 'jar')
}
```

License
-------
* MIT

Author
------
* Simon Legner <Simon.Legner@gmail.com>
