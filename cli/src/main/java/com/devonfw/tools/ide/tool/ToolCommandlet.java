package com.devonfw.tools.ide.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.devonfw.tools.ide.cli.CliException;
import com.devonfw.tools.ide.commandlet.Commandlet;
import com.devonfw.tools.ide.common.Tag;
import com.devonfw.tools.ide.common.Tags;
import com.devonfw.tools.ide.context.IdeContext;
import com.devonfw.tools.ide.environment.EnvironmentVariables;
import com.devonfw.tools.ide.environment.EnvironmentVariablesType;
import com.devonfw.tools.ide.io.FileAccess;
import com.devonfw.tools.ide.io.TarCompression;
import com.devonfw.tools.ide.os.MacOsHelper;
import com.devonfw.tools.ide.process.ProcessContext;
import com.devonfw.tools.ide.process.ProcessErrorHandling;
import com.devonfw.tools.ide.property.StringListProperty;
import com.devonfw.tools.ide.util.FilenameUtil;
import com.devonfw.tools.ide.version.VersionIdentifier;

/**
 * {@link Commandlet} for a tool integrated into the IDE.
 */
public abstract class ToolCommandlet extends Commandlet implements Tags {

  /** @see #getName() */
  protected final String tool;

  private final Set<Tag> tags;

  /** The commandline arguments to pass to the tool. */
  public final StringListProperty arguments;

  private MacOsHelper macOsHelper;

  /**
   * The constructor.
   *
   * @param context the {@link IdeContext}.
   * @param tool the {@link #getName() tool name}.
   * @param tags the {@link #getTags() tags} classifying the tool. Should be created via {@link Set#of(Object) Set.of}
   *        method.
   */
  public ToolCommandlet(IdeContext context, String tool, Set<Tag> tags) {

    super(context);
    this.tool = tool;
    this.tags = tags;
    addKeyword(tool);
    this.arguments = add(new StringListProperty("", false, "args"));
  }

  /**
   * @return the name of the tool (e.g. "java", "mvn", "npm", "node").
   */
  @Override
  public String getName() {

    return this.tool;
  }

  /**
   * @return the name of the binary executable for this tool.
   */
  protected String getBinaryName() {

    return this.tool;
  }

  @Override
  public final Set<Tag> getTags() {

    return this.tags;
  }

  @Override
  public void run() {

    runTool(null, this.arguments.asArray());
  }

  /**
   * Ensures the tool is installed and then runs this tool with the given arguments.
   *
   * @param toolVersion the explicit version (pattern) to run. Typically {@code null} to ensure the configured version
   *        is installed and use that one. Otherwise the specified version will be installed in the software repository
   *        without touching and IDE installation and used to run.
   * @param args the commandline arguments to run the tool.
   */
  public void runTool(VersionIdentifier toolVersion, String... args) {

    Path binaryPath;
    Path toolPath = Paths.get(getBinaryName());
    if (toolVersion == null) {
      install(true);
      binaryPath = toolPath;
    } else {
      throw new UnsupportedOperationException("Not yet implemented!");
    }
    ProcessContext pc = this.context.newProcess().errorHandling(ProcessErrorHandling.WARNING).executable(binaryPath)
        .addArgs(args);
    pc.run();
  }

  /**
   * @return the {@link EnvironmentVariables#getToolEdition(String) tool edition}.
   */
  protected String getEdition() {

    return this.context.getVariables().getToolEdition(getName());
  }

  /**
   * @return the {@link #getName() tool} with its {@link #getEdition() edition}. The edition will be omitted if same as
   *         tool.
   * @see #getToolWithEdition(String, String)
   */
  protected final String getToolWithEdition() {

    return getToolWithEdition(getName(), getEdition());
  }

  /**
   * @param tool the tool name.
   * @param edition the edition.
   * @return the {@link #getName() tool} with its {@link #getEdition() edition}. The edition will be omitted if same as
   *         tool.
   */
  protected final static String getToolWithEdition(String tool, String edition) {

    if (tool.equals(edition)) {
      return tool;
    }
    return tool + "/" + edition;
  }

  /**
   * @return the {@link EnvironmentVariables#getToolVersion(String) tool version}.
   */
  public VersionIdentifier getConfiguredVersion() {

    return this.context.getVariables().getToolVersion(getName());
  }

  /**
   * Method to be called for {@link #install(boolean)} from dependent {@link Commandlet}s.
   *
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and
   *         nothing has changed.
   */
  public boolean install() {

    return install(true);
  }

  /**
   * Performs the installation of the {@link #getName() tool} managed by this {@link Commandlet}.
   *
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and
   *         nothing has changed.
   */
  public boolean install(boolean silent) {

    return doInstall(silent);
  }

  /**
   * Installs or updates the managed {@link #getName() tool}.
   *
   * @param silent - {@code true} if called recursively to suppress verbose logging, {@code false} otherwise.
   * @return {@code true} if the tool was newly installed, {@code false} if the tool was already installed before and
   *         nothing has changed.
   */
  protected abstract boolean doInstall(boolean silent);

  /**
   * This method is called after the tool has been newly installed or updated to a new version.
   */
  protected void postInstall() {

    // nothing to do by default
  }

  /**
   * @param path the {@link Path} to start the recursive search from.
   * @return the deepest subdir {@code s} of the passed path such that all directories between {@code s} and the passed
   *         path (including {@code s}) are the sole item in their respective directory and {@code s} is not named
   *         "bin".
   */
  private Path getProperInstallationSubDirOf(Path path) {

    try (Stream<Path> stream = Files.list(path)) {
      Path[] subFiles = stream.toArray(Path[]::new);
      if (subFiles.length == 0) {
        throw new CliException("The downloaded package for the tool " + this.tool
            + " seems to be empty as you can check in the extracted folder " + path);
      } else if (subFiles.length == 1) {
        String filename = subFiles[0].getFileName().toString();
        if (!filename.equals(IdeContext.FOLDER_BIN) && !filename.equals(IdeContext.FOLDER_CONTENTS)
            && !filename.endsWith(".app") && Files.isDirectory(subFiles[0])) {
          return getProperInstallationSubDirOf(subFiles[0]);
        }
      }
      return path;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to get sub-files of " + path);
    }
  }

  /**
   * @param file the {@link Path} to the file to extract.
   * @param targetDir the {@link Path} to the directory where to extract (or copy) the file.
   */
  protected void extract(Path file, Path targetDir) {

    FileAccess fileAccess = this.context.getFileAccess();
    if (isExtract()) {
      Path tmpDir = this.context.getFileAccess().createTempDir("extract-" + file.getFileName());
      this.context.trace("Trying to extract the downloaded file {} to {} and move it to {}.", file, tmpDir, targetDir);
      String extension = FilenameUtil.getExtension(file.getFileName().toString());
      this.context.trace("Determined file extension {}", extension);
      TarCompression tarCompression = TarCompression.of(extension);
      if (tarCompression != null) {
        fileAccess.untar(file, tmpDir, tarCompression);
      } else if ("zip".equals(extension) || "jar".equals(extension)) {
        fileAccess.unzip(file, tmpDir);
      } else if ("dmg".equals(extension)) {
        assert this.context.getSystemInfo().isMac();
        Path mountPath = this.context.getIdeHome().resolve(IdeContext.FOLDER_UPDATES).resolve(IdeContext.FOLDER_VOLUME);
        fileAccess.mkdirs(mountPath);
        ProcessContext pc = this.context.newProcess();
        pc.executable("hdiutil");
        pc.addArgs("attach", "-quiet", "-nobrowse", "-mountpoint", mountPath, file);
        pc.run();
        Path appPath = fileAccess.findFirst(mountPath, p -> p.getFileName().toString().endsWith(".app"), false);
        if (appPath == null) {
          throw new IllegalStateException("Failed to unpack DMG as no MacOS *.app was found in file " + file);
        }
        fileAccess.copy(appPath, tmpDir);
        pc.addArgs("detach", "-force", mountPath);
        pc.run();
        // if [ -e "${target_dir}/Applications" ]
        // then
        // rm "${target_dir}/Applications"
        // fi
      } else if ("msi".equals(extension)) {
        this.context.newProcess().executable("msiexec").addArgs("/a", file, "/qn", "TARGETDIR=" + tmpDir).run();
        // msiexec also creates a copy of the MSI
        Path msiCopy = tmpDir.resolve(file.getFileName());
        fileAccess.delete(msiCopy);
      } else if ("pkg".equals(extension)) {

        Path tmpDirPkg = fileAccess.createTempDir("ide-pkg-");
        ProcessContext pc = this.context.newProcess();
        // we might also be able to use cpio from commons-compression instead of external xar...
        pc.executable("xar").addArgs("-C", tmpDirPkg, "-xf", file).run();
        Path contentPath = fileAccess.findFirst(tmpDirPkg, p -> p.getFileName().toString().equals("Payload"), true);
        fileAccess.untar(contentPath, tmpDir, TarCompression.GZ);
        fileAccess.delete(tmpDirPkg);
      } else {
        throw new IllegalStateException("Unknown archive format " + extension + ". Can not extract " + file);
      }
      fileAccess.move(getProperInstallationSubDirOf(tmpDir), targetDir);
      fileAccess.delete(tmpDir);
    } else {
      this.context.trace("Extraction is disabled for '{}' hence just moving the downloaded file {}.", getName(), file);
      fileAccess.move(file, targetDir);
    }
  }

  /**
   * @return {@code true} to extract (unpack) the downloaded binary file, {@code false} otherwise.
   */
  protected boolean isExtract() {

    return true;
  }

  /**
   * @return the {@link MacOsHelper} instance.
   */
  protected MacOsHelper getMacOsHelper() {

    if (this.macOsHelper == null) {
      this.macOsHelper = new MacOsHelper(this.context);
    }
    return this.macOsHelper;
  }

  /**
   * @return the currently installed {@link VersionIdentifier version} of this tool or {@code null} if not installed.
   */
  public VersionIdentifier getInstalledVersion() {

    return getInstalledVersion(this.context.getSoftwarePath().resolve(getName()));
  }

  /**
   * @param toolPath the installation {@link Path} where to find the version file.
   * @return the currently installed {@link VersionIdentifier version} of this tool or {@code null} if not installed.
   */
  protected VersionIdentifier getInstalledVersion(Path toolPath) {

    if (!Files.isDirectory(toolPath)) {
      this.context.debug("Tool {} not installed in {}", getName(), toolPath);
      return null;
    }
    Path toolVersionFile = toolPath.resolve(IdeContext.FILE_SOFTWARE_VERSION);
    if (!Files.exists(toolVersionFile)) {
      Path legacyToolVersionFile = toolPath.resolve(IdeContext.FILE_LEGACY_SOFTWARE_VERSION);
      if (Files.exists(legacyToolVersionFile)) {
        toolVersionFile = legacyToolVersionFile;
      } else {
        this.context.warning("Tool {} is missing version file in {}", getName(), toolVersionFile);
        return null;
      }
    }
    try {
      String version = Files.readString(toolVersionFile).trim();
      return VersionIdentifier.of(version);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read file " + toolVersionFile, e);
    }
  }

  /**
   * List the available versions of this tool.
   */
  public void listVersions() {

    List<VersionIdentifier> versions = this.context.getUrls().getSortedVersions(getName(), getEdition());
    for (VersionIdentifier vi : versions) {
      this.context.info(vi.toString());
    }
  }

  /**
   * Sets the tool version in the environment variable configuration file.
   *
   * @param version the version (pattern) to set.
   */
  public void setVersion(String version) {

    if ((version == null) || version.isBlank()) {
      throw new IllegalStateException("Version has to be specified!");
    }
    VersionIdentifier configuredVersion = VersionIdentifier.of(version);
    if (!configuredVersion.isPattern() && !configuredVersion.isValid()) {
      this.context.warning("Version {} seems to be invalid", version);
    }
    setVersion(configuredVersion, true);
  }

  /**
   * Sets the tool version in the environment variable configuration file.
   *
   * @param version the version to set. May also be a {@link VersionIdentifier#isPattern() version pattern}.
   * @param hint - {@code true} to print the installation hint, {@code false} otherwise.
   */
  public void setVersion(VersionIdentifier version, boolean hint) {

    EnvironmentVariables variables = this.context.getVariables();
    EnvironmentVariables settingsVariables = variables.getByType(EnvironmentVariablesType.SETTINGS);
    String edition = getEdition();
    String name = EnvironmentVariables.getToolVersionVariable(this.tool);
    VersionIdentifier resolvedVersion = this.context.getUrls().getVersion(this.tool, edition, version);
    if (version.isPattern()) {
      this.context.debug("Resolved version {} to {} for tool {}/{}", version, resolvedVersion, this.tool, edition);
    }
    settingsVariables.set(name, resolvedVersion.toString(), false);
    settingsVariables.save();
    this.context.info("{}={} has been set in {}", name, version, settingsVariables.getSource());
    EnvironmentVariables declaringVariables = variables.findVariable(name);
    if ((declaringVariables != null) && (declaringVariables != settingsVariables)) {
      this.context.warning(
          "The variable {} is overridden in {}. Please remove the overridden declaration in order to make the change affect.",
          name, declaringVariables.getSource());
    }
    if (hint) {
      this.context.info("To install that version call the following command:");
      this.context.info("ide install {}", this.tool);
    }
  }

}
