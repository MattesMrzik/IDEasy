package com.devonfw.tools.ide.tool.eclipse;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import com.devonfw.tools.ide.cli.CliArgument;
import com.devonfw.tools.ide.cli.CliException;
import com.devonfw.tools.ide.common.Tag;
import com.devonfw.tools.ide.context.IdeContext;
import com.devonfw.tools.ide.log.IdeLogLevel;
import com.devonfw.tools.ide.process.ProcessContext;
import com.devonfw.tools.ide.process.ProcessErrorHandling;
import com.devonfw.tools.ide.process.ProcessResult;
import com.devonfw.tools.ide.tool.ide.IdeToolCommandlet;
import com.devonfw.tools.ide.tool.ide.PluginDescriptor;
import com.devonfw.tools.ide.tool.java.Java;

/**
 * {@link IdeToolCommandlet} for <a href="https://www.eclipse.org/">Eclipse</a>.
 */
public class Eclipse extends IdeToolCommandlet {

  /**
   * The constructor.
   *
   * @param context the {@link IdeContext}.
   */
  public Eclipse(IdeContext context) {

    super(context, "eclipse", Set.of(Tag.ECLIPSE));
  }

  @Override
  protected void runIde(String... args) {

    install(true);
    runEclipse(false,
        CliArgument.prepend(args, "gui", "-showlocation", this.context.getIdeHome().getFileName().toString()));
  }

  @Override
  public boolean install(boolean silent) {

    getCommandlet(Java.class).install();
    return super.install(silent);
  }

  /**
   * Runs eclipse application.
   *
   * @param log - {@code true} to run in log mode without opening a window and capture the output, {@code false}
   *        otherwise (run in GUI mode).
   * @param args the individual arguments to pass to eclipse.
   * @return the {@link ProcessResult}.
   */
  protected ProcessResult runEclipse(boolean log, String... args) {

    Path toolPath = Paths.get(getBinaryName());
    ProcessContext pc = this.context.newProcess();
    if (log) {
      pc.errorHandling(ProcessErrorHandling.ERROR);
    }
    pc.executable(toolPath);
    Path configurationPath = getPluginsInstallationPath().resolve("configuration");
    this.context.getFileAccess().mkdirs(configurationPath);
    if (log) {
      pc.addArg("-consoleLog").addArg("-nosplash");
    } else {
      pc.addArg("-clean");
      pc.addArg("-data").addArg(this.context.getWorkspacePath());
      pc.addArg("-keyring").addArg(this.context.getUserHome().resolve(".eclipse").resolve(".keyring"));
    }
    pc.addArg("-configuration").addArg(configurationPath);
    // TODO ability to use different Java version
    Path javaPath = getCommandlet(Java.class).getToolBinPath();
    pc.addArg("-vm").addArg(javaPath);
    pc.addArgs(args);
    return pc.run(log);
  }

  @Override
  public void installPlugin(PluginDescriptor plugin) {

    ProcessResult result = runEclipse(true, "org.eclipse.equinox.p2.director", "-repository", plugin.getUrl(),
        "-installIU", plugin.getId());
    if (result.isSuccessful()) {
      for (String line : result.getOut()) {
        if (line.contains("Overall install request is satisfiable")) {
          return;
        }
      }
    }
    this.context.error("Failed to install plugin {} ({}): exit code was {}", plugin.getName(), plugin.getId(),
        result.getExitCode());
    log(IdeLogLevel.WARNING, result.getOut());
    log(IdeLogLevel.ERROR, result.getErr());
  }

  private void log(IdeLogLevel level, List<String> lines) {

    for (String line : lines) {
      if (line.startsWith("!MESSAGE ")) {
        line = line.substring(9);
      }
      this.context.level(level).log(line);
    }
  }

  @Override
  protected void configureWorkspace() {

    Path lockfile = this.context.getWorkspacePath().resolve(".metadata/.lock");
    if (isLocked(lockfile)) {
      throw new CliException("Your workspace is locked at " + lockfile);
    }
    super.configureWorkspace();
  }

  /**
   * @param lockfile the {@link File} pointing to the lockfile to check.
   * @return {@code true} if the given {@link File} is locked, {@code false} otherwise.
   */
  private static boolean isLocked(Path lockfile) {

    if (Files.isRegularFile(lockfile)) {
      try (RandomAccessFile raFile = new RandomAccessFile(lockfile.toFile(), "rw")) {
        FileLock fileLock = raFile.getChannel().tryLock(0, 1, false);
        // success, file was not locked so we immediately unlock again...
        fileLock.release();
        return false;
      } catch (Exception e) {
        return true;
      }
    }
    return false;
  }

}
