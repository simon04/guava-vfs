/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.simon04.guavavfs;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteProcessor;
import com.google.common.io.Closer;
import com.google.common.io.LineProcessor;
import com.google.common.primitives.Bytes;

import junit.framework.TestSuite;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Unit test for {@link VirtualFiles}.
 *
 * @author Chris Nokleberg
 */

public class FilesTest extends IoTestCase {

  /*
  public static TestSuite suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(ByteSourceTester.tests("VirtualFiles.asByteSource[File]",
        SourceSinkFactories.fileByteSourceFactory(), true));
    suite.addTest(ByteSinkTester.tests("VirtualFiles.asByteSink[File]",
        SourceSinkFactories.fileByteSinkFactory()));
    suite.addTest(ByteSinkTester.tests("VirtualFiles.asByteSink[File, APPEND]",
        SourceSinkFactories.appendingFileByteSinkFactory()));
    suite.addTest(CharSourceTester.tests("VirtualFiles.asCharSource[File, Charset]",
        SourceSinkFactories.fileCharSourceFactory()));
    suite.addTest(CharSinkTester.tests("VirtualFiles.asCharSink[File, Charset]",
        SourceSinkFactories.fileCharSinkFactory()));
    suite.addTest(CharSinkTester.tests("VirtualFiles.asCharSink[File, Charset, APPEND]",
        SourceSinkFactories.appendingFileCharSinkFactory()));
    suite.addTestSuite(FilesTest.class);
    return suite;
  }      */

  public void testToByteArray() throws IOException {
    String asciiFile = getTestFile("ascii.txt");
    String i18nFile = getTestFile("i18n.txt");
    assertTrue(Arrays.equals(ASCII.getBytes(Charsets.US_ASCII),
        VirtualFiles.toByteArray(asciiFile)));
    assertTrue(Arrays.equals(I18N.getBytes(Charsets.UTF_8),
        VirtualFiles.toByteArray(i18nFile)));
    assertTrue(Arrays.equals(I18N.getBytes(Charsets.UTF_8),
        VirtualFiles.asByteSource(i18nFile).read()));
  }

  public void testReadFile_withCorrectSize() throws IOException {
    String asciiFile = getTestFile("ascii.txt");

    Closer closer = Closer.create();
    try {
      InputStream in = closer.register(new FileInputStream(asciiFile));
      byte[] bytes = VirtualFiles.readFile(in, VirtualFiles.length(asciiFile));
      assertTrue(Arrays.equals(ASCII.getBytes(Charsets.US_ASCII), bytes));
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  public void testReadFile_withSmallerSize() throws IOException {
    String asciiFile = getTestFile("ascii.txt");

    Closer closer = Closer.create();
    try {
      InputStream in = closer.register(new FileInputStream(asciiFile));
      byte[] bytes = VirtualFiles.readFile(in, 10);
      assertTrue(Arrays.equals(ASCII.getBytes(Charsets.US_ASCII), bytes));
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  public void testReadFile_withLargerSize() throws IOException {
    String asciiFile = getTestFile("ascii.txt");

    Closer closer = Closer.create();
    try {
      InputStream in = closer.register(new FileInputStream(asciiFile));
      byte[] bytes = VirtualFiles.readFile(in, 500);
      assertTrue(Arrays.equals(ASCII.getBytes(Charsets.US_ASCII), bytes));
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  public void testReadFile_withSizeZero() throws IOException {
    String asciiFile = getTestFile("ascii.txt");

    Closer closer = Closer.create();
    try {
      InputStream in = closer.register(new FileInputStream(asciiFile));
      byte[] bytes = VirtualFiles.readFile(in, 0);
      assertTrue(Arrays.equals(ASCII.getBytes(Charsets.US_ASCII), bytes));
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  /**
   * A {@link File} that provides a specialized value for {@link File#length()}.
   */
  private static class BadLengthFile extends File {

    private final long badLength;

    public BadLengthFile(String delegate, long badLength) {
      super(delegate);
      this.badLength = badLength;
    }

    @Override
    public long length() {
      return badLength;
    }

    private static final long serialVersionUID = 0;
  }

  public void testToString() throws IOException {
    String asciiFile = getTestFile("ascii.txt");
    String i18nFile = getTestFile("i18n.txt");
    assertEquals(ASCII, VirtualFiles.toString(asciiFile, Charsets.US_ASCII));
    assertEquals(I18N, VirtualFiles.toString(i18nFile, Charsets.UTF_8));
    assertThat(VirtualFiles.toString(i18nFile, Charsets.US_ASCII))
        .isNotEqualTo(I18N);
  }

  public void testWriteString() throws IOException {
    String temp = createTempFile();
    VirtualFiles.write(I18N, temp, Charsets.UTF_16LE);
    assertEquals(I18N, VirtualFiles.toString(temp, Charsets.UTF_16LE));
  }

  public void testWriteBytes() throws IOException {
    String temp = createTempFile();
    byte[] data = newPreFilledByteArray(2000);
    VirtualFiles.write(data, temp);
    assertTrue(Arrays.equals(data, VirtualFiles.toByteArray(temp)));

    try {
      VirtualFiles.write(null, temp);
      fail("expected exception");
    } catch (NullPointerException expected) {
    }
  }

  public void testAppendString() throws IOException {
    String temp = createTempFile();
    VirtualFiles.append(I18N, temp, Charsets.UTF_16LE);
    assertEquals(I18N, VirtualFiles.toString(temp, Charsets.UTF_16LE));
    VirtualFiles.append(I18N, temp, Charsets.UTF_16LE);
    assertEquals(I18N + I18N, VirtualFiles.toString(temp, Charsets.UTF_16LE));
    VirtualFiles.append(I18N, temp, Charsets.UTF_16LE);
    assertEquals(I18N + I18N + I18N, VirtualFiles.toString(temp, Charsets.UTF_16LE));
  }

  public void testCopyToOutputStream() throws IOException {
    String i18nFile = getTestFile("i18n.txt");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    VirtualFiles.copy(i18nFile, out);
    assertEquals(I18N, out.toString("UTF-8"));
  }

  public void testCopyToAppendable() throws IOException {
    String i18nFile = getTestFile("i18n.txt");
    StringBuilder sb = new StringBuilder();
    VirtualFiles.copy(i18nFile, Charsets.UTF_8, sb);
    assertEquals(I18N, sb.toString());
  }

  public void testCopyFile() throws IOException {
    String i18nFile = getTestFile("i18n.txt");
    String temp = createTempFile();
    VirtualFiles.copy(i18nFile, temp);
    assertEquals(I18N, VirtualFiles.toString(temp, Charsets.UTF_8));
  }

  public void testCopyEqualFiles() throws IOException {
    String temp1 = createTempFile();
    String temp2 = temp1;
    assertEquals(temp1, temp2);
    VirtualFiles.write(ASCII, temp1, Charsets.UTF_8);
    try {
      VirtualFiles.copy(temp1, temp2);
      fail("Expected an IAE to be thrown but wasn't");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(ASCII, VirtualFiles.toString(temp1, Charsets.UTF_8));
  }

  public void testCopySameFile() throws IOException {
    String temp = createTempFile();
    VirtualFiles.write(ASCII, temp, Charsets.UTF_8);
    try {
      VirtualFiles.copy(temp, temp);
      fail("Expected an IAE to be thrown but wasn't");
    } catch (IllegalArgumentException expected) {
    }
    assertEquals(ASCII, VirtualFiles.toString(temp, Charsets.UTF_8));
  }

  public void testCopyIdenticalFiles() throws IOException {
    String temp1 = createTempFile();
    VirtualFiles.write(ASCII, temp1, Charsets.UTF_8);
    String temp2 = createTempFile();
    VirtualFiles.write(ASCII, temp2, Charsets.UTF_8);
    VirtualFiles.copy(temp1, temp2);
    assertEquals(ASCII, VirtualFiles.toString(temp1, Charsets.UTF_8));
  }

  public void testEqual() throws IOException {
    String asciiFile = getTestFile("ascii.txt");
    String i18nFile = getTestFile("i18n.txt");
    assertFalse(VirtualFiles.equal(asciiFile, i18nFile));
    assertTrue(VirtualFiles.equal(asciiFile, asciiFile));

    String temp = createTempFile();
    VirtualFiles.copy(asciiFile, temp);
    assertTrue(VirtualFiles.equal(asciiFile, temp));

    VirtualFiles.copy(i18nFile, temp);
    assertTrue(VirtualFiles.equal(i18nFile, temp));

    VirtualFiles.copy(asciiFile, temp);
    RandomAccessFile rf = new RandomAccessFile(temp, "rw");
    rf.writeByte(0);
    rf.close();
    assertEquals(VirtualFiles.length(asciiFile), VirtualFiles.length(temp));
    assertFalse(VirtualFiles.equal(asciiFile, temp));

    assertTrue(VirtualFiles.asByteSource(asciiFile)
        .contentEquals(VirtualFiles.asByteSource(asciiFile)));
  }

  public void testNewReader() throws IOException {
    String asciiFile = getTestFile("ascii.txt");
    try {
      VirtualFiles.newReader(asciiFile, null);
      fail("expected exception");
    } catch (NullPointerException expected) {
    }

    try {
      VirtualFiles.newReader(null, Charsets.UTF_8);
      fail("expected exception");
    } catch (NullPointerException expected) {
    }

    BufferedReader r = VirtualFiles.newReader(asciiFile, Charsets.US_ASCII);
    try {
      assertEquals(ASCII, r.readLine());
    } finally {
      r.close();
    }
  }

  public void testNewWriter() throws IOException {
    String temp = createTempFile();
    try {
      VirtualFiles.newWriter(temp, null);
      fail("expected exception");
    } catch (NullPointerException expected) {
    }

    try {
      VirtualFiles.newWriter(null, Charsets.UTF_8);
      fail("expected exception");
    } catch (NullPointerException expected) {
    }

    BufferedWriter w = VirtualFiles.newWriter(temp, Charsets.UTF_8);
    try {
      w.write(I18N);
    } finally {
      w.close();
    }

    String i18nFile = getTestFile("i18n.txt");
    assertTrue(VirtualFiles.equal(i18nFile, temp));
  }

  public void testTouch() throws IOException {
    String temp = createTempFile();
    assertTrue(VirtualFiles.exists(temp));
    assertTrue(VirtualFiles.delete(temp));
    assertFalse(VirtualFiles.exists(temp));
    VirtualFiles.touch(temp);
    assertTrue(VirtualFiles.exists(temp));
    VirtualFiles.touch(temp);
    assertTrue(VirtualFiles.exists(temp));
  }

  public void testTouchTime() throws IOException {
    String temp = createTempFile();
    assertTrue(VirtualFiles.exists(temp));
    VirtualFiles.setLastModified(temp, 0);
    assertEquals(0, VirtualFiles.getLastModified(temp));
    VirtualFiles.touch(temp);
    assertThat(VirtualFiles.getLastModified(temp)).isNotEqualTo(0);
  }

  public void testCreateParentDirs_root() throws IOException {
    File file = root();
    assertNull(file.getParentFile());
    assertNull(file.getCanonicalFile().getParentFile());
//    VirtualFiles.createParentDirs(file.getPath());
  }

  public void testCreateParentDirs_relativePath() throws IOException {
    File file = file("nonexistent.file");
    assertNull(file.getParentFile());
    assertNotNull(file.getCanonicalFile().getParentFile());
//    VirtualFiles.createParentDirs(file.getPath());
  }

  public void testCreateParentDirs_noParentsNeeded() throws IOException {
    File file = file(getTempDir(), "nonexistent.file");
    assertTrue(file.getParentFile().exists());
    VirtualFiles.createParentDirs(file.getPath());
  }

  public void testCreateParentDirs_oneParentNeeded() throws IOException {
    File file = file(getTempDir(), "parent", "nonexistent.file");
    File parent = file.getParentFile();
    assertFalse(parent.exists());
    try {
      VirtualFiles.createParentDirs(file.getPath());
      assertTrue(parent.exists());
    } finally {
      assertTrue(parent.delete());
    }
  }

  public void testCreateParentDirs_multipleParentsNeeded() throws IOException {
    File file = file(getTempDir(), "grandparent", "parent", "nonexistent.file");
    File parent = file.getParentFile();
    File grandparent = parent.getParentFile();
    assertFalse(grandparent.exists());
    VirtualFiles.createParentDirs(file.getPath());
    assertTrue(parent.exists());
  }

  public void testCreateParentDirs_nonDirectoryParentExists() throws IOException {
    String parent = getTestFile("ascii.txt");
//    assertTrue(parent.isFile());
    File file = file(parent, "foo");
    try {
      VirtualFiles.createParentDirs(file.getPath());
      fail();
    } catch (IOException expected) {
    }
  }

  public void testCreateTempDir() {
    File temp = VirtualFiles.createTempDir();
    assertTrue(temp.exists());
    assertTrue(temp.isDirectory());
    assertThat(temp.listFiles()).isEmpty();
    assertTrue(temp.delete());
  }

  public void testMove() throws IOException {
    String i18nFile = getTestFile("i18n.txt");
    String temp1 = createTempFile();
    String temp2 = createTempFile();

    VirtualFiles.copy(i18nFile, temp1);
    moveHelper(true, temp1, temp2);
    assertTrue(VirtualFiles.equal(temp2, i18nFile));
  }

  private void moveHelper(boolean success, String from, String to)
      throws IOException {
    try {
      VirtualFiles.move(from, to);
      if (success) {
        assertFalse(VirtualFiles.exists(from));
        assertTrue(VirtualFiles.exists(to));
      } else {
        fail("expected exception");
      }
    } catch (IOException possiblyExpected) {
      if (success) {
        throw possiblyExpected;
      }
    }
  }

  public void testLineReading() throws IOException {
    String temp = createTempFile();
    assertNull(VirtualFiles.readFirstLine(temp, Charsets.UTF_8));
    assertTrue(VirtualFiles.readLines(temp, Charsets.UTF_8).isEmpty());

    PrintWriter w = new PrintWriter(VirtualFiles.newWriter(temp, Charsets.UTF_8));
    w.println("hello");
    w.println("");
    w.println(" world  ");
    w.println("");
    w.close();

    assertEquals("hello", VirtualFiles.readFirstLine(temp, Charsets.UTF_8));
    assertEquals(ImmutableList.of("hello", "", " world  ", ""),
        VirtualFiles.readLines(temp, Charsets.UTF_8));

    assertTrue(VirtualFiles.delete(temp));
  }

  public void testReadLines_withLineProcessor() throws IOException {
    String temp = createTempFile();
    LineProcessor<List<String>> collect = new LineProcessor<List<String>>() {
      List<String> collector = new ArrayList<String>();

      @Override
      public boolean processLine(String line) {
        collector.add(line);
        return true;
      }

      @Override
      public List<String> getResult() {
        return collector;
      }
    };
    assertThat(VirtualFiles.readLines(temp, Charsets.UTF_8, collect)).isEmpty();

    PrintWriter w = new PrintWriter(VirtualFiles.newWriter(temp, Charsets.UTF_8));
    w.println("hello");
    w.println("");
    w.println(" world  ");
    w.println("");
    w.close();
    VirtualFiles.readLines(temp, Charsets.UTF_8, collect);
    assertThat(collect.getResult())
        .containsExactly("hello", "", " world  ", "").inOrder();

    LineProcessor<List<String>> collectNonEmptyLines =
        new LineProcessor<List<String>>() {
          List<String> collector = new ArrayList<String>();

          @Override
          public boolean processLine(String line) {
            if (line.length() > 0) {
              collector.add(line);
            }
            return true;
          }

          @Override
          public List<String> getResult() {
            return collector;
          }
        };
    VirtualFiles.readLines(temp, Charsets.UTF_8, collectNonEmptyLines);
    assertThat(collectNonEmptyLines.getResult()).containsExactly(
        "hello", " world  ").inOrder();

    assertTrue(VirtualFiles.delete(temp));
  }

  public void testHash() throws IOException {
    String asciiFile = getTestFile("ascii.txt");
    String i18nFile = getTestFile("i18n.txt");

    String init = "d41d8cd98f00b204e9800998ecf8427e";
    assertEquals(init, Hashing.md5().newHasher().hash().toString());

    String asciiHash = "e5df5a39f2b8cb71b24e1d8038f93131";
    assertEquals(asciiHash, VirtualFiles.hash(asciiFile, Hashing.md5()).toString());

    String i18nHash = "7fa826962ce2079c8334cd4ebf33aea4";
    assertEquals(i18nHash, VirtualFiles.hash(i18nFile, Hashing.md5()).toString());
  }

  public void testMap() throws IOException {
    // Test data
    int size = 1024;
    byte[] bytes = newPreFilledByteArray(size);

    // Setup
    String file = createTempFile();
    VirtualFiles.write(bytes, file);

    // Test
    MappedByteBuffer actual = VirtualFiles.map(file);

    // Verify
    ByteBuffer expected = ByteBuffer.wrap(bytes);
    assertTrue("ByteBuffers should be equal.", expected.equals(actual));
  }

  public void testMap_noSuchFile() throws IOException {
    // Setup
    String file = createTempFile();
    boolean deleted = VirtualFiles.delete(file);
    assertTrue(deleted);

    // Test
    try {
      VirtualFiles.map(file);
      fail("Should have thrown FileNotFoundException.");
    } catch (FileNotFoundException expected) {
    }
  }

  public void testMap_readWrite() throws IOException {
    // Test data
    int size = 1024;
    byte[] expectedBytes = new byte[size];
    byte[] bytes = newPreFilledByteArray(1024);

    // Setup
    String file = createTempFile();
    VirtualFiles.write(bytes, file);

    Random random = new Random();
    random.nextBytes(expectedBytes);

    // Test
    MappedByteBuffer map = VirtualFiles.map(file, MapMode.READ_WRITE);
    map.put(expectedBytes);

    // Verify
    byte[] actualBytes = VirtualFiles.toByteArray(file);
    assertTrue(Arrays.equals(expectedBytes, actualBytes));
  }

  public void testMap_readWrite_creates() throws IOException {
    // Test data
    int size = 1024;
    byte[] expectedBytes = newPreFilledByteArray(1024);

    // Setup
    String file = createTempFile();
    boolean deleted = VirtualFiles.delete(file);
    assertTrue(deleted);
    assertFalse(VirtualFiles.exists(file));

    // Test
    MappedByteBuffer map = VirtualFiles.map(file, MapMode.READ_WRITE, size);
    map.put(expectedBytes);

    // Verify
    assertTrue(VirtualFiles.exists(file));
//    assertTrue(file.isFile());
    assertEquals(size, VirtualFiles.length(file));
    byte[] actualBytes = VirtualFiles.toByteArray(file);
    assertTrue(Arrays.equals(expectedBytes, actualBytes));
  }

  public void testMap_readWrite_max_value_plus_1() throws IOException {
    // Setup
    String file = createTempFile();
    // Test
    try {
      VirtualFiles.map(file, MapMode.READ_WRITE, (long) Integer.MAX_VALUE + 1);
      fail("Should throw when size exceeds Integer.MAX_VALUE");
    } catch (IllegalArgumentException expected) {
    }
  }

  public void testGetFileExtension() {
    assertEquals("txt", VirtualFiles.getFileExtension(".txt"));
    assertEquals("txt", VirtualFiles.getFileExtension("blah.txt"));
    assertEquals("txt", VirtualFiles.getFileExtension("blah..txt"));
    assertEquals("txt", VirtualFiles.getFileExtension(".blah.txt"));
    assertEquals("txt", VirtualFiles.getFileExtension("/tmp/blah.txt"));
    assertEquals("gz", VirtualFiles.getFileExtension("blah.tar.gz"));
    assertEquals("", VirtualFiles.getFileExtension("/"));
    assertEquals("", VirtualFiles.getFileExtension("."));
    assertEquals("", VirtualFiles.getFileExtension(".."));
    assertEquals("", VirtualFiles.getFileExtension("..."));
    assertEquals("", VirtualFiles.getFileExtension("blah"));
    assertEquals("", VirtualFiles.getFileExtension("blah."));
    assertEquals("", VirtualFiles.getFileExtension(".blah."));
    assertEquals("", VirtualFiles.getFileExtension("/foo.bar/blah"));
    assertEquals("", VirtualFiles.getFileExtension("/foo/.bar/blah"));
  }

  public void testGetNameWithoutExtension() {
    assertEquals("", VirtualFiles.getNameWithoutExtension(".txt"));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("blah.txt"));
    assertEquals("blah.", VirtualFiles.getNameWithoutExtension("blah..txt"));
    assertEquals(".blah", VirtualFiles.getNameWithoutExtension(".blah.txt"));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("/tmp/blah.txt"));
    assertEquals("blah.tar", VirtualFiles.getNameWithoutExtension("blah.tar.gz"));
    assertEquals("", VirtualFiles.getNameWithoutExtension("/"));
    assertEquals("", VirtualFiles.getNameWithoutExtension("."));
    assertEquals(".", VirtualFiles.getNameWithoutExtension(".."));
    assertEquals("..", VirtualFiles.getNameWithoutExtension("..."));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("blah"));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("blah."));
    assertEquals(".blah", VirtualFiles.getNameWithoutExtension(".blah."));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("/foo.bar/blah"));
    assertEquals("blah", VirtualFiles.getNameWithoutExtension("/foo/.bar/blah"));
  }

  public void testReadBytes() throws IOException {
    ByteProcessor<byte[]> processor = new ByteProcessor<byte[]>() {
      private final ByteArrayOutputStream out = new ByteArrayOutputStream();

      @Override
      public boolean processBytes(byte[] buffer, int offset, int length) throws IOException {
        if (length >= 0) {
          out.write(buffer, offset, length);
        }
        return true;
      }

      @Override
      public byte[] getResult() {
        return out.toByteArray();
      }
    };

    String asciiFile = getTestFile("ascii.txt");
    byte[] result = VirtualFiles.readBytes(asciiFile, processor);
    assertEquals(Bytes.asList(VirtualFiles.toByteArray(asciiFile)), Bytes.asList(result));
  }

  public void testReadBytes_returnFalse() throws IOException {
    ByteProcessor<byte[]> processor = new ByteProcessor<byte[]>() {
      private final ByteArrayOutputStream out = new ByteArrayOutputStream();

      @Override
      public boolean processBytes(byte[] buffer, int offset, int length) throws IOException {
        if (length > 0) {
          out.write(buffer, offset, 1);
          return false;
        } else {
          return true;
        }
      }

      @Override
      public byte[] getResult() {
        return out.toByteArray();
      }
    };

    String asciiFile = getTestFile("ascii.txt");
    byte[] result = VirtualFiles.readBytes(asciiFile, processor);
    assertEquals(1, result.length);
  }

  /**
   * Returns a root path for the file system.
   */
  private static File root() {
    return File.listRoots()[0];
  }

  /**
   * Returns a {@code File} object for the given path parts.
   */
  private static File file(String first, String... more) {
    return file(new File(first), more);
  }

  /**
   * Returns a {@code File} object for the given path parts.
   */
  private static File file(File first, String... more) {
    // not very efficient, but should definitely be correct
    File file = first;
    for (String name : more) {
      file = new File(file, name);
    }
    return file;
  }

  public void testRelativeFile() throws Exception {
    assertFalse(VirtualFiles.exists("a/relative/file"));
  }
}
