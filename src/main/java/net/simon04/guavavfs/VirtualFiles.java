/*
 * Copyright (C) 2015 Simon Legner
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.FileWriteMode.APPEND;

import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.TreeTraverser;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSink;
import com.google.common.io.CharSource;
import com.google.common.io.Closer;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.FileType;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * Provides utility methods for working with files.
 * <p>
 * <p>All method parameters must be non-null unless documented otherwise.
 *
 * @author Simon Legner
 */
public final class VirtualFiles {

  private VirtualFiles() {
  }

  static final FileSystemOptions FILE_SYSTEM_OPTIONS = new FileSystemOptions();
  static {
    SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(FILE_SYSTEM_OPTIONS, false);
    try {
      if (VFS.getManager() instanceof DefaultFileSystemManager) {
        ((DefaultFileSystemManager) VFS.getManager()).setBaseFile(new File("").getAbsoluteFile());
      }
    } catch (FileSystemException ex) {
      LogFactory.getLog(VirtualFiles.class).warn("Failed to set base file");
    }
  }

  /**
   * Returns the {@link FileSystemOptions} object used for all operations
   * (especially {@link FileSystemManager#resolveFile(java.lang.String, org.apache.commons.vfs2.FileSystemOptions)}).
   *
   * This allows to customize the options using methods of {@link FileSystemConfigBuilder} subclasses.
   */
  public static FileSystemOptions getFileSystemOptions() {
    return FILE_SYSTEM_OPTIONS;
  }

  static FileObject resolveFile(String file) throws FileSystemException {
    return VFS.getManager().resolveFile(file, FILE_SYSTEM_OPTIONS);
  }

  /**
   * Returns a buffered reader that reads from a file using the given
   * character set.
   *
   * @param file    the file to read from
   * @param charset the charset used to decode the input stream; see {@link
   *                Charsets} for helpful predefined constants
   * @return the buffered reader
   */
  public static BufferedReader newReader(String file, Charset charset)
          throws IOException {
    checkNotNull(file);
    checkNotNull(charset);
    final InputStream in = resolveFile(file).getContent().getInputStream();
    return new BufferedReader(
            new InputStreamReader(in, charset));
  }

  /**
   * Returns a buffered writer that writes to a file using the given
   * character set.
   *
   * @param file    the file to write to
   * @param charset the charset used to encode the output stream; see {@link
   *                Charsets} for helpful predefined constants
   * @return the buffered writer
   */
  public static BufferedWriter newWriter(String file, Charset charset)
          throws IOException {
    checkNotNull(file);
    checkNotNull(charset);
    final OutputStream out = resolveFile(file).getContent().getOutputStream();
    return new BufferedWriter(
            new OutputStreamWriter(out, charset));
  }

  /**
   * Returns a new {@link ByteSource} for reading bytes from the given file.
   *
   */
  public static ByteSource asByteSource(String file) {
    return new VirtualFileByteSource(file);
  }

  private static final class VirtualFileByteSource extends ByteSource {

    private final String filename;

    private VirtualFileByteSource(String filename) {
      this.filename = checkNotNull(filename);
    }

    @Override
    public InputStream openStream() throws IOException {
      final FileContent content = resolveFile(filename).getContent();
      return content.getInputStream();
    }

    @Override
    public String toString() {
      return "VirtualFiles.asByteSource(" + filename + ")";
    }
  }

  /**
   * Reads a file of the given expected size from the given input stream, if
   * it will fit into a byte array. This method handles the case where the file
   * size changes between when the size is read and when the contents are read
   * from the stream.
   */
  static byte[] readFile(
          InputStream in, long expectedSize) throws IOException {
    if (expectedSize > Integer.MAX_VALUE) {
      throw new OutOfMemoryError("file is too large to fit in a byte array: "
              + expectedSize + " bytes");
    }

    return ByteStreams.toByteArray(in);
  }

  /**
   * Returns a new {@link ByteSink} for writing bytes to the given file. The
   * given {@code modes} control how the file is opened for writing. When no
   * mode is provided, the file will be truncated before writing. When the
   * {@link FileWriteMode#APPEND APPEND} mode is provided, writes will
   * append to the end of the file without truncating it.
   *
   */
  public static ByteSink asByteSink(String file, FileWriteMode... modes) {
    return new VirtualFileByteSink(file, modes);
  }

  private static final class VirtualFileByteSink extends ByteSink {

    private final String filename;
    private final ImmutableSet<FileWriteMode> modes;

    private VirtualFileByteSink(String filename, FileWriteMode... modes) {
      this.filename = checkNotNull(filename);
      this.modes = ImmutableSet.copyOf(modes);
    }

    @Override
    public OutputStream openStream() throws IOException {
      final FileContent content = resolveFile(filename).getContent();
      return content.getOutputStream(modes.contains(APPEND));
    }
  }

  /**
   * Returns a new {@link CharSource} for reading character data from the given
   * file using the given character set.
   *
   */
  public static CharSource asCharSource(String file, Charset charset) {
    return asByteSource(file).asCharSource(charset);
  }

  /**
   * Returns a new {@link CharSink} for writing character data to the given
   * file using the given character set. The given {@code modes} control how
   * the file is opened for writing. When no mode is provided, the file
   * will be truncated before writing. When the
   * {@link FileWriteMode#APPEND APPEND} mode is provided, writes will
   * append to the end of the file without truncating it.
   *
   */
  public static CharSink asCharSink(String file, Charset charset,
                                    FileWriteMode... modes) {
    return asByteSink(file, modes).asCharSink(charset);
  }

  private static FileWriteMode[] modes(boolean append) {
    return append
            ? new FileWriteMode[]{FileWriteMode.APPEND}
            : new FileWriteMode[0];
  }

  /**
   * Reads all bytes from a file into a byte array.
   *
   * @param file the file to read from
   * @return a byte array containing all the bytes from file
   * @throws IllegalArgumentException if the file is bigger than the largest
   *                                  possible byte array (2^31 - 1)
   * @throws IOException              if an I/O error occurs
   */
  public static byte[] toByteArray(String file) throws IOException {
    return asByteSource(file).read();
  }

  /**
   * Reads all characters from a file into a {@link String}, using the given
   * character set.
   *
   * @param file    the file to read from
   * @param charset the charset used to decode the input stream; see {@link
   *                Charsets} for helpful predefined constants
   * @return a string containing all the characters from the file
   * @throws IOException if an I/O error occurs
   */
  public static String toString(String file, Charset charset) throws IOException {
    return asCharSource(file, charset).read();
  }

  /**
   * Overwrites a file with the contents of a byte array.
   *
   * @param from the bytes to write
   * @param to   the destination file
   * @throws IOException if an I/O error occurs
   */
  public static void write(byte[] from, String to) throws IOException {
    asByteSink(to).write(from);
  }

  /**
   * Copies all bytes from a file to an output stream.
   *
   * @param from the source file
   * @param to   the output stream
   * @throws IOException if an I/O error occurs
   */
  public static void copy(String from, OutputStream to) throws IOException {
    asByteSource(from).copyTo(to);
  }

  /**
   * Copies all the bytes from one file to another.
   * <p>
   * <p><b>Warning:</b> If {@code to} represents an existing file, that file
   * will be overwritten with the contents of {@code from}. If {@code to} and
   * {@code from} refer to the <i>same</i> file, the contents of that file
   * will be deleted.
   *
   * @param from the source file
   * @param to   the destination file
   * @throws IOException              if an I/O error occurs
   * @throws IllegalArgumentException if {@code from.equals(to)}
   */
  public static void copy(String from, String to) throws IOException {
    checkArgument(!from.equals(to),
            "Source %s and destination %s must be different", from, to);
    asByteSource(from).copyTo(asByteSink(to));
  }

  /**
   * Writes a character sequence (such as a string) to a file using the given
   * character set.
   *
   * @param from    the character sequence to write
   * @param to      the destination file
   * @param charset the charset used to encode the output stream; see {@link
   *                Charsets} for helpful predefined constants
   * @throws IOException if an I/O error occurs
   */
  public static void write(CharSequence from, String to, Charset charset)
          throws IOException {
    asCharSink(to, charset).write(from);
  }

  /**
   * Appends a character sequence (such as a string) to a file using the given
   * character set.
   *
   * @param from    the character sequence to append
   * @param to      the destination file
   * @param charset the charset used to encode the output stream; see {@link
   *                Charsets} for helpful predefined constants
   * @throws IOException if an I/O error occurs
   */
  public static void append(CharSequence from, String to, Charset charset)
          throws IOException {
    write(from, to, charset, true);
  }

  /**
   * Private helper method. Writes a character sequence to a file,
   * optionally appending.
   *
   * @param from    the character sequence to append
   * @param to      the destination file
   * @param charset the charset used to encode the output stream; see {@link
   *                Charsets} for helpful predefined constants
   * @param append  true to append, false to overwrite
   * @throws IOException if an I/O error occurs
   */
  private static void write(CharSequence from, String to, Charset charset,
                            boolean append) throws IOException {
    asCharSink(to, charset, modes(append)).write(from);
  }

  /**
   * Copies all characters from a file to an appendable object,
   * using the given character set.
   *
   * @param from    the source file
   * @param charset the charset used to decode the input stream; see {@link
   *                Charsets} for helpful predefined constants
   * @param to      the appendable object
   * @throws IOException if an I/O error occurs
   */
  public static void copy(String from, Charset charset, Appendable to)
          throws IOException {
    asCharSource(from, charset).copyTo(to);
  }

  /**
   * Returns true if the files contains the same bytes.
   *
   * @throws IOException if an I/O error occurs
   */
  public static boolean equal(String file1, String file2) throws IOException {
    checkNotNull(file1);
    checkNotNull(file2);
    final FileSystemManager vfs = VFS.getManager();
    if (Objects.equals(file1, file2) || vfs.resolveFile(file1).equals(vfs.resolveFile(file2))) {
      return true;
    }

    /*
     * Some operating systems may return zero as the length for files
     * denoting system-dependent entities such as devices or pipes, in
     * which case we must fall back on comparing the bytes directly.
     */
    long len1 = length(file1);
    long len2 = length(file2);
    if (len1 != 0 && len2 != 0 && len1 != len2) {
      return false;
    }
    return asByteSource(file1).contentEquals(asByteSource(file2));
  }

  /**
   * Atomically creates a new directory somewhere beneath the system's
   * temporary directory (as defined by the {@code java.io.tmpdir} system
   * property), and returns its name.
   * <p>
   * <p>Use this method instead of {@link File#createTempFile(String, String)}
   * when you wish to create a directory, not a regular file.  A common pitfall
   * is to call {@code createTempFile}, delete the file and create a
   * directory in its place, but this leads a race condition which can be
   * exploited to create security vulnerabilities, especially when executable
   * files are to be written into the directory.
   * <p>
   * <p>This method assumes that the temporary volume is writable, has free
   * inodes and free blocks, and that it will not be called thousands of times
   * per second.
   *
   * @return the newly-created directory
   * @throws IllegalStateException if the directory could not be created
   */
  public static File createTempDir() {
    return Files.createTempDir();
  }

  /**
   * Creates an empty file or updates the last updated timestamp on the
   * same as the unix command of the same name.
   *
   * @param file the file to create or update
   * @throws IOException if an I/O error occurs
   */
  public static void touch(String file) throws IOException {
    checkNotNull(file);
    resolveFile(file).createFile();
  }

  /**
   * Creates any necessary but nonexistent parent directories of the specified
   * file. Note that if this operation fails it may have succeeded in creating
   * some (but not all) of the necessary parent directories.
   *
   * @throws IOException if an I/O error occurs, or if any necessary but
   *                     nonexistent parent directories of the specified file could not be
   *                     created.
   */
  public static void createParentDirs(String file) throws IOException {
    checkNotNull(file);
    resolveFile(file).getParent().createFolder();
    if (resolveFile(file).getParent().getType() != FileType.FOLDER) {
      throw new IOException("Unable to create parent directories of " + file);
    }
  }

  /**
   * Moves a file from one path to another. This method can rename a file
   * and/or move it to a different directory. In either case {@code to} must
   * be the target path for the file itself; not just the new name for the
   * file or the path to the new parent directory.
   *
   * @param from the source file
   * @param to   the destination file
   * @throws IOException              if an I/O error occurs
   * @throws IllegalArgumentException if {@code from.equals(to)}
   */
  public static void move(String from, String to) throws IOException {
    checkNotNull(from);
    checkNotNull(to);
    checkArgument(!from.equals(to),
            "Source %s and destination %s must be different", from, to);
    resolveFile(from).moveTo(resolveFile(to));
  }

  /**
   * Reads the first line from a file. The line does not include
   * line-termination characters, but does include other leading and
   * trailing whitespace.
   *
   * @param file    the file to read from
   * @param charset the charset used to decode the input stream; see {@link
   *                Charsets} for helpful predefined constants
   * @return the first line, or null if the file is empty
   * @throws IOException if an I/O error occurs
   */
  public static String readFirstLine(String file, Charset charset)
          throws IOException {
    return asCharSource(file, charset).readFirstLine();
  }

  /**
   * Reads all of the lines from a file. The lines do not include
   * line-termination characters, but do include other leading and
   * trailing whitespace.
   * <p>
   * <p>This method returns a mutable {@code List}. For an
   * {@code ImmutableList}, use
   * {@code Files.asCharSource(file, charset).readLines()}.
   *
   * @param file    the file to read from
   * @param charset the charset used to decode the input stream; see {@link
   *                Charsets} for helpful predefined constants
   * @return a mutable {@link List} containing all the lines
   * @throws IOException if an I/O error occurs
   */
  public static List<String> readLines(String file, Charset charset)
          throws IOException {
    // don't use asCharSource(file, charset).readLines() because that returns
    // an immutable list, which would change the behavior of this method
    return readLines(file, charset, new LineProcessor<List<String>>() {
      final List<String> result = Lists.newArrayList();

      @Override
      public boolean processLine(String line) {
        result.add(line);
        return true;
      }

      @Override
      public List<String> getResult() {
        return result;
      }
    });
  }

  /**
   * Streams lines from a {@link File}, stopping when our callback returns
   * false, or we have read all of the lines.
   *
   * @param file     the file to read from
   * @param charset  the charset used to decode the input stream; see {@link
   *                 Charsets} for helpful predefined constants
   * @param callback the {@link LineProcessor} to use to handle the lines
   * @return the output of processing the lines
   * @throws IOException if an I/O error occurs
   */
  public static <T> T readLines(String file, Charset charset,
                                LineProcessor<T> callback) throws IOException {
    return asCharSource(file, charset).readLines(callback);
  }

  /**
   * Process the bytes of a file.
   * <p>
   * <p>(If this seems too complicated, maybe you're looking for
   * {@link #toByteArray}.)
   *
   * @param file      the file to read
   * @param processor the object to which the bytes of the file are passed.
   * @return the result of the byte processor
   * @throws IOException if an I/O error occurs
   */
  public static <T> T readBytes(String file, ByteProcessor<T> processor)
          throws IOException {
    return asByteSource(file).read(processor);
  }

  /**
   * Computes the hash code of the {@code file} using {@code hashFunction}.
   *
   * @param file         the file to read
   * @param hashFunction the hash function to use to hash the data
   * @return the {@link HashCode} of all of the bytes in the file
   * @throws IOException if an I/O error occurs
   */
  public static HashCode hash(String file, HashFunction hashFunction)
          throws IOException {
    return asByteSource(file).hash(hashFunction);
  }

  /**
   * Fully maps a file read-only in to memory as per
   * {@link FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}.
   * <p>
   * <p>Files are mapped from offset 0 to its length.
   * <p>
   * <p>This only works for files {@code <=} {@link Integer#MAX_VALUE} bytes.
   *
   * @param file the file to map
   * @return a read-only buffer reflecting {@code file}
   * @throws FileNotFoundException if the {@code file} does not exist
   * @throws IOException           if an I/O error occurs
   * @see FileChannel#map(MapMode, long, long)
   */
  public static MappedByteBuffer map(String file) throws IOException {
    checkNotNull(file);
    return map(file, MapMode.READ_ONLY);
  }

  /**
   * Fully maps a file in to memory as per
   * {@link FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}
   * using the requested {@link MapMode}.
   * <p>
   * <p>Files are mapped from offset 0 to its length.
   * <p>
   * <p>This only works for files {@code <=} {@link Integer#MAX_VALUE} bytes.
   *
   * @param file the file to map
   * @param mode the mode to use when mapping {@code file}
   * @return a buffer reflecting {@code file}
   * @throws FileNotFoundException if the {@code file} does not exist
   * @throws IOException           if an I/O error occurs
   * @see FileChannel#map(MapMode, long, long)
   */
  public static MappedByteBuffer map(String file, MapMode mode)
          throws IOException {
    checkNotNull(file);
    checkNotNull(mode);
    if (!exists(file)) {
      throw new FileNotFoundException(file);
    }
    return map(file, mode, length(file));
  }

  /**
   * Determines if this file exists.
   * @param file the file to test
   * @throws IOException if an I/O error occurs
   */
  public static boolean exists(String file) throws IOException {
    return resolveFile(file).exists();
  }

  /**
   * Deletes this file. Does nothing if this file does not exist of if it is a folder that has children.
   * @param file the file to delete
   * @return true if this object has been deleted
   * @throws IOException if an I/O error occurs
   */
  public static boolean delete(String file) throws IOException {
    return resolveFile(file).delete();
  }

  /**
   * Determines the size of the file, in bytes.
   * @param file the file to analyze
   * @throws IOException if an I/O error occurs
   */
  public static long length(String file) throws IOException {
    return resolveFile(file).getContent().getSize();
  }

  /**
   * Determines the last-modified timestamp of the file.
   * @param file the file to analyze
   * @throws IOException if an I/O error occurs
   */
  public static long getLastModified(String file) throws IOException {
    return resolveFile(file).getContent().getLastModifiedTime();
  }

  /**
   * Sets the last-modified timestamp of the file. Creates the file if it does not exist.
   * @param file the file to modify
   * @param modTime the time to set the last-modified timestamp to
   * @throws IOException if an I/O error occurs
   */
  public static void setLastModified(String file, long modTime) throws IOException {
    resolveFile(file).getContent().setLastModifiedTime(modTime);
  }

  /**
   * Maps a file in to memory as per
   * {@link FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long)}
   * using the requested {@link MapMode}.
   * <p>
   * <p>Files are mapped from offset 0 to {@code size}.
   * <p>
   * <p>If the mode is {@link MapMode#READ_WRITE} and the file does not exist,
   * it will be created with the requested {@code size}. Thus this method is
   * useful for creating memory mapped files which do not yet exist.
   * <p>
   * <p>This only works for files {@code <=} {@link Integer#MAX_VALUE} bytes.
   *
   * @param file the file to map
   * @param mode the mode to use when mapping {@code file}
   * @return a buffer reflecting {@code file}
   * @throws IOException if an I/O error occurs
   * @see FileChannel#map(MapMode, long, long)
   */
  public static MappedByteBuffer map(String file, MapMode mode, long size)
          throws FileNotFoundException, IOException {
    checkNotNull(file);
    checkNotNull(mode);

    Closer closer = Closer.create();
    try {
      RandomAccessFile raf = closer.register(
              new RandomAccessFile(file, mode == MapMode.READ_ONLY ? "r" : "rw"));
      return map(raf, mode, size);
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  private static MappedByteBuffer map(RandomAccessFile raf, MapMode mode,
                                      long size) throws IOException {
    Closer closer = Closer.create();
    try {
      FileChannel channel = closer.register(raf.getChannel());
      return channel.map(mode, 0, size);
    } catch (Throwable e) {
      throw closer.rethrow(e);
    } finally {
      closer.close();
    }
  }

  /**
   * Returns the lexically cleaned form of the path name, <i>usually</i> (but
   * not always) equivalent to the original. The following heuristics are used:
   * <p>
   * <ul>
   * <li>empty string becomes .
   * <li>. stays as .
   * <li>fold out ./
   * <li>fold out ../ when possible
   * <li>collapse multiple slashes
   * <li>delete trailing slashes (unless the path is just "/")
   * </ul>
   * <p>
   * <p>These heuristics do not always match the behavior of the filesystem. In
   * particular, consider the path {@code a/../b}, which {@code simplifyPath}
   * will change to {@code b}. If {@code a} is a symlink to {@code x}, {@code
   * a/../b} may refer to a sibling of {@code x}, rather than the sibling of
   * {@code a} referred to by {@code b}.
   *
   */
  public static String simplifyPath(String pathname) {
    return Files.simplifyPath(pathname);
  }

  /**
   * Returns the <a href="http://en.wikipedia.org/wiki/Filename_extension">file
   * extension</a> for the given file name, or the empty string if the file has
   * no extension.  The result does not include the '{@code .}'.
   *
   */
  public static String getFileExtension(String fullName) {
    checkNotNull(fullName);
    String fileName = new File(fullName).getName();
    int dotIndex = fileName.lastIndexOf('.');
    return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
  }

  /**
   * Returns the file name without its
   * <a href="http://en.wikipedia.org/wiki/Filename_extension">file extension</a> or path. This is
   * similar to the {@code basename} unix command. The result does not include the '{@code .}'.
   *
   * @param file The name of the file to trim the extension from. This can be either a fully
   *             qualified file name (including a path) or just a file name.
   * @return The file name without its path or extension.
   */
  public static String getNameWithoutExtension(String file) {
    checkNotNull(file);
    String fileName = new File(file).getName();
    int dotIndex = fileName.lastIndexOf('.');
    return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
  }

  /**
   * Returns a {@link TreeTraverser} instance for {@link File} trees.
   * <p>
   * <p><b>Warning:</b> {@code File} provides no support for symbolic links, and as such there is no
   * way to ensure that a symbolic link to a directory is not followed when traversing the tree.
   * In this case, iterables created by this traverser could contain files that are outside of the
   * given directory or even be infinite if there is a symbolic link loop.
   *
   */
  public static TreeTraverser<File> fileTreeTraverser() {
    return Files.fileTreeTraverser();
  }

  /**
   * Returns a predicate that returns the result of {@link File#isDirectory} on input files.
   *
   */
  public static Predicate<File> isDirectory() {
    return Files.isDirectory();
  }

  /**
   * Returns a predicate that returns the result of {@link File#isFile} on input files.
   *
   */
  public static Predicate<File> isFile() {
    return Files.isFile();
  }
}
