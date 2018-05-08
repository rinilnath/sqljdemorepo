/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 1999 The Apache Software Foundation.  All rights 
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:  
 *       "This product includes software developed by the 
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "The Jakarta Project", "Tomcat", and "Apache Software
 *    Foundation" must not be used to endorse or promote products derived
 *    from this software without prior written permission. For written 
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache"
 *    nor may "Apache" appear in their names without prior written
 *    permission of the Apache Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package com.rinil.java;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.LogOutputStream;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Reference;

/**
 * Task to compile SQLj source files. This task can take the following
 * arguments:
 * <ul>
 * <li>srcdir
 * <li>destdir
 * <li>compile
 * <li>ser2class
 * <li>user
 * <li>url
 * </ul>
 * Of these arguments, the <b>srcdir</b> and <b>destdir</b> are required.
 * <p>
 * When this task executes, it will recursively scan the srcdir and dest destdir
 * looking for sqlj files to compile. This task make its compile decision based
 * on timestamp. Notice that the generated java files will be located at the
 * same directory as the sqlj files, Only the binary files will be placed at the
 * destdir directory.
 *
 * @auther Eli Sherman <a href="mailto:eli@aspear.com">eli@aspear.com</a>
 */

public class sqljTask extends MatchingTask {

	private boolean compile = false;
	private boolean ser2class = false;
	private File srcDir;
	private File destDir;
	private Path compileClasspath;
	private String user = null;
	private String url = null;
	private static String lSep = System.getProperty("line.separator");

	private static final int SQLJ_SUCCESS = 0;

	protected Vector compileList = new Vector();

	/**
	 * Set the url to the Oracle database. This will be used for the online code
	 * checking i.e tables, columns etc.
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * Set the connect string for the Oracle database. This will be used for
	 * online code checking i.e tables columns etc.
	 */
	public void setUser(String user) {
		this.user = user;
	}

	/**
	 * Set if sqlj should compile the generated java files to class file. The
	 * default is false, better javac task do this.
	 */
	public void setCompile(boolean compile) {
		this.compile = compile;
	}

	/**
	 * Set if sqlj should compile the ser files into class files. The default is
	 * false.
	 */
	public void setSer2class(boolean ser2class) {
		this.ser2class = ser2class;
	}

	/**
	 * Set the source dir to find the source sqlj files.
	 */
	public void setSrcdir(File srcDir) {
		this.srcDir = srcDir;
	}

	/**
	 * Set the destination of the binary files generated by the SQLj process.
	 * Notice that this directory will not contain the generated java files!
	 */
	public void setDestdir(File destDir) {
		this.destDir = destDir;
	}

	/**
	 * Set the classpath to be used for this compilation.
	 */
	public void setClasspath(Path classpath) {
		if (compileClasspath == null) {
			compileClasspath = classpath;
		} else {
			compileClasspath.append(classpath);
		}
	}

	/**
	 * Maybe creates a nested classpath element.
	 */
	public Path createClasspath() {
		if (compileClasspath == null) {
			compileClasspath = new Path(project);
		}
		return compileClasspath.createPath();
	}

	/**
	 * Adds a reference to a CLASSPATH defined elsewhere.
	 */
	public void setClasspathRef(Reference r) {
		createClasspath().setRefid(r);
	}

	/**
	 * Execute the task
	 */
	public void execute() throws BuildException {
		// make sure that we've got a srcdir
		if (srcDir != null && !srcDir.exists()) {
			throw new BuildException("source directory \"" + srcDir
					+ "\" does not exist!", location);
		}

		// make sure that that the destdir exsits.
		if (destDir != null && !destDir.isDirectory()) {
			throw new BuildException("destination directory \"" + destDir
					+ "\" does not exist!", location);
		}

		// scan source directories and dest directory to build up both copy
		// lists and
		// compile lists
		resetFileLists();
		DirectoryScanner ds = this.getDirectoryScanner(srcDir);

		String[] files = ds.getIncludedFiles();

		scanDir(srcDir, srcDir, files);

		if (compileList.size() > 0) {
			log("Compiling " + compileList.size() + " sqlj source file"
					+ (compileList.size() == 1 ? "" : "s")
					+ (destDir != null ? " to " + destDir : ""));
			sqljFiles();
		}
	}

	/**
	 * Clear the list of files to be compiled and copied..
	 */
	protected void resetFileLists() {
		compileList.removeAllElements();
	}

	/**
	 * Scans the directory looking for source files to be compiled. The results
	 * are returned in the class variable compileList
	 */
	protected void scanDir(File srcDir, File destDir, String files[]) {

		long now = (new Date()).getTime();

		for (int i = 0; i < files.length; i++) {
			File srcFile = new File(srcDir, files[i]);
			if (files[i].endsWith(".sqlj")) {
				File javaFile = new File(srcDir, files[i].substring(0,
						files[i].indexOf(".sqlj"))
						+ ".java");

				if (srcFile.lastModified() > now) {
					log("Warning: file modified in the future: " + files[i],
							Project.MSG_WARN);
				}

				// compare the sqlj file to java file
				if (!javaFile.exists()
						|| srcFile.lastModified() > javaFile.lastModified()) {
					if (!javaFile.exists()) {
						log("Compiling " + srcFile.getPath()
								+ " because java file " + javaFile.getPath()
								+ " does not exist", Project.MSG_DEBUG);
					} else {
						log("Compiling " + srcFile.getPath()
								+ " because it is out of date with respect to "
								+ javaFile.getPath(), Project.MSG_DEBUG);
					}
					compileList.addElement(srcFile.getAbsolutePath());
					continue;
				}
			}
		}
	}

	/**
	 * Run sqlj on the source files.
	 */
	private void sqljFiles() throws BuildException {
		try {
			Class.forName("sqlj.tools.Sqlj");
		} catch (ClassNotFoundException cnfe) {
			throw new BuildException("Cannot run sqlj, as it is not available",
					location);
		}

		Commandline cmd = setupSqljCommand();

		PrintStream err = System.err;
		PrintStream out = System.out;

		try {
			PrintStream logstr = new PrintStream(new LogOutputStream(this,
					Project.MSG_WARN));
			System.setOut(logstr);
			System.setErr(logstr);
			Class c = Class.forName("sqlj.tools.Sqlj");
			Method main = c.getMethod("statusMain",
					new Class[] { (new String[] {}).getClass() });
			int result = ((Integer) main.invoke(null,
					new Object[] { cmd.getArguments() })).intValue();
			if (result != SQLJ_SUCCESS) {
				throw new BuildException(
						"Translation failed, message should have been provided.",
						location);
			}
		} catch (Exception ex) {
			if (ex instanceof BuildException) {
				throw (BuildException) ex;
			} else {
				throw new BuildException("Error running sqlj translator", ex,
						location);
			}
		} finally {
			System.setErr(err);
			System.setOut(out);
		}
	}

	/**
	 * Command line argument processing.
	 */
	private Commandline setupSqljCommand() {
		Commandline cmd = new Commandline();
		Path classpath = getCompileClasspath(false);

		if (destDir != null) {
			cmd.createArgument().setValue("-d=" + destDir);
		}

		cmd.createArgument().setValue(
				"-C-classpath=" + classpath.toString() + File.pathSeparator
						+ srcDir.toString());

		if (compile == false) {
			cmd.createArgument().setValue("-compile=false");
		}

		if (ser2class == true) {
			cmd.createArgument().setValue("-ser2class");
		}

		if (user != null) {
			cmd.createArgument().setValue("-user=" + user);
		}

		if (url != null) {
			cmd.createArgument().setValue("-url=" + url);
		}

		logAndAddFilesToTranslate(cmd);

		return cmd;
	}

	/**
	 * Logs the compilation parameters, adds the files to translate and logs the
	 * &qout;niceSourceList&quot;
	 */
	protected void logAndAddFilesToTranslate(Commandline cmd) {
		log("Compilation args: " + cmd.toString(), Project.MSG_VERBOSE);

		StringBuffer niceSourceList = new StringBuffer("File");
		if (compileList.size() != 1) {
			niceSourceList.append("s");
		}
		niceSourceList.append(" to be compiled:");

		niceSourceList.append(lSep);

		Enumeration enumeration = compileList.elements();
		while (enumeration.hasMoreElements()) {
			String arg = (String) enumeration.nextElement();
			cmd.createArgument().setValue(arg);
			niceSourceList.append("    " + arg + lSep);
		}

		log(niceSourceList.toString(), Project.MSG_VERBOSE);
	}

	/**
	 * Builds the compilation classpath.
	 *
	 * @param addRuntime
	 *            Shall <code>rt.jar</code> or <code>classes.zip</code> be added
	 *            to the classpath.
	 */
	protected Path getCompileClasspath(boolean addRuntime) {
		Path classpath = new Path(project);

		// add dest dir to classpath so that previously compiled and
		// untouched classes are on classpath

		if (destDir != null) {
			classpath.setLocation(destDir);
		}

		// add our classpath to the mix

		if (compileClasspath != null) {
			classpath.addExisting(compileClasspath);
		}

		// add the system classpath

		classpath.addExisting(Path.systemClasspath);
		if (addRuntime) {
			if (System.getProperty("java.vendor").toLowerCase()
					.indexOf("microsoft") >= 0) {
				// Pull in *.zip from packages directory
				FileSet msZipFiles = new FileSet();
				msZipFiles.setDir(new File(System.getProperty("java.home")
						+ File.separator + "Packages"));
				msZipFiles.setIncludes("*.ZIP");
				classpath.addFileset(msZipFiles);
			} else if (Project.getJavaVersion() == Project.JAVA_1_1) {
				classpath.addExisting(new Path(null, System
						.getProperty("java.home")
						+ File.separator
						+ "lib"
						+ File.separator + "classes.zip"));
			} else {
				// JDK > 1.1 seems to set java.home to the JRE directory.
				classpath.addExisting(new Path(null, System
						.getProperty("java.home")
						+ File.separator
						+ "lib"
						+ File.separator + "rt.jar"));
				// Just keep the old version as well and let addExistingToPath
				// sort it out.
				classpath.addExisting(new Path(null, System
						.getProperty("java.home")
						+ File.separator
						+ "jre"
						+ File.separator + "lib" + File.separator + "rt.jar"));
			}
		}

		return classpath;
	}
}