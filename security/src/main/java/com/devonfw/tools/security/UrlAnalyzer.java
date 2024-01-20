package com.devonfw.tools.security;

import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.owasp.dependencycheck.Engine;
import org.owasp.dependencycheck.analyzer.AbstractFileTypeAnalyzer;
import org.owasp.dependencycheck.analyzer.AnalysisPhase;
import org.owasp.dependencycheck.dependency.Confidence;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.dependency.Evidence;
import org.owasp.dependencycheck.dependency.EvidenceType;
import org.owasp.dependencycheck.exception.InitializationException;

import com.devonfw.tools.ide.url.updater.AbstractUrlUpdater;
import com.devonfw.tools.ide.url.updater.UpdateManager;

public class UrlAnalyzer extends AbstractFileTypeAnalyzer {

  // The file filter is used to filter supported files.
  private final FileFilter fileFilter;

  private static final String ANALYZER_NAME = "UrlAnalyzer";

  private final UpdateManager updateManager;

  public UrlAnalyzer(UpdateManager updateManager) {

    fileFilter = new UrlFileFilter();
    this.updateManager = updateManager;
  }

  @Override
  protected void analyzeDependency(Dependency dependency, Engine engine) {

    String filePath = dependency.getFilePath();
    Path parent = Paths.get(filePath).getParent();
    String tool = parent.getParent().getParent().getFileName().toString();
    String edition = parent.getParent().getFileName().toString();

    AbstractUrlUpdater urlUpdater = updateManager.getUrlUpdater(tool);

    // adding vendor evidence
    String cpeVendor = urlUpdater.getCpeVendor();
    String cpeProduct = urlUpdater.getCpeProduct();
    String cpeEdition = urlUpdater.getCpeEdition(edition);
    String cpeVersion = urlUpdater.mapUrlVersionToCpeVersion(parent.getFileName().toString());

    if (cpeVendor == null || cpeProduct == null) {
      return;
    }
    Evidence evidence;
    evidence = new Evidence(ANALYZER_NAME, "CpeVendor", cpeVendor, Confidence.HIGH);
    dependency.addEvidence(EvidenceType.VENDOR, evidence);

    evidence = new Evidence(ANALYZER_NAME, "CpeProduct", cpeProduct, Confidence.HIGH);
    dependency.addEvidence(EvidenceType.PRODUCT, evidence);

    if (cpeEdition != null) {
      evidence = new Evidence(ANALYZER_NAME, "CpeEdition", cpeEdition, Confidence.HIGH);
      dependency.addEvidence(EvidenceType.PRODUCT, evidence);
    }

    evidence = new Evidence(ANALYZER_NAME, "CpeVersion", cpeVersion, Confidence.HIGH);
    dependency.addEvidence(EvidenceType.VERSION, evidence);
  }

  @Override
  public boolean isEnabled() {

    return true;
  }

  @Override
  protected String getAnalyzerEnabledSettingKey() {

    // whether this Analyzer is enabled or not is not configurable but fixed by isEnabled()
    return null;
  }

  @Override
  protected FileFilter getFileFilter() {

    return fileFilter;
  }

  @Override
  protected void prepareFileTypeAnalyzer(Engine engine) throws InitializationException {

    // nothing to prepare here
  }

  @Override
  public String getName() {

    return ANALYZER_NAME;
  }

  @Override
  public AnalysisPhase getAnalysisPhase() {

    return AnalysisPhase.INFORMATION_COLLECTION;
  }
}
