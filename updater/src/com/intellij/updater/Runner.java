package com.intellij.updater;

import javax.swing.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import org.apache.log4j.FileAppender;
import org.apache.log4j.PatternLayout;

public class Runner {
  public static Logger logger = null;
  private static final String PATCH_FILE_NAME = "patch-file.zip";
  private static final String PATCH_PROPERTIES_ENTRY = "patch.properties";
  private static final String OLD_BUILD_DESCRIPTION = "old.build.description";
  private static final String NEW_BUILD_DESCRIPTION = "new.build.description";

  public static void main(String[] args) throws Exception {
    initLogger();
    logger.info("--- Updater started ---");

    if (args.length != 2 && args.length < 6) {
      printUsage();
      return;
    }

    String command = args[0];
    logger.info("args[0]: " + args[0]);

    if ("create".equals(command)) {
      if (args.length < 6) {
        printUsage();
        return;
      }
      String oldVersionDesc = args[1];
      String newVersionDesc = args[2];
      String oldFolder = args[3];
      String newFolder = args[4];
      String patchFile = args[5];
      List<String> ignoredFiles = extractFiles(args, "ignored");
      List<String> criticalFiles = extractFiles(args, "critical");
      List<String> optionalFiles = extractFiles(args, "optional");
      create(oldVersionDesc, newVersionDesc, oldFolder, newFolder, patchFile, ignoredFiles, criticalFiles, optionalFiles);
    }
    else if ("install".equals(command)) {
      if (args.length != 2) {
        printUsage();
        return;
      }

      String destFolder = args[1];
      logger.info("destFolder: " + destFolder);
      install(destFolder);
    }
    else {
      printUsage();
      return;
    }
  }

  public final static void initLogger(){
    if (logger == null){
    String tmpDir = System.getProperty("java.io.tmpdir");
    System.out.println("java.io.tmpdir: " + tmpDir);
    //    String uHome = System.getProperty("user.home");
    FileAppender update = new FileAppender();

    update.setFile(tmpDir + "idea_updater.log");
    update.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    update.setThreshold(Level.ALL);
    update.setAppend(true);
    update.activateOptions();

    FileAppender update_error = new FileAppender();
    update_error.setFile(tmpDir + "idea_updater_error.log");
    update_error.setLayout(new PatternLayout("%d{dd MMM yyyy HH:mm:ss} %-5p %C{1}.%M - %m%n"));
    update_error.setThreshold(Level.ERROR);
    // The error(s) from an old run of the updater (if there were) could be found in idea_updater.log file
    update_error.setAppend(false);
    update_error.activateOptions();

    logger = Logger.getLogger("com.intellij.updater");
    logger.addAppender(update_error);
    logger.addAppender(update);
    logger.setLevel(Level.ALL);
    }
  }

  public static void printStackTrace(Exception e){
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    logger.error(sw.toString());
  }

  public static void printStackTrace(Throwable e){
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    logger.error(sw.toString());
  }

  public static List<String> extractFiles(String[] args, String paramName) {
    List<String> result = new ArrayList<String>();
    for (String param : args) {
      if (param.startsWith(paramName + "=")) {
        param = param.substring((paramName + "=").length());
        for (StringTokenizer tokenizer = new StringTokenizer(param, ";"); tokenizer.hasMoreTokens();) {
          String each = tokenizer.nextToken();
          result.add(each);
        }
      }
    }
    return result;
  }

  private static void printUsage() {
    System.err.println("Usage:\n" +
                       "create <old_version_description> <new_version_description> <old_version_folder> <new_version_folder>" +
                       " <patch_file_name> [ignored=file1;file2;...] [critical=file1;file2;...] [optional=file1;file2;...]\n" +
                       "install <destination_folder>\n");
  }

  private static void create(String oldBuildDesc,
                             String newBuildDesc,
                             String oldFolder,
                             String newFolder,
                             String patchFile,
                             List<String> ignoredFiles,
                             List<String> criticalFiles,
                             List<String> optionalFiles) throws IOException, OperationCancelledException {
    UpdaterUI ui = new ConsoleUpdaterUI();
    try {
      File tempPatchFile = Utils.createTempFile();
      PatchFileCreator.create(new File(oldFolder),
                              new File(newFolder),
                              tempPatchFile,
                              ignoredFiles,
                              criticalFiles,
                              optionalFiles,
                              ui);

      logger.info("Packing jar file: " + patchFile );
      ui.startProcess("Packing jar file '" + patchFile + "'...");

      FileOutputStream fileOut = new FileOutputStream(patchFile);
      try {
        ZipOutputWrapper out = new ZipOutputWrapper(fileOut);
        ZipInputStream in = new ZipInputStream(new FileInputStream(resolveJarFile()));
        try {
          ZipEntry e;
          while ((e = in.getNextEntry()) != null) {
            out.zipEntry(e, in);
          }
        }
        finally {
          in.close();
        }

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try {
          Properties props = new Properties();
          props.setProperty(OLD_BUILD_DESCRIPTION, oldBuildDesc);
          props.setProperty(NEW_BUILD_DESCRIPTION, newBuildDesc);
          props.store(byteOut, "");
        }
        finally {
          byteOut.close();
        }

        out.zipBytes(PATCH_PROPERTIES_ENTRY, byteOut);
        out.zipFile(PATCH_FILE_NAME, tempPatchFile);
        out.finish();
      }
      finally {
        fileOut.close();
      }
    }
    finally {
      cleanup(ui);
    }
  }

  private static void cleanup(UpdaterUI ui) throws IOException {
    logger.info("Cleaning up...");
    ui.startProcess("Cleaning up...");
    ui.setProgressIndeterminate();
    Utils.cleanup();
  }

  private static void install(final String destFolder) throws Exception {
    InputStream in = Runner.class.getResourceAsStream("/" + PATCH_PROPERTIES_ENTRY);
    Properties props = new Properties();
    try {
      props.load(in);
    }
    finally {
      in.close();
    }

    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception ignore) {
          printStackTrace(ignore);
        }
      }
    });

    new SwingUpdaterUI(props.getProperty(OLD_BUILD_DESCRIPTION),
                  props.getProperty(NEW_BUILD_DESCRIPTION),
                  new SwingUpdaterUI.InstallOperation() {
                    public boolean execute(UpdaterUI ui) throws OperationCancelledException {
                      logger.info("installing patch to the " + destFolder);
                      return doInstall(ui, destFolder);
                    }
                  });
  }

  private static boolean doInstall(UpdaterUI ui, String destFolder) throws OperationCancelledException {
    try {
      try {
        File patchFile = Utils.createTempFile();
        ZipFile jarFile = new ZipFile(resolveJarFile());

        logger.info("Extracting patch file...");
        ui.startProcess("Extracting patch file...");
        ui.setProgressIndeterminate();
        try {
          InputStream in = Utils.getEntryInputStream(jarFile, PATCH_FILE_NAME);
          OutputStream out = new BufferedOutputStream(new FileOutputStream(patchFile));
          try {
            Utils.copyStream(in, out);
          }
          finally {
            in.close();
            out.close();
          }
        }
        finally {
          jarFile.close();
        }

        ui.checkCancelled();

        File destDir = new File(destFolder);
        PatchFileCreator.PreparationResult result = PatchFileCreator.prepareAndValidate(patchFile, destDir, ui);
        Map<String, ValidationResult.Option> options = ui.askUser(result.validationResults);
        return PatchFileCreator.apply(result, options, ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }
    finally {
      try {
        cleanup(ui);
      }
      catch (IOException e) {
        ui.showError(e);
        printStackTrace(e);
      }
    }

    return false;
  }

  private static File resolveJarFile() throws IOException {
    URL url = Runner.class.getResource("");
    if (url == null) throw new IOException("Cannot resolve jar file path");
    if (!"jar".equals(url.getProtocol())) throw new IOException("Patch file is not a 'jar' file");

    String path = url.getPath();

    int start = path.indexOf("file:/");
    int end = path.indexOf("!/");
    if (start == -1 || end == -1) throw new IOException("Unknown protocol: " + url);

    String jarFileUrl = path.substring(start, end);

    try {
      return new File(new URI(jarFileUrl));
    }
    catch (URISyntaxException e) {
      printStackTrace(e);
      throw new IOException(e.getMessage());
    }
  }
}
