package com.devonfw.tools.ide.tool.aws;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import com.devonfw.tools.ide.context.IdeContext;
import com.devonfw.tools.ide.environment.EnvironmentVariables;
import com.devonfw.tools.ide.environment.EnvironmentVariablesType;
import com.devonfw.tools.ide.process.ProcessContext;
import com.devonfw.tools.ide.tool.LocalToolCommandlet;


/**
 * {@link LocalToolCommandlet} for AWS CLI (aws).
 *
 * @see <a href="https://docs.aws.amazon.com/cli/">AWS CLI homepage</a>
 */

public class Aws extends LocalToolCommandlet {

  /**
   * The constructor.
   *
   * @param context the {@link IdeContext}.
   */
  public Aws(IdeContext context) {

    super(context, "aws", Set.of(TAG_CLOUD));
  }
  private void makeExecutable(Path file) {
    // TODO this can be removed if issue #132 is fixed
    Set<PosixFilePermission> permissions = null;
    try {
      permissions = Files.getPosixFilePermissions(file);
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(file, permissions);
    } catch (IOException e) {
      throw new RuntimeException("Adding execution permission for Group, Owner and Others did not work for " + file, e);
    }
  }

  @Override
  protected void moveAndProcessExtraction(Path from, Path to) {
    if (!this.context.getSystemInfo().isLinux()) {
      this.context.getFileAccess().move(from, to);
    } else {
      // make binary executable using java nio
      // because unpacking didn't preserve the file permissions
      Path awsInDistPath = from.resolve("dist").resolve("aws");
      Path awsCompleterInDistPath = from.resolve("dist").resolve("aws_completer");

      // TODO this can be removed if issue #132 is fixed
      makeExecutable(awsInDistPath);
      makeExecutable(awsCompleterInDistPath);

      // running the install-script that aws shipped
      ProcessContext pc = this.context.newProcess();
      Path linuxInstallScript = from.resolve("install");
      pc.executable("bash");
      pc.addArgs(linuxInstallScript, "-i", from.toString(), "-b", from.toString());
      pc.run();

      // the install-script that aws ships creates symbolic links to binaries but using absolute paths
      // since the current process happens in a temporary dir these links wouldn't be valid after moving the installation
      // files to the target dir. So the absolute paths are replaced by relative ones.
      for (String file : new String[]{"aws", "aws_completer", Path.of("v2").resolve("current").toString()}) {
        try {
          Path link = from.resolve(file);
          Path linkTarget = link.toRealPath();
          this.context.getFileAccess().delete(link); // delete old absolute link
          this.context.getFileAccess().relativeSymlink(link, linkTarget); // and replace it by the new relative link
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      this.context.getFileAccess().delete(linuxInstallScript);
      this.context.getFileAccess().delete(from.resolve("dist"));

      this.context.getFileAccess().move(from, to);
    }
  }

  @Override
  public void postInstall() {

    super.postInstall();

    EnvironmentVariables variables = this.context.getVariables();
    EnvironmentVariables typeVariables = variables.getByType(EnvironmentVariablesType.CONF);
    Path awsConfigDir = this.context.getConfPath().resolve("aws");
    this.context.getFileAccess().mkdirs(awsConfigDir);
    Path awsConfigFile = awsConfigDir.resolve("config");
    Path awsCredentialsFile = awsConfigDir.resolve("credentials");
    typeVariables.set("AWS_CONFIG_FILE", awsConfigFile.toString(), true);
    typeVariables.set("AWS_SHARED_CREDENTIALS_FILE", awsCredentialsFile.toString(), true);
    typeVariables.save();
  }
}
